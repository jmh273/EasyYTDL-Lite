package com.jmh273.easyytdllite.queue

import com.jmh273.easyytdllite.queue.MediaKind.MP3
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadHistoryTest {
    @Test
    fun `加入與查詢`() {
        val h = DownloadHistory().add("a", MP3, "u1").add("b", MP3, null)
        assertTrue(h.has("a", MP3))
        assertTrue(h.has("b", MP3))
        assertFalse(h.has("z", MP3))
        assertEquals(listOf("a", "b"), h.ids)
    }

    @Test
    fun `同一支同格式再下載一次時換成新的位置並移到最後`() {
        val h = DownloadHistory().add("a", MP3, "u1").add("b", MP3, "u2").add("a", MP3, "u3")
        assertEquals(listOf(HistoryEntry("b", MP3, "u2"), HistoryEntry("a", MP3, "u3")), h.entries)
    }

    @Test
    fun `超過上限刪掉最舊的`() {
        var h = DownloadHistory()
        for (i in 1..5002) h = h.add("v$i", MP3, "u$i")
        assertEquals(5000, h.entries.size)
        assertFalse(h.has("v1", MP3))
        assertFalse(h.has("v2", MP3))
        assertTrue(h.has("v3", MP3))
        assertTrue(h.has("v5002", MP3))
    }

    @Test
    fun `從佇列併入只補沒有的,不動已有的位置`() {
        val h = DownloadHistory().add("a", MP3, "u1").addAll(listOf("b", "a", "c", "b"))
        assertEquals(
            listOf(HistoryEntry("a", MP3, "u1"), HistoryEntry("b", MP3, null), HistoryEntry("c", MP3, null)),
            h.entries,
        )
    }

    @Test
    fun `已下載要有紀錄而且檔案還在,舊紀錄一律算已下載`() {
        val h = DownloadHistory()
            .add("kept", MP3, "content://media/external/audio/media/1")
            .add("deleted", MP3, "content://media/external/audio/media/2")
            .add("legacy", MP3, null)
        val existing = setOf("content://media/external/audio/media/1", "content://media/external/audio/media/99")
        assertEquals(setOf("kept", "legacy"), h.downloadedIds(MP3, existing))
        assertEquals(setOf("legacy"), h.downloadedIds(MP3, emptySet()))
    }

    @Test
    fun `v2 與 JSON 互轉`() {
        val h = DownloadHistory().add("a", MP3, "u1").add("b", MP3, null)
        assertEquals(h, DownloadHistory.decode(DownloadHistory.encode(h)))
    }

    @Test
    fun `讀得懂 v1,筆數不變`() {
        val ids = (1..120).map { "v$it" }
        val v1 = JSONObject().put("version", 1).put("ids", org.json.JSONArray(ids)).toString()
        val h = DownloadHistory.decode(v1)
        assertEquals(120, h.entries.size)
        assertEquals(ids.map { HistoryEntry(it, MP3, null) }, h.entries)
    }

    @Test
    fun `舊版只讀 ids 也拿得到全部 id(降版保護)`() {
        val h = DownloadHistory().add("a", MP3, "u1").add("b", MP3, null)
        val json = JSONObject(DownloadHistory.encode(h))
        assertEquals(2, json.getInt("version"))
        // 舊版 decode 的寫法:只讀 ids
        val array = json.getJSONArray("ids")
        assertEquals(listOf("a", "b"), (0 until array.length()).map { array.getString(it) })
    }

    @Test
    fun `壞檔為空,壞掉的單筆略過`() {
        assertEquals(DownloadHistory(), DownloadHistory.decode(null))
        assertEquals(DownloadHistory(), DownloadHistory.decode("{壞"))
        val json = """{"version":2,"entries":[{"id":"a","kind":"MP3","uri":"u1"},{"kind":"MP3"},{"id":"c","kind":"WAV"},{"id":"d","kind":"MP3"}],"ids":["a","d"]}"""
        assertEquals(listOf(HistoryEntry("a", MP3, "u1"), HistoryEntry("d", MP3, null)), DownloadHistory.decode(json).entries)
    }

    @Test
    fun `舊版讀的 ids 只放 MP3,entries 兩種都有`() {
        val h = DownloadHistory().add("a", MediaKind.MP3, "u1").add("b", MediaKind.MP4, "u2").add("a", MediaKind.MP4, "u3")
        val json = org.json.JSONObject(DownloadHistory.encode(h))
        val ids = json.getJSONArray("ids")
        assertEquals(listOf("a"), (0 until ids.length()).map { ids.getString(it) })
        assertEquals(3, json.getJSONArray("entries").length())
        assertEquals(h, DownloadHistory.decode(json.toString()))
    }

    @Test
    fun `已下載依格式分開`() {
        val h = DownloadHistory().add("a", MediaKind.MP3, "u1").add("b", MediaKind.MP4, "u2")
        assertEquals(setOf("b"), h.downloadedIds(MediaKind.MP4, setOf("u1", "u2")))
        assertEquals(setOf("a"), h.downloadedIds(MediaKind.MP3, setOf("u1", "u2")))
    }

    @Test
    fun `從佇列併入時只算已完成的 MP3`() {
        val queue = Queue(
            listOf(
                QueueItem("a", "u", 1, Status.DONE),
                QueueItem("b", "u", 2, Status.DONE, kind = MediaKind.MP4, height = 720),
                QueueItem("c", "u", 3, Status.WAITING),
            ),
        )
        val h = DownloadHistory().addDone(queue)
        assertEquals(listOf(HistoryEntry("a", MediaKind.MP3, null)), h.entries)
        // 只下載過 MP4 的 b,在 MP3 下不能被當成已下載
        assertEquals(setOf("a"), h.downloadedIds(MediaKind.MP3, emptySet()))
    }
}
