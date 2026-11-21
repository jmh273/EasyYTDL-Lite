package com.jmh273.easyytdllite.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 前半是手寫的邊界情形;「實機輸出」開頭的測試讀 test/resources/flatlist/ 的真實輸出
 * (2026-11-22 測試機上 youtubedl-android 內建 yt-dlp 更新到 2026.10.26 後跑出,Lister 的 debug 傾印取回)。
 */
class FlatListTest {
    private fun real(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("flatlist/$name.json")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `實機輸出 搜尋 20 筆,略過 3 支直播中的`() {
        val page = FlatList.parse(real("search"))!!
        assertEquals("lofi jazz piano", page.title)
        assertEquals(17, page.videos.size)
        assertEquals(Video(page.videos[0].id, page.videos[0].title, 12923), page.videos[0])
        assertEquals("MYPVQccHhAQ", page.videos[0].id)
        for (live in listOf("Dx5qFachd3A", "N0snMcR6aaA", "O8q9nnyK6Xw")) {
            assertEquals(false, page.videos.any { it.id == live })
        }
    }

    @Test
    fun `實機輸出 頻道 30 支,都有長度`() {
        val page = FlatList.parse(real("channel"))!!
        assertEquals("YouTube - Videos", page.title)
        assertEquals(30, page.videos.size)
        assertEquals("dD33lDxL9K0", page.videos.first().id)
        assertEquals(441L, page.videos.first().durationSeconds)
        assertEquals("OzjerLmZt8Q", page.videos.last().id)
        assertEquals(true, page.videos.all { it.durationSeconds != null })
    }

    @Test
    fun `實機輸出 播放清單 15 支,Shorts 沒有長度`() {
        val page = FlatList.parse(real("playlist"))!!
        assertEquals("Making it count", page.title)
        assertEquals(15, page.videos.size)
        assertEquals("46oqYboBCb0", page.videos.first().id)
        assertEquals("Fp-usx4DWHw", page.videos.last().id)
        assertEquals(true, page.videos.all { it.durationSeconds == null })
    }

    @Test
    fun `直播中與尚未開播的略過,已結束的直播照收`() {
        val json = """{"title":"t","entries":[
            {"id":"aaaaaaaaaaa","title":"直播中","live_status":"is_live"},
            {"id":"bbbbbbbbbbb","title":"預告","live_status":"is_upcoming"},
            {"id":"ccccccccccc","title":"重播","live_status":"was_live","duration":3600},
            {"id":"ddddddddddd","title":"一般","live_status":null,"duration":60}]}"""
        assertEquals(listOf("ccccccccccc", "ddddddddddd"), FlatList.parse(json)!!.videos.map { it.id })
    }
    @Test
    fun `頻道清單`() {
        val json = """
            {"_type":"playlist","id":"UCabc","title":"Some Channel - Videos","channel":"Some Channel",
             "entries":[
               {"_type":"url","ie_key":"Youtube","id":"aaaaaaaaaaa","url":"https://www.youtube.com/watch?v=aaaaaaaaaaa","title":"第一支","duration":215.0},
               {"_type":"url","ie_key":"Youtube","id":"bbbbbbbbbbb","url":"https://www.youtube.com/watch?v=bbbbbbbbbbb","title":"第二支","duration":null}
             ]}
        """.trimIndent()
        val page = FlatList.parse(json)!!
        assertEquals("Some Channel - Videos", page.title)
        assertEquals(listOf(Video("aaaaaaaaaaa", "第一支", 215), Video("bbbbbbbbbbb", "第二支", null)), page.videos)
    }

    @Test
    fun `搜尋結果略過頻道與播放清單`() {
        val json = """
            {"_type":"playlist","id":"lofi","title":"lofi","entries":[
              {"_type":"url","ie_key":"YoutubeTab","id":"UCxxxxxxxxxxxxxxxxxxxxxx","url":"https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxx","title":"頻道"},
              {"_type":"url","ie_key":"Youtube","id":"ccccccccccc","url":"https://www.youtube.com/watch?v=ccccccccccc","title":"影片","duration":61},
              {"_type":"url","ie_key":"YoutubeTab","id":"PLabc","url":"https://www.youtube.com/playlist?list=PLabc","title":"清單"}
            ]}
        """.trimIndent()
        assertEquals(listOf(Video("ccccccccccc", "影片", 61)), FlatList.parse(json)!!.videos)
    }

    @Test
    fun `缺標題時用 id、重複的 id 只留第一個`() {
        val json = """{"title":"x","entries":[{"id":"ddddddddddd"},{"id":"ddddddddddd","title":"重複"},null,{"title":"沒有 id"}]}"""
        assertEquals(listOf(Video("ddddddddddd", "ddddddddddd", null)), FlatList.parse(json)!!.videos)
    }

    @Test
    fun `沒有 entries 是空清單,壞掉的 JSON 是 null`() {
        assertEquals(emptyList<Video>(), FlatList.parse("""{"title":"空"}""")!!.videos)
        assertNull(FlatList.parse(""))
        assertNull(FlatList.parse("WARNING: something\n"))
        assertNull(FlatList.parse(null))
    }

    @Test
    fun `stdout 前面有雜訊時取最後一行 JSON`() {
        val out = "[debug] something\n{\"title\":\"t\",\"entries\":[{\"id\":\"eeeeeeeeeee\",\"title\":\"e\"}]}\n"
        assertEquals("eeeeeeeeeee", FlatList.parse(out)!!.videos.single().id)
    }

    @Test
    fun `長度文字`() {
        assertEquals("3:35", FlatList.durationText(215))
        assertEquals("1:02:03", FlatList.durationText(3723))
        assertEquals("0:05", FlatList.durationText(5))
        assertNull(FlatList.durationText(null))
    }
}
