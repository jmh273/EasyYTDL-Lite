package com.jmh273.easyytdllite.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSettingsTest {
    @Test
    fun `預設同時下載 2 支`() {
        assertEquals(2, DownloadSettings.DEFAULT.concurrency)
    }

    @Test
    fun `壞檔或超出範圍時用預設值`() {
        assertEquals(2, DownloadSettings.decode(null).concurrency)
        assertEquals(2, DownloadSettings.decode("{bad").concurrency)
        assertEquals(2, DownloadSettings.decode("""{"version":1,"concurrency":0}""").concurrency)
        assertEquals(2, DownloadSettings.decode("""{"version":1,"concurrency":4}""").concurrency)
    }

    @Test
    fun `1 到 3 原樣保留並能互轉`() {
        for (n in 1..3) assertEquals(n, DownloadSettings.decode(DownloadSettings.encode(DownloadSettings(n, 89))).concurrency)
    }

    @Test
    fun `預設目標音量 89 dB`() {
        assertEquals(89, DownloadSettings.DEFAULT.targetDb)
        assertEquals(89, DownloadSettings.decode(null).targetDb)
    }

    @Test
    fun `目標音量 75 到 95 原樣保留並能互轉`() {
        for (db in listOf(75, 83, 92, 95)) {
            assertEquals(DownloadSettings(3, db), DownloadSettings.decode(DownloadSettings.encode(DownloadSettings(3, db))))
        }
    }

    @Test
    fun `目標音量不合法時只換掉那一欄`() {
        for (bad in listOf("74", "96", "92.5", "\"92\"", "null")) {
            assertEquals(bad, DownloadSettings(3, 89), DownloadSettings.decode("""{"version":1,"concurrency":3,"targetDb":$bad}"""))
        }
    }

    @Test
    fun `同時下載支數不合法時不影響目標音量`() {
        assertEquals(DownloadSettings(2, 92), DownloadSettings.decode("""{"version":1,"concurrency":9,"targetDb":92}"""))
        assertEquals(DownloadSettings(2, 92), DownloadSettings.decode("""{"version":1,"targetDb":92}"""))
    }

    @Test
    fun `舊版設定檔沒有目標音量`() {
        assertEquals(DownloadSettings(3, 89), DownloadSettings.decode("""{"version":1,"concurrency":3}"""))
    }

    @Test
    fun `格式與解析度來回轉換`() {
        val s = DownloadSettings(3, 92, com.jmh273.easyytdllite.queue.MediaKind.MP4, 1080)
        assertEquals(s, DownloadSettings.decode(DownloadSettings.encode(s)))
    }

    @Test
    fun `舊檔沒有格式欄位時是 MP3 與 720`() {
        assertEquals(
            DownloadSettings(3, 92, com.jmh273.easyytdllite.queue.MediaKind.MP3, 720),
            DownloadSettings.decode("""{"version":1,"concurrency":3,"targetDb":92}"""),
        )
    }

    @Test
    fun `格式或解析度壞掉時只換那一欄`() {
        val mp4 = com.jmh273.easyytdllite.queue.MediaKind.MP4
        assertEquals(DownloadSettings(2, 89, mp4, 720), DownloadSettings.decode("""{"concurrency":2,"targetDb":89,"kind":"MP4","height":999}"""))
        assertEquals(DownloadSettings(2, 89, com.jmh273.easyytdllite.queue.MediaKind.MP3, 480), DownloadSettings.decode("""{"concurrency":2,"targetDb":89,"kind":"MKV","height":480}"""))
        assertEquals(DownloadSettings(2, 89, mp4, 720), DownloadSettings.decode("""{"concurrency":2,"targetDb":89,"kind":"MP4","height":480.5}"""))
    }
}
