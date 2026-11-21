package com.jmh273.easyytdllite.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class ListSourceTest {
    @Test
    fun `有影片 id 的網址是單支影片`() {
        assertEquals(ListSource.SingleVideo("j8yO0LkqA7s"), ListSource.classify("https://youtu.be/j8yO0LkqA7s?si=x"))
        // 單支影片優先:播放中的清單網址也帶 v=,照舊當單支(和分享的既有行為一致)
        assertEquals(
            ListSource.SingleVideo("YhiQDARUJ7Y"),
            ListSource.classify("https://www.youtube.com/watch?v=YhiQDARUJ7Y&list=PLabc123&index=2"),
        )
    }

    @Test
    fun `播放清單網址`() {
        val cases = mapOf(
            "https://www.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG" to "PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG",
            "分享 https://youtube.com/playlist?list=PL123-ab_C&si=AbCd" to "PL123-ab_C",
            "https://m.youtube.com/playlist?si=x&list=OLAK5uy_abc" to "OLAK5uy_abc",
            "https://music.youtube.com/playlist?list=PLmusic1" to "PLmusic1",
        )
        for ((text, id) in cases) assertEquals(text, ListSource.Playlist(id), ListSource.classify(text))
    }

    @Test
    fun `頻道網址的四種路徑`() {
        val cases = mapOf(
            "https://www.youtube.com/@SomeChannel" to "@SomeChannel",
            "https://youtube.com/@some.channel-1?si=x" to "@some.channel-1",
            "https://www.youtube.com/@SomeChannel/videos" to "@SomeChannel",
            "https://www.youtube.com/@SomeChannel/shorts" to "@SomeChannel",
            "https://m.youtube.com/channel/UCabcdefghijklmnopqrstuv" to "channel/UCabcdefghijklmnopqrstuv",
            "https://www.youtube.com/c/SomeName/featured" to "c/SomeName",
            "https://www.youtube.com/user/oldname" to "user/oldname",
        )
        for ((text, path) in cases) assertEquals(text, ListSource.Channel(path), ListSource.classify(text))
    }

    @Test
    fun `不認得的文字與合輯`() {
        for (text in listOf(
            null, "", "   ", "lofi", "https://example.com/@x", "https://example.com/playlist?list=PL1",
            "https://www.youtube.com/playlist?list=RDCLAK5uy_abc", "https://www.youtube.com/feed/subscriptions",
            "https://www.youtube.com/", "https://www.youtube.com/results?search_query=x",
        )) {
            assertEquals(text.toString(), ListSource.Unsupported, ListSource.classify(text))
        }
    }

    @Test
    fun `yt-dlp 目標與數量上限`() {
        assertEquals("https://www.youtube.com/playlist?list=PL1", ListSource.Playlist("PL1").target())
        assertEquals(200, ListSource.Playlist("PL1").limit)
        assertEquals("https://www.youtube.com/@x/videos", ListSource.Channel("@x").target())
        assertEquals(30, ListSource.Channel("@x").limit)
        assertEquals("ytsearch20:lofi beats", ListSource.Search("  lofi beats ").target())
        assertEquals(20, ListSource.Search("x").limit)
    }

    @Test
    fun `yt-dlp 參數`() {
        assertEquals(
            listOf("--flat-playlist", "-J", "--playlist-end", "30"),
            ListSource.Channel("@x").options(),
        )
    }

    @Test
    fun `單支影片也可以列出 不展開清單`() {
        val v = ListSource.SingleVideo("j8yO0LkqA7s")
        assertEquals("https://www.youtube.com/watch?v=j8yO0LkqA7s", v.target())
        assertEquals(listOf("--flat-playlist", "-J", "--no-playlist"), v.options())
    }
}
