package com.jmh273.easyytdllite.ytdlp

import com.jmh273.easyytdllite.queue.Failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureClassifierTest {
    // spike 1 第 1 次(內建 yt-dlp 2025.11.12)的日誌原文。
    private val forbidden = """
        WARNING: [youtube] j8yO0LkqA7s: Some web client https formats have been skipped as they are missing a url. YouTube is forcing SABR streaming for this client.
        ERROR: unable to download video data: HTTP Error 403: Forbidden
        Traceback (most recent call last):
        yt_dlp.networking.exceptions.HTTPError: HTTP Error 403: Forbidden
    """.trimIndent()

    private val dns = "ERROR: [youtube] abc: Unable to download API page: <urlopen error [Errno 7] No address associated with hostname> (caused by TransportError(\"<urlopen error [Errno 7] No address associated with hostname>\"))"
    private val timeout = "ERROR: unable to download video data: <urlopen error _ssl.c:1011: The handshake operation timed out>"
    private val bot = "ERROR: [youtube] abc: Sign in to confirm you’re not a bot. Use --cookies-from-browser or --cookies for the authentication."
    private val unavailable = "ERROR: [youtube] abc12345678: Video unavailable. This video is not available"
    private val privateVideo = "ERROR: [youtube] abc12345678: Private video. Sign in if you've been granted access to this video"
    // 2026-11-04 真機實測(yt-dlp 2026.10.26)對不存在的影片 id 的原文。
    private val unavailableReal = "ERROR: [youtube] ZzZzZzZzZz0: This video is unavailable"
    private val extract ="ERROR: [youtube] abc: Unable to extract nsig function code"

    @Test
    fun `依錯誤輸出分類`() {
        assertEquals(Failure.YOUTUBE_REJECTED, FailureClassifier.classify(forbidden, true))
        assertEquals(Failure.YOUTUBE_REJECTED, FailureClassifier.classify(extract, true))
        assertEquals(Failure.NETWORK, FailureClassifier.classify(dns, true))
        assertEquals(Failure.NETWORK, FailureClassifier.classify(timeout, true))
        assertEquals(Failure.BOT_CHECK, FailureClassifier.classify(bot, true))
        assertEquals(Failure.UNAVAILABLE, FailureClassifier.classify(unavailable, true))
        assertEquals(Failure.UNAVAILABLE, FailureClassifier.classify(privateVideo, true))
        assertEquals(Failure.UNAVAILABLE, FailureClassifier.classify(unavailableReal, true))
        assertEquals(Failure.OTHER,FailureClassifier.classify("ERROR: something else", true))
    }

    @Test
    fun `失敗當下沒有網路一律算網路失敗`() {
        assertEquals(Failure.NETWORK, FailureClassifier.classify(forbidden, false))
        assertEquals(Failure.NETWORK, FailureClassifier.classify("", false))
    }

    @Test
    fun `私人影片的「Sign in」不算機器人驗證`() {
        assertEquals(Failure.UNAVAILABLE, FailureClassifier.classify(privateVideo, true))
    }

    @Test
    fun `只有 YouTube 拒絕與機器人驗證提示更新`() {
        assertTrue(FailureClassifier.suggestsUpdate(Failure.YOUTUBE_REJECTED))
        assertTrue(FailureClassifier.suggestsUpdate(Failure.BOT_CHECK))
        for (f in listOf(Failure.NETWORK, Failure.UNAVAILABLE, Failure.OTHER)) assertFalse(f.name, FailureClassifier.suggestsUpdate(f))
    }
}
