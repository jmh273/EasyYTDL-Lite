package com.jmh273.easyytdllite.ytdlp

import com.jmh273.easyytdllite.queue.Failure

/** 依 yt-dlp 的錯誤輸出與失敗當下的網路狀態分類失敗原因(design D6)。純邏輯。 */
object FailureClassifier {
    private val NETWORK = listOf(
        "No address associated with hostname", "Temporary failure in name resolution",
        "Network is unreachable", "timed out", "Connection reset", "Connection refused",
        "Remote end closed connection", "Software caused connection abort", "Connection aborted",
    )
    private val UNAVAILABLE = listOf(
        // 「This video is unavailable」是 2026.10.26 對不存在 id 的實際訊息(2026-11-04 真機)。
        "Video unavailable", "This video is unavailable", "Private video", "This video is not available",
        "members-only", "This video has been removed", "who has blocked it in your country",
    )
    private val REJECTED = listOf(
        "HTTP Error 403", "Unable to extract", "nsig", "Requested format is not available",
        "Signature extraction failed",
    )

    fun classify(stderr: String, networkAvailable: Boolean): Failure = when {
        !networkAvailable -> Failure.NETWORK
        NETWORK.any { stderr.contains(it, ignoreCase = true) } -> Failure.NETWORK
        // 私人影片的訊息也有「Sign in」,所以先判斷無法取得。
        UNAVAILABLE.any { stderr.contains(it, ignoreCase = true) } -> Failure.UNAVAILABLE
        stderr.contains("Sign in to confirm") -> Failure.BOT_CHECK
        REJECTED.any { stderr.contains(it) } -> Failure.YOUTUBE_REJECTED
        else -> Failure.OTHER
    }

    /** 這些失敗會在畫面提示「更新 yt-dlp」。 */
    fun suggestsUpdate(failure: Failure): Boolean =
        failure == Failure.YOUTUBE_REJECTED || failure == Failure.BOT_CHECK
}
