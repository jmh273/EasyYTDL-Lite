package com.jmh273.easyytdllite.service

import android.content.Context
import com.jmh273.easyytdllite.ytdlp.YtDlpPolicy
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * yt-dlp 執行檔的版本管理(design D7)。會阻塞的方法 MUST 在背景執行緒呼叫。
 * 下載與更新、退回共用 [lock](add-drawer-and-parallel-downloads design D7):下載拿讀鎖,可以同時多支;
 * 更新與退回拿寫鎖,等所有下載的 yt-dlp 結束才替換執行檔。fair 模式:寫鎖在等時,新的下載排在它後面。
 */
class YtDlpManager private constructor(context: Context) {
    private val app = context.applicationContext
    private val ytdlpDir = File(app.noBackupFilesDir, "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}")
    private val binary = File(ytdlpDir, YoutubeDL.ytdlpBin)
    private val ownDir = File(app.noBackupFilesDir, "easyytdllite").apply { mkdirs() }
    private val prevBinary = File(ownDir, "yt-dlp.prev")
    private val libPrefs = app.getSharedPreferences(YoutubeDL.baseName, Context.MODE_PRIVATE)
    private val ownPrefs = app.getSharedPreferences("ytdlp_versions", Context.MODE_PRIVATE)

    val lock = ReentrantReadWriteLock(true)

    /** 在讀鎖內執行(下載用)。 */
    fun <T> whileRunning(block: () -> T): T = lock.read(block)

    /** 這個 process 是否已經試過首次更新(design D7;失敗也只試一次)。下載與列清單共用。 */
    @Volatile private var firstUpdateAttempted = false

    /** 還是內建版,而且這次執行還沒試過更新。 */
    fun needsFirstUpdate(): Boolean = YtDlpPolicy.shouldUpdateBeforeDownload(isBundled(), firstUpdateAttempted)

    /** 內建版一定被 YouTube 擋:第一次下載或列清單前先更新一次。沒有更新時回傳 null。 */
    @Synchronized
    fun updateBundledOnce(): UpdateOutcome? {
        if (!needsFirstUpdate()) return null
        firstUpdateAttempted = true
        return update()
    }

    /** 有 yt-dlp 正在下載:這時按更新要等它們結束。 */
    fun isBusy(): Boolean = lock.readLockCount > 0

    sealed class UpdateOutcome {
        data class Updated(val from: String?, val to: String) : UpdateOutcome()
        data class AlreadyLatest(val version: String?) : UpdateOutcome()
        data class Restored(val version: String?, val reason: String) : UpdateOutcome()
        data class Failed(val reason: String) : UpdateOutcome()
    }

    @Synchronized
    fun init() {
        YoutubeDL.getInstance().init(app)
        FFmpeg.getInstance().init(app)
    }

    /** 實際執行 `yt-dlp --version`;失敗回傳 null。 */
    fun version(): String? = runCatching {
        YoutubeDL.getInstance().execute(YoutubeDLRequest(emptyList<String>()).addOption("--version")).out.trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** 函式庫只在更新時寫入 `dlpVersion`;沒有它代表還是內建版。 */
    fun isBundled(): Boolean = libPrefs.getString(KEY_VERSION, null) == null

    fun previousVersion(): String? = if (prevBinary.exists()) ownPrefs.getString(PREV_SHOWN, null) else null

    fun update(): UpdateOutcome = lock.write {
        init()
        val before = version()
        // 更新成功才把它變成「上一版」;已是最新時不能蓋掉原本的上一版。
        val candidate = File(ownDir, "yt-dlp.candidate")
        binary.copyTo(candidate, overwrite = true)
        val beforeTag = libPrefs.getString(KEY_VERSION, null)
        val beforeName = libPrefs.getString(KEY_VERSION_NAME, null)

        val status = try {
            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
        } catch (e: Exception) {
            candidate.delete()
            return@write UpdateOutcome.Failed(e.cause?.toString() ?: e.toString())
        }
        if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) {
            candidate.delete()
            return@write UpdateOutcome.AlreadyLatest(before)
        }
        val after = version()
        if (YtDlpPolicy.mustRestoreAfterUpdate(after)) {
            ytdlpDir.mkdirs()
            candidate.copyTo(binary, overwrite = true)
            candidate.delete()
            writeLibVersion(beforeTag, beforeName)
            return@write UpdateOutcome.Restored(version(), after ?: "(no output)")
        }
        candidate.copyTo(prevBinary, overwrite = true)
        candidate.delete()
        ownPrefs.edit()
            .putString(PREV_SHOWN, before)
            .putString(PREV_TAG, beforeTag)
            .putString(PREV_NAME, beforeName)
            .apply()
        UpdateOutcome.Updated(before, after!!)
    }

    /** 和上一版互換;再按一次就換回來。回傳換過之後的版本。 */
    fun revertToPrevious(): String? = lock.write {
        if (!prevBinary.exists()) return@write version()
        init()
        val currentShown = version()
        val currentTag = libPrefs.getString(KEY_VERSION, null)
        val currentName = libPrefs.getString(KEY_VERSION_NAME, null)
        val swap = File(ownDir, "yt-dlp.swap")
        binary.copyTo(swap, overwrite = true)
        prevBinary.copyTo(binary, overwrite = true)
        swap.copyTo(prevBinary, overwrite = true)
        swap.delete()
        writeLibVersion(ownPrefs.getString(PREV_TAG, null), ownPrefs.getString(PREV_NAME, null))
        ownPrefs.edit()
            .putString(PREV_SHOWN, currentShown)
            .putString(PREV_TAG, currentTag)
            .putString(PREV_NAME, currentName)
            .apply()
        version()
    }

    /** null 代表內建版:移除鍵,讓 [isBundled] 為真。 */
    private fun writeLibVersion(tag: String?, name: String?) {
        val e = libPrefs.edit()
        if (tag == null) e.remove(KEY_VERSION) else e.putString(KEY_VERSION, tag)
        if (name == null) e.remove(KEY_VERSION_NAME) else e.putString(KEY_VERSION_NAME, name)
        e.commit()
    }

    companion object {
        // youtubedl-android 0.18.1 的 YoutubeDLUpdater 用的鍵名。
        private const val KEY_VERSION = "dlpVersion"
        private const val KEY_VERSION_NAME = "dlpVersionName"
        private const val PREV_SHOWN = "prev_shown"
        private const val PREV_TAG = "prev_tag"
        private const val PREV_NAME = "prev_name"

        @Volatile private var instance: YtDlpManager? = null

        fun get(context: Context): YtDlpManager = instance ?: synchronized(this) {
            instance ?: YtDlpManager(context).also { instance = it }
        }
    }
}
