package com.jmh273.easyytdllite.queue

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QueueCodecTest {
    @Test
    fun `編碼後解碼得到相同的佇列`() {
        val queue = Queue(
            listOf(
                QueueItem("a", "https://www.youtube.com/watch?v=a", 1L),
                QueueItem("b", "u/b", 2L, Status.FAILED, "標題 \"引號\"", Failure.NETWORK, 4, null),
                QueueItem("c", "u/c", 3L, Status.DONE, "歌", null, 0, "Music/EasyYTDL-Lite/2026-11-05/歌.mp3"),
                QueueItem("d", "u/d", 4L, Status.WAITING_NETWORK, null, null, 2, null),
            ),
        )
        assertEquals(queue, QueueCodec.decode(QueueCodec.encode(queue)))
    }

    @Test
    fun `空檔、壞檔、不認得的狀態都不丟例外`() {
        assertEquals(Queue(), QueueCodec.decode(null))
        assertEquals(Queue(), QueueCodec.decode(""))
        assertEquals(Queue(), QueueCodec.decode("{壞掉"))
        val unknown = """{"items":[{"id":"a","url":"u","addedAt":1,"status":"未來的狀態"},{"id":"b","url":"u/b","addedAt":2,"status":"WAITING"}]}"""
        assertEquals(listOf("b"), QueueCodec.decode(unknown).items.map { it.id })
    }

    @Test
    fun `格式欄位寫進檔案`() {
        val json = JSONObject(QueueCodec.encode(Queue(listOf(QueueItem("a", "u/a", 1L, kind = MediaKind.MP3)))))
        assertEquals("MP3", json.getJSONArray("items").getJSONObject(0).getString("kind"))
    }

    @Test
    fun `沒有格式欄位的舊檔當作 MP3,不認得的格式略過該項`() {
        val json = """{"version":1,"items":[{"id":"a","url":"u/a","addedAt":1,"status":"DONE"},{"id":"b","url":"u/b","addedAt":2,"status":"WAITING","kind":"未來的格式"}]}"""
        val items = QueueCodec.decode(json).items
        assertEquals(listOf("a"), items.map { it.id })
        assertEquals(MediaKind.MP3, items.single().kind)
    }

    @Test
    fun `MP4 保留解析度,MP3 不寫解析度`() {
        val queue = Queue(listOf(QueueItem("a", "u/a", 1L), QueueItem("a", "u/a", 2L, kind = MediaKind.MP4, height = 1080)))
        val json = QueueCodec.encode(queue)
        assertEquals(queue, QueueCodec.decode(json))
        assertFalse(json, JSONObject(json).getJSONArray("items").getJSONObject(0).has("height"))
    }

    @Test
    fun `MP4 缺解析度或值不合法時當作 720`() {
        val json = """{"version":1,"items":[
            {"id":"a","url":"u","addedAt":1,"status":"WAITING","kind":"MP4"},
            {"id":"b","url":"u","addedAt":1,"status":"WAITING","kind":"MP4","height":999},
            {"id":"c","url":"u","addedAt":1,"status":"WAITING","kind":"MP4","height":480}]}"""
        assertEquals(listOf(720, 720, 480), QueueCodec.decode(json).items.map { it.height })
    }

    @Test
    fun `降版保護,0_5_0 的讀法讀新檔時 MP3 項目都在`() {
        val json = QueueCodec.encode(Queue(listOf(QueueItem("a", "u/a", 1L), QueueItem("b", "u/b", 2L, kind = MediaKind.MP4, height = 720))))
        // 0.5.0 只認得 MP3:kind 不認得就略過那一項。
        val array = JSONObject(json).getJSONArray("items")
        val oldReadable = (0 until array.length()).map { array.getJSONObject(it) }.filter { it.optString("kind", "MP3") == "MP3" }
        assertEquals(listOf("a"), oldReadable.map { it.getString("id") })
    }
}
