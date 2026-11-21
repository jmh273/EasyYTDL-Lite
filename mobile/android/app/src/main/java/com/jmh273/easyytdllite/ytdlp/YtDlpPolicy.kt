package com.jmh273.easyytdllite.ytdlp

/** yt-dlp 版本政策(design D7)。純邏輯。 */
object YtDlpPolicy {
    /** 處理佇列前要不要先更新:還是內建版,而且這次執行還沒試過。 */
    fun shouldUpdateBeforeDownload(isBundled: Boolean, attemptedThisRun: Boolean): Boolean =
        isBundled && !attemptedThisRun

    private val VERSION = Regex("""^\d{4}\.\d{2}\.\d{2}(\.\d+)?$""")

    /** 更新後新版執行 `--version` 的結果:失敗、空字串或不是版本號就要還原。 */
    fun mustRestoreAfterUpdate(newVersionOutput: String?): Boolean =
        newVersionOutput == null || !VERSION.matches(newVersionOutput.trim())
}
