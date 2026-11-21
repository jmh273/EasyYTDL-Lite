package com.jmh273.easyytdllite.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守門:build.gradle 每個 implementation 相依的 group 都要在授權名單裡找得到。
 * 錨點:一定要讀到 youtubedl-android,讀不到代表讀錯檔,0 個缺漏不能當證據。
 * 射程:只看 build.gradle 的直接相依;間接相依與內建執行檔(yt-dlp、Python、FFmpeg、QuickJS)由下面另一條列舉檢查。
 */
class OpenSourceNoticesTest {
    private val implementation = Regex("""^\s*implementation\s+(?:platform\()?'([^':]+):[^']*'""")

    private fun gradleGroups(): List<String> {
        val f = File("build.gradle") // 單元測試的工作目錄是 app/
        val groups = f.readLines().map { it.substringBefore("//") }.mapNotNull { implementation.find(it)?.groupValues?.get(1) }
        assertTrue("錨點:${f.absolutePath} 讀不到 youtubedl-android", groups.any { it.contains("youtubedl-android") })
        return groups.distinct()
    }

    private fun missing(groups: List<String>, entries: List<Notice>) =
        groups.filter { g -> entries.none { e -> e.groups.any { g == it || g.startsWith("$it.") } } }

    @Test
    fun `build_gradle 的每個直接相依都在名單裡`() {
        assertEquals(emptyList<String>(), missing(gradleGroups(), OpenSourceNotices.ENTRIES))
    }

    @Test
    fun `對照組 拿掉 youtubedl-android 那一項就會缺`() {
        val without = OpenSourceNotices.ENTRIES.filterNot { e -> e.groups.any { it.contains("youtubedl-android") } }
        assertEquals(listOf("io.github.junkfood02.youtubedl-android"), missing(gradleGroups(), without))
    }

    @Test
    fun `內建執行檔與 GPL 元件都列出`() {
        val names = OpenSourceNotices.ENTRIES.map { it.name }
        for (n in listOf("yt-dlp", "youtubedl-android", "Python", "FFmpeg", "QuickJS")) {
            assertTrue("名單缺 $n", names.contains(n))
        }
        assertEquals("GPL-3.0", OpenSourceNotices.APP.license)
        assertTrue(OpenSourceNotices.ENTRIES.all { it.url.startsWith("https://") })
    }
}
