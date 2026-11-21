package com.jmh273.easyytdllite.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoUrlTest {
    @Test
    fun `各種分享網址都取得 11 碼影片 id`() {
        val cases = mapOf(
            "https://youtu.be/j8yO0LkqA7s?si=AbCdEf" to "j8yO0LkqA7s",
            "看看 https://www.youtube.com/watch?v=YhiQDARUJ7Y&list=RDCLAK5uy&index=2" to "YhiQDARUJ7Y",
            "https://m.youtube.com/watch?feature=share&v=_cTAlxWtmS4" to "_cTAlxWtmS4",
            "https://www.youtube.com/shorts/abcDEF12345" to "abcDEF12345",
            "https://music.youtube.com/watch?v=-a_b-C_d-E1&si=x" to "-a_b-C_d-E1",
            "https://www.youtube.com/live/Zz9Zz9Zz9Zz?feature=shared" to "Zz9Zz9Zz9Zz",
        )
        for ((text, id) in cases) assertEquals(text, id, VideoUrl.videoId(text))
    }

    @Test
    fun `不是 YouTube 影片時回傳 null`() {
        for (text in listOf(null, "", "沒有網址", "https://example.com/watch?v=j8yO0LkqA7s",
            "https://www.youtube.com/playlist?list=RDCLAK5uy", "https://youtu.be/short")) {
            assertNull(text, VideoUrl.videoId(text))
        }
    }

    @Test
    fun `標準網址`() {
        assertEquals("https://www.youtube.com/watch?v=j8yO0LkqA7s", VideoUrl.canonical("j8yO0LkqA7s"))
    }
}
