package com.jmh273.easyytdllite.probe

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.storage.DailyFolder
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * debug 版限定的下載探測(openspec change add-download-spike)。
 * 所有方法都會阻塞,呼叫端 MUST 在背景執行緒呼叫。
 * 每個動作都在 `getExternalFilesDir("probe")/<資料夾>/` 留原始紀錄,可用 adb pull 取出。
 */
class ProbeRunner(private val context: Context) {
    private val app = context.applicationContext
    private val probeRoot: File = File(app.getExternalFilesDir(null), "probe")

    // youtubedl-android 0.18.1 的路徑(見 design.md Context)。
    private val libBase = File(app.noBackupFilesDir, YoutubeDL.baseName)
    private val ytdlpDir = File(libBase, YoutubeDL.ytdlpDirName)
    private val ffmpegBin = File(app.applicationInfo.nativeLibraryDir, "libffmpeg.so")
    private val ffmpegLdPath = listOf("packages/ffmpeg/usr/lib", "packages/python/usr/lib")
        .joinToString(":") { File(libBase, it).absolutePath }

    fun init() {
        YoutubeDL.getInstance().init(app)
        FFmpeg.getInstance().init(app)
    }

    /** 實際執行 `yt-dlp --version`(不連網)。不用函式庫的 SharedPreferences:內建版從未寫入過那裡。 */
    fun ytdlpVersion(): String =
        YoutubeDL.getInstance().execute(YoutubeDLRequest(emptyList<String>()).addOption("--version")).out.trim()

    fun download(url: String, mode: JsMode, now: LocalDateTime = LocalDateTime.now()): String {
        val runDir = newRunDir(ProbeCommands.runFolderName(now, mode))
        val workDir = File(app.cacheDir, "probe-work").apply { deleteRecursively(); mkdirs() }
        val cmd = ProbeCommands.build(mode, workDir.absolutePath)
        val request = YoutubeDLRequest(url)
        cmd.options.forEach { (k, v) -> if (v == null) request.addOption(k) else request.addOption(k, v) }
        if (cmd.customCommands.isNotEmpty()) request.addCommands(cmd.customCommands)

        // 函式庫失敗時只丟出含 stderr 的例外,stdout 與退出碼都會遺失;stdout 改由回呼逐行收集。
        val stdout = StringBuilder()
        val started = System.currentTimeMillis()
        val (exit, stderr) = try {
            val resp = YoutubeDL.getInstance().execute(request, null) { _, _, line -> stdout.appendLine(line) }
            resp.exitCode.toString() to resp.err
        } catch (e: YoutubeDLException) {
            app.getString(R.string.probe_exit_unknown) to (e.message ?: e.toString())
        }
        val elapsed = System.currentTimeMillis() - started
        // executeImpl 會把它自己加的選項(--js-runtimes、--ffmpeg-location 等)寫回 request,這裡讀到的是完整參數。
        write(runDir, "command.txt", (listOf("<python>", "<yt-dlp>") + request.buildCommand()).joinToString("\n"))
        write(runDir, "url.txt", url)
        write(runDir, "exit.txt", exit)
        write(runDir, "stdout.txt", stdout.toString())
        write(runDir, "stderr.txt", stderr)
        write(runDir, "elapsed_ms.txt", elapsed.toString())

        workDir.listFiles { f -> f.name.endsWith(".info.json") }?.forEach { it.copyTo(File(runDir, it.name), true) }
        val mp3 = workDir.listFiles { f -> f.name.endsWith(".mp3") }?.firstOrNull()
        val report = StringBuilder()
            .appendLine(app.getString(R.string.probe_exit, exit))
            .appendLine(app.getString(R.string.probe_log_dir, runDir.absolutePath))
        if (mp3 != null) {
            val gain = runFfmpeg(listOf("-hide_banner", "-nostats", "-i", mp3.absolutePath, "-af", "replaygain", "-f", "null", "-"))
            write(runDir, "replaygain.txt", gain)
            val saved = saveToMusic(mp3, LocalDate.now())
            write(runDir, "saved.txt", saved)
            report.appendLine(app.getString(R.string.probe_saved_to, saved))
            gain.lineSequence().filter { "track_" in it }.forEach { report.appendLine(it.trim()) }
        } else {
            report.appendLine(app.getString(R.string.probe_no_mp3))
        }
        workDir.deleteRecursively()
        return report.toString()
    }

    fun checkFfmpeg(): String {
        val out = runFfmpeg(listOf("-hide_banner", "-filters"))
        val runDir = newRunDir(ProbeCommands.runFolderName(LocalDateTime.now(), "ffmpeg"))
        write(runDir, "filters.txt", out)
        // -filters 每行形如「 ... replaygain        A->N       ReplayGain scanner.」
        val has = out.lineSequence().any { it.trim().split(Regex("\\s+")).getOrNull(1) == "replaygain" }
        return app.getString(if (has) R.string.probe_replaygain_yes else R.string.probe_replaygain_no) +
            "\n" + app.getString(R.string.probe_log_dir, runDir.absolutePath)
    }

    fun update(): String {
        val runDir = newRunDir(ProbeCommands.runFolderName(LocalDateTime.now(), "update"))
        val before = ytdlpVersion()
        val status = try {
            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE).toString()
        } catch (e: YoutubeDLException) {
            app.getString(R.string.probe_update_failed, e.cause?.toString() ?: e.message)
        }
        val after = ytdlpVersion()
        write(runDir, "update.txt", "before=$before\nstatus=$status\nafter=$after\nbinary_bytes=${File(ytdlpDir, YoutubeDL.ytdlpBin).length()}")
        return app.getString(R.string.probe_update_result, before, status, after)
    }

    /** 刪掉已更新的執行檔與函式庫的版本紀錄,再讓函式庫從內建資源複製回來(不連網)。 */
    fun revertToBundled(): String {
        val before = ytdlpVersion()
        ytdlpDir.deleteRecursively()
        app.getSharedPreferences(YoutubeDL.baseName, Context.MODE_PRIVATE).edit()
            .remove("dlpVersion").remove("dlpVersionName").commit()
        YoutubeDL.getInstance().init_ytdlp(app, ytdlpDir)
        val after = ytdlpVersion()
        val runDir = newRunDir(ProbeCommands.runFolderName(LocalDateTime.now(), "revert"))
        write(runDir, "revert.txt", "before=$before\nafter=$after\nbinary_bytes=${File(ytdlpDir, YoutubeDL.ytdlpBin).length()}")
        return app.getString(R.string.probe_revert_result, before, after)
    }

    private fun runFfmpeg(args: List<String>): String {
        val pb = ProcessBuilder(listOf(ffmpegBin.absolutePath) + args).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = ffmpegLdPath
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        return "exit=$code\n$out"
    }

    private fun saveToMusic(mp3: File, date: LocalDate): String {
        val relative = DailyFolder.relativePath(com.jmh273.easyytdllite.service.Pipeline.ROOT_FOLDER, date)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, mp3.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relative)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("MediaStore insert 回傳 null")
        resolver.openOutputStream(uri).use { out -> mp3.inputStream().use { it.copyTo(out!!) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return relative + mp3.name
    }

    private fun newRunDir(name: String) = File(probeRoot, name).apply { mkdirs() }

    private fun write(dir: File, name: String, text: String) = File(dir, name).writeText(text)
}
