package com.jmh273.easyytdllite.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class DailyFolderTest {
    @Test
    fun `路徑是 Music 底下的根目錄再加當天日期(YYYYMMDD)`() {
        assertEquals(
            "Music/MyRoot/20261104/",
            DailyFolder.relativePath("MyRoot", LocalDate.of(2026, 11, 4)),
        )
    }

    @Test
    fun `月與日補零`() {
        assertEquals(
            "Music/MyRoot/20270109/",
            DailyFolder.relativePath("MyRoot", LocalDate.of(2027, 1, 9)),
        )
    }

    @Test
    fun `根目錄前後空白會去掉`() {
        assertEquals(
            "Music/MyRoot/20261104/",
            DailyFolder.relativePath("  MyRoot ", LocalDate.of(2026, 11, 4)),
        )
    }

    @Test
    fun `根目錄空白或含路徑分隔字元時拒絕`() {
        for (bad in listOf("", "   ", "a/b", "a\\b", ".", "..")) {
            assertThrows("「$bad」應該被拒絕", IllegalArgumentException::class.java) {
                DailyFolder.relativePath(bad, LocalDate.of(2026, 11, 4))
            }
        }
    }

    @Test
    fun `MP4 在 Movies 底下,日期加 _MP4`() {
        assertEquals("Movies/EasyYTDL-Lite/20261106_MP4/", DailyFolder.videoPath("EasyYTDL-Lite", java.time.LocalDate.of(2026, 11, 6)))
        assertEquals("Movies/EasyYTDL-Lite/20260105_MP4/", DailyFolder.videoPath(" EasyYTDL-Lite ", java.time.LocalDate.of(2026, 1, 5)))
    }
}
