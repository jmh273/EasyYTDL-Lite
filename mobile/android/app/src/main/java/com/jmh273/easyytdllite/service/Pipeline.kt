package com.jmh273.easyytdllite.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.MediaStore
import com.jmh273.easyytdllite.audio.FileNames
import com.jmh273.easyytdllite.audio.Gain
import com.jmh273.easyytdllite.audio.GainPlan
import com.jmh273.easyytdllite.queue.Failure
import com.jmh273.easyytdllite.queue.MediaKind
import com.jmh273.easyytdllite.queue.QueueItem
import com.jmh273.easyytdllite.storage.DailyFolder
import com.jmh273.easyytdllite.video.VideoFormat
import com.jmh273.easyytdllite.ytdlp.FailureClassifier
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * 一支影片的處理步驟(design D4)。會阻塞,MUST 在背景執行緒呼叫。
 * 每支影片的紀錄寫在 `filesDir/logs/<影片 id>/`,只保留最近 [KEEP_LOGS] 支。
 */
class Pipeline(context: Context, private val ytdlp: YtDlpManager, private val networkAvailable: () -> Boolean) {
    private val app = context.applicationContext
    private val ffmpeg = Ffmpeg(app)
    private val logsRoot = File(app.filesDir, "logs")

    sealed class Outcome {
        /** [uri] 是輸出檔的 MediaStore content URI,記進下載紀錄,用來判斷檔案還在不在。 */
        data class Done(val savedPath: String, val uri: String, val title: String, val gain: Gain?) : Outcome()
        data class Failed(val failure: Failure) : Outcome()
    }

    fun run(item: QueueItem, targetDb: Int, onTitle: (String) -> Unit, onProgress: (Float) -> Unit): Outcome {
        // 每個項目各自的暫存與紀錄資料夾:平行下載時不能刪到別支的檔案(design D6);
        // 同一支影片的 MP3 和 MP4 也要分開,所以用 key(add-mp4-download design D1)。
        val work = File(workRoot(app), item.key).apply { deleteRecursively(); mkdirs() }
        val log = logDir(item.key)
        return try {
            when (item.kind) {
                MediaKind.MP3 -> runMp3(item, work, log, targetDb, onTitle, onProgress)
                MediaKind.MP4 -> runMp4(item, work, log, onTitle, onProgress)
            }
        } catch (e: Exception) {
            failOther(log, e.stackTraceToString())
        } finally {
            // 清掉暫存
            work.deleteRecursively()
        }
    }

    private class Info(val title: String, val artist: String, val date: String, val json: JSONObject?)

    /** 執行 yt-dlp 並寫下指令與輸出;失敗時回傳分類好的 [Outcome.Failed],成功回傳 null。 */
    private fun download(request: YoutubeDLRequest, item: QueueItem, log: File, onProgress: (Float) -> Unit): Outcome.Failed? {
        val stdout = StringBuilder()
        val stderr = try {
            ytdlp.whileRunning {
                YoutubeDL.getInstance().execute(request, item.key) { progress, _, line ->
                    stdout.appendLine(line)
                    if (progress >= 0) onProgress(progress)
                }.err
            }
        } catch (e: YoutubeDLException) {
            val err = e.message ?: e.toString()
            write(log, "stdout.txt", stdout.toString())
            write(log, "stderr.txt", err)
            write(log, "command.txt", request.buildCommand().joinToString("\n"))
            return Outcome.Failed(FailureClassifier.classify(err, networkAvailable()))
        }
        write(log, "stdout.txt", stdout.toString())
        write(log, "stderr.txt", stderr)
        write(log, "command.txt", request.buildCommand().joinToString("\n"))
        return null
    }

    /** 從 info JSON 取標題、頻道與上傳日期。 */
    private fun info(work: File, item: QueueItem): Info {
        val json = work.listFiles { f -> f.name.endsWith(".info.json") }?.firstOrNull()
            ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        val title = json?.optString("title")?.takeIf { it.isNotBlank() } ?: item.title ?: item.id
        val artist = json?.optString("channel")?.takeIf { it.isNotBlank() }
            ?: json?.optString("uploader").orEmpty()
        val date = json?.optString("upload_date").orEmpty().let {
            if (it.length == 8) "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)}" else ""
        }
        return Info(title, artist, date, json)
    }

    private fun runMp3(item: QueueItem, work: File, log: File, targetDb: Int, onTitle: (String) -> Unit, onProgress: (Float) -> Unit): Outcome {
        // 1. 只下載音訊
        val request = YoutubeDLRequest(item.url)
            .addOption("-v")
            .addOption("--no-playlist")
            .addOption("-f", "bestaudio")
            .addOption("--write-info-json")
            .addOption("--no-mtime")
            .addOption("-o", "${work.absolutePath}/%(id)s.%(ext)s")
        download(request, item, log, onProgress)?.let { return it }

        // 2. 影片資訊
        val info = info(work, item)
        onTitle(info.title)
        val audio = work.listFiles { f -> !f.name.endsWith(".json") && !f.name.endsWith(".part") }?.firstOrNull()
            ?: return failOther(log, "yt-dlp 結束但沒有音訊檔")

        // 3. 量響度
        val analysis = ffmpeg.run(listOf("-hide_banner", "-nostats", "-i", audio.absolutePath, "-af", "replaygain", "-f", "null", "-"))
        write(log, "replaygain.txt", "exit=${analysis.exitCode}\n${analysis.output}")
        val trackGain = Regex("""track_gain = ([-+]?\d+(?:\.\d+)?)""").find(analysis.output)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: return failOther(log, "replaygain 沒有輸出 track_gain")
        val trackPeak = Regex("""track_peak = (\d+(?:\.\d+)?)""").find(analysis.output)?.groupValues?.get(1)?.toDoubleOrNull()

        // 4. 決定增益
        val gain = GainPlan.plan(trackGain, trackPeak, targetDb.toDouble())

        // 5. 調音量並轉成 192 kbps MP3
        val out = File(work, "out.mp3")
        val volume = String.format(Locale.US, "%.2f", gain.appliedDb)
        val encode = ffmpeg.run(
            listOf(
                "-hide_banner", "-nostats", "-y", "-i", audio.absolutePath,
                "-vn", "-map_metadata", "-1", "-af", "volume=${volume}dB",
                "-c:a", "libmp3lame", "-b:a", "192k", "-id3v2_version", "3",
                "-metadata", "title=${info.title}", "-metadata", "artist=${info.artist}",
                "-metadata", "date=${info.date}", "-metadata", "comment=${item.url}",
                out.absolutePath,
            ),
        )
        write(
            log, "encode.txt",
            "target_db=$targetDb\ntrack_gain=$trackGain\ntrack_peak=$trackPeak\napplied_db=${gain.appliedDb}\nlimited=${gain.limited}\nexit=${encode.exitCode}\n${encode.output}",
        )
        if (encode.exitCode != 0 || !out.exists()) return failOther(log, "ffmpeg 轉檔失敗(exit=${encode.exitCode})")

        // 6. 寫到 Music/EasyYTDL-Lite/YYYYMMDD/,檔名加當天的序號
        val saved = save(out, FileNames.stem(info.title, item.id), MediaKind.MP3)
        write(log, "saved.txt", "${saved.path}\n${saved.uri}")
        return Outcome.Done(saved.path, saved.uri, info.title, gain)
    }

    /** MP4(add-mp4-download design D5):不重新編碼、不調音量。 */
    private fun runMp4(item: QueueItem, work: File, log: File, onTitle: (String) -> Unit, onProgress: (Float) -> Unit): Outcome {
        // 1. 剩餘空間
        val free = StatFs(app.filesDir.absolutePath).availableBytes
        if (free < MIN_FREE_BYTES_MP4) {
            return failOther(log, "剩餘空間不足:${free / 1_000_000} MB(MP4 至少要 ${MIN_FREE_BYTES_MP4 / 1_000_000} MB)")
        }

        // 2. 下載影音軌並合併(yt-dlp 自己呼叫內建的 ffmpeg;youtubedl-android 會加上 --ffmpeg-location)
        val height = VideoFormat.normalize(item.height)
        val request = YoutubeDLRequest(item.url)
            .addOption("-v")
            .addOption("--no-playlist")
            .addOption("-f", VideoFormat.selector(height))
            .addOption("--merge-output-format", "mp4")
            .addOption("--write-info-json")
            .addOption("--no-mtime")
            .addOption("-o", "${work.absolutePath}/%(id)s.%(ext)s")
        download(request, item, log, onProgress)?.let { return it }

        val info = info(work, item)
        onTitle(info.title)
        // 合併後是 .mp4;第 4 段退路(單一檔案)理論上可能不是 mp4,下一步的重新封裝一樣會輸出 mp4。
        val video = work.listFiles { f -> !f.name.endsWith(".json") && !f.name.endsWith(".part") }?.firstOrNull()
            ?: return failOther(log, "yt-dlp 結束但沒有影片檔")

        // 3. 寫標籤並把索引移到開頭;-c copy:不重新編碼
        val out = File(work, "out.mp4")
        val remux = ffmpeg.run(
            listOf(
                "-hide_banner", "-nostats", "-y", "-i", video.absolutePath,
                "-map", "0", "-c", "copy", "-map_metadata", "-1",
                "-metadata", "title=${info.title}", "-metadata", "artist=${info.artist}",
                "-metadata", "date=${info.date}", "-metadata", "comment=${item.url}",
                "-movflags", "+faststart",
                out.absolutePath,
            ),
        )
        val json = info.json
        write(
            log, "encode.txt",
            "kind=MP4\nmax_height=$height\nheight=${json?.opt("height")}\nvcodec=${json?.opt("vcodec")}\nacodec=${json?.opt("acodec")}\n" +
                "format_id=${json?.opt("format_id")}\nexit=${remux.exitCode}\n${remux.output}",
        )
        if (remux.exitCode != 0 || !out.exists()) return failOther(log, "ffmpeg 寫標籤失敗(exit=${remux.exitCode})")

        // 4. 寫到 Movies/EasyYTDL-Lite/YYYYMMDD_MP4/,序號在 MP4 資料夾裡算
        val saved = save(out, FileNames.stem(info.title, item.id), MediaKind.MP4)
        write(log, "saved.txt", "${saved.path}\n${saved.uri}")
        return Outcome.Done(saved.path, saved.uri, info.title, null)
    }

    private data class Saved(val path: String, val uri: String)

    /**
     * 查詢已有檔名、決定序號與檔名、寫入要串行:同時完成的兩支才不會拿到同一個序號或檔名
     * (design D6;序號見 add-gain-numbering-and-history-v2 D3)。MP3 和 MP4 共用同一把鎖,序號各自在自己的資料夾裡算。
     */
    private fun save(file: File, stem: String, kind: MediaKind): Saved = synchronized(SAVE_LOCK) { saveLocked(file, stem, kind) }

    private class Target(val relative: String, val collection: Uri, val mime: String, val ext: String)

    private fun target(kind: MediaKind): Target {
        val today = LocalDate.now()
        return when (kind) {
            MediaKind.MP3 -> Target(
                DailyFolder.relativePath(ROOT_FOLDER, today),
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), "audio/mpeg", "mp3",
            )
            MediaKind.MP4 -> Target(
                DailyFolder.videoPath(ROOT_FOLDER, today),
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), "video/mp4", "mp4",
            )
        }
    }

    private fun saveLocked(file: File, stem: String, kind: MediaKind): Saved {
        val t = target(kind)
        val resolver = app.contentResolver
        val existing = mutableSetOf<String>()
        resolver.query(
            t.collection, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf(t.relative), null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { existing += it } }
        val name = FileNames.unique(FileNames.numbered(stem, FileNames.nextSequence(existing)), existing, t.ext)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, t.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, t.relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(t.collection, values) ?: error("MediaStore insert 回傳 null")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return Saved(t.relative + name, uri.toString())
    }

    private fun failOther(log: File, message: String): Outcome.Failed {
        write(log, "error.txt", message)
        return Outcome.Failed(Failure.OTHER)
    }

    private fun logDir(id: String): File {
        logsRoot.mkdirs()
        val dir = File(logsRoot, id).apply { deleteRecursively(); mkdirs() }
        logsRoot.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP_LOGS)?.forEach { it.deleteRecursively() }
        return dir
    }

    private fun write(dir: File, name: String, text: String) = File(dir, name).writeText(text)

    companion object {
        const val ROOT_FOLDER = "EasyYTDL-Lite"
        const val KEEP_LOGS = 50

        /** MP4 開始前至少要有的剩餘空間(spec video-output「開始前檢查剩餘空間」)。 */
        const val MIN_FREE_BYTES_MP4 = 2_000_000_000L
        private val SAVE_LOCK = Any()

        fun workRoot(context: Context) = File(context.cacheDir, "work")

        /** 畫面查看錯誤紀錄用:stderr 與 error 的最後一段。[key] 是佇列項目的 key。 */
        fun readLog(context: Context, key: String, maxChars: Int = 6000): String {
            val dir = File(context.filesDir, "logs/$key")
            val text = listOf("error.txt", "stderr.txt")
                .map { File(dir, it) }.filter { it.exists() }
                .joinToString("\n\n") { "== ${it.name}\n" + it.readText() }
            return if (text.length > maxChars) "…" + text.takeLast(maxChars) else text
        }
    }
}
