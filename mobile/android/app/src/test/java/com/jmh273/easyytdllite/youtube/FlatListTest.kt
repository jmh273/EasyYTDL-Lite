package com.jmh273.easyytdllite.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 輸入依 yt-dlp `--flat-playlist -J` 的欄位撰寫(design 風險:未以實機輸出確認);
 * 實機跑過後換成真實輸出存成的測試檔。
 */
class FlatListTest {
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
