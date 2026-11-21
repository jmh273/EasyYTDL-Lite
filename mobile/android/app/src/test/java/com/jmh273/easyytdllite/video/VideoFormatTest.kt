package com.jmh273.easyytdllite.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFormatTest {
    @Test
    fun `格式字串依序是 H264+M4A、任何影片+M4A、任何影片+任何聲音、單一檔案,每段分橫式與直式`() {
        val l = "[aspect_ratio>=1][height<=720]"
        val p = "[aspect_ratio<1][width<=720]"
        assertEquals(
            "bv[vcodec^=avc1]$l+ba[ext=m4a]/bv[vcodec^=avc1]$p+ba[ext=m4a]" +
                "/bv$l+ba[ext=m4a]/bv$p+ba[ext=m4a]" +
                "/bv*$l+ba/bv*$p+ba" +
                "/b$l/b$p",
            VideoFormat.selector(720),
        )
        // 每一段都有上限:橫式比高度、直式比寬度(短邊)
        val parts = VideoFormat.selector(1080).split('/')
        assertEquals(8, parts.size)
        assertEquals(4, parts.count { "[aspect_ratio>=1][height<=1080]" in it })
        assertEquals(4, parts.count { "[aspect_ratio<1][width<=1080]" in it })
    }

    @Test
    fun `解析度選項與預設值`() {
        assertEquals(listOf(1080, 720, 480, 360), VideoFormat.HEIGHTS)
        assertEquals(720, VideoFormat.normalize(null))
        assertEquals(720, VideoFormat.normalize(1440))
        assertEquals(360, VideoFormat.normalize(360))
    }
}

class MergeProgressTest {
    private fun run(vararg ps: Float): List<Float> {
        var s = MergeProgress()
        return ps.map { s = s.next(it); s.shown }
    }

    @Test
    fun `兩段分別對應到 0–85 和 85–100`() {
        val shown = run(0f, 50f, 100f, 0f, 50f, 100f)
        assertEquals(listOf(0f, 42.5f, 85f, 85f, 92.5f, 100f), shown)
    }

    @Test
    fun `一段式最多 85,而且不會倒退`() {
        val shown = run(10f, 60f, 100f)
        assertEquals(listOf(8.5f, 51f, 85f), shown)
    }

    @Test
    fun `開頭的小幅抖動不算第二段`() {
        val shown = run(3f, 2f, 40f)
        assertEquals(listOf(2.55f, 2.55f, 34f), shown)
    }
}
