package com.jmh273.easyytdllite.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {
    @Test
    fun `不能用的字元換成空白並合併`() {
        assertEquals("A B Live", FileNames.stem("A/B: \"Live\" ?", "id"))
        assertEquals("a b c", FileNames.stem("a\\b*c<>|", "id").let { it.replace(Regex(" +"), " ") })
        assertEquals("甲乙丙丁Strangers", FileNames.stem("  甲乙丙丁Strangers\t\n", "id"))
    }

    @Test
    fun `空標題用備用名稱,開頭的點去掉`() {
        assertEquals("abc12345678", FileNames.stem(null, "abc12345678"))
        assertEquals("abc12345678", FileNames.stem(" /?* ", "abc12345678"))
        assertEquals("hidden", FileNames.stem("...hidden", "id"))
    }

    @Test
    fun `過長時以 UTF-8 位元組截短,不切斷字元`() {
        val stem = FileNames.stem("林".repeat(100), "id") // 300 位元組
        assertTrue(stem.toByteArray(Charsets.UTF_8).size <= FileNames.MAX_STEM_BYTES)
        assertEquals("林".repeat(66), stem)
    }

    @Test
    fun `重名時加序號,比對不分大小寫`() {
        assertEquals("歌名.mp3", FileNames.unique("歌名", emptySet()))
        assertEquals("歌名 (2).mp3", FileNames.unique("歌名", setOf("歌名.mp3")))
        assertEquals("歌名 (3).mp3", FileNames.unique("歌名", setOf("歌名.mp3", "歌名 (2).mp3")))
        assertEquals("Song (2).mp3", FileNames.unique("Song", setOf("SONG.MP3")))
    }

    @Test
    fun `開頭數字加底線才算序號`() {
        assertEquals(7, FileNames.sequenceOf("07_C.mp3"))
        assertEquals(123, FileNames.sequenceOf("123_歌名.mp3"))
        assertEquals(1, FileNames.sequenceOf("01_1999_演唱會.mp3"))
        assertNull(FileNames.sequenceOf("歌名.mp3"))
        assertNull(FileNames.sequenceOf("1999 演唱會.mp3"))
        assertNull(FileNames.sequenceOf("_x.mp3"))
        assertNull(FileNames.sequenceOf("a01_x.mp3"))
    }

    @Test
    fun `下一個序號是最大序號加 1`() {
        assertEquals(8, FileNames.nextSequence(setOf("01_A.mp3", "02_B.mp3", "07_C.mp3")))
        assertEquals(1, FileNames.nextSequence(emptySet()))
        assertEquals(1, FileNames.nextSequence(setOf("歌名.mp3", "1999 演唱會.mp3")))
        assertEquals(100, FileNames.nextSequence(setOf("99_A.mp3")))
        assertEquals(101, FileNames.nextSequence(setOf("100_A.mp3", "99_B.mp3")))
    }

    @Test
    fun `序號 99 以內補成 2 位數,100 以上照實際位數`() {
        assertEquals("01_歌名", FileNames.numbered("歌名", 1))
        assertEquals("99_歌名", FileNames.numbered("歌名", 99))
        assertEquals("100_歌名", FileNames.numbered("歌名", 100))
    }

    @Test
    fun `加上序號後仍可用重名保險`() {
        val existing = setOf("01_歌名.mp3")
        val stem = FileNames.numbered("歌名", 1) // 刻意給已被佔用的序號
        assertEquals("01_歌名 (2).mp3", FileNames.unique(stem, existing))
        assertEquals("02_歌名.mp3", FileNames.unique(FileNames.numbered("歌名", FileNames.nextSequence(existing)), existing))
    }

    @Test
    fun `MP4 的副檔名與同名處理`() {
        assertEquals("01_歌名.mp4", FileNames.unique("01_歌名", emptySet(), "mp4"))
        assertEquals("01_歌名 (2).mp4", FileNames.unique("01_歌名", setOf("01_歌名.MP4"), "mp4"))
        // MP3 的同名檔不影響 MP4
        assertEquals("01_歌名.mp4", FileNames.unique("01_歌名", setOf("01_歌名.mp3"), "mp4"))
    }
}
