package com.jmh273.easyytdllite.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private fun real(): String =
        javaClass.classLoader!!.getResourceAsStream("update/latest.json")!!.readBytes().toString(Charsets.UTF_8)

    private fun rel(v: String) = LatestRelease(Version.parse(v)!!, "https://github.com/jmh273/EasyYTDL-Lite/releases/tag/mobile-v$v")

    @Test
    fun `實機回應 2026-11-22 的 releases latest`() {
        assertEquals(
            LatestRelease(Version(0, 1, 1), "https://github.com/jmh273/EasyYTDL-Lite/releases/tag/mobile-v0.1.1"),
            UpdateCheck.parse(real()),
        )
    }

    @Test
    fun `tag 或網址不對就讀不懂`() {
        fun j(tag: String, url: String) = """{"tag_name":"$tag","html_url":"$url"}"""
        val ok = "https://github.com/jmh273/EasyYTDL-Lite/releases/tag/x"
        assertEquals(Version(1, 2, 3), UpdateCheck.parse(j("mobile-v1.2.3", ok))!!.version)
        assertNull(UpdateCheck.parse(j("v1.2.3", ok)))
        assertNull(UpdateCheck.parse(j("mobile-v1.2", ok)))
        assertNull(UpdateCheck.parse(j("mobile-v1.2.3-beta", ok)))
        assertNull(UpdateCheck.parse(j("mobile-v1.2.3", "https://evil.example/github.com/")))
        assertNull(UpdateCheck.parse(j("mobile-v1.2.3", "http://github.com/x")))
        assertNull(UpdateCheck.parse("""{"message":"Not Found"}"""))
        assertNull(UpdateCheck.parse("not json"))
        assertNull(UpdateCheck.parse(null))
    }

    @Test
    fun `版號比較與 debug 後綴`() {
        assertEquals(Version(0, 2, 0), Version.parse("0.2.0-debug"))
        assertNull(Version.parse("0.2"))
        assertNull(Version.parse("a.b.c"))
        assertTrue(Version(0, 10, 0) > Version(0, 9, 9))
        assertTrue(Version(1, 0, 0) > Version(0, 99, 99))
        assertTrue(Version(0, 2, 1) > Version(0, 2, 0))
        assertEquals(0, Version(0, 2, 0).compareTo(Version(0, 2, 0)))
    }

    @Test
    fun `一天最多自動檢查一次`() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(UpdateCheck.shouldAutoCheck(null, 1000))
        assertFalse(UpdateCheck.shouldAutoCheck(1000, 1000 + 3 * 60 * 60 * 1000))
        assertFalse(UpdateCheck.shouldAutoCheck(1000, 1000 + day - 1))
        assertTrue(UpdateCheck.shouldAutoCheck(1000, 1000 + day))
        assertTrue(UpdateCheck.shouldAutoCheck(5 * day, day)) // 時鐘被調回
    }

    @Test
    fun `提示 有新版才提示,略過同一版不提示,更新的版本照常提示`() {
        assertEquals(rel("0.2.1"), UpdateCheck.banner("0.2.0", rel("0.2.1"), null))
        assertNull(UpdateCheck.banner("0.2.0", rel("0.2.0"), null))
        assertNull(UpdateCheck.banner("0.2.0", rel("0.1.9"), null))
        assertNull(UpdateCheck.banner("0.2.0", rel("0.2.1"), Version(0, 2, 1)))
        assertEquals(rel("0.3.0"), UpdateCheck.banner("0.2.0", rel("0.3.0"), Version(0, 2, 1)))
        assertEquals(rel("0.2.1"), UpdateCheck.banner("0.2.0-debug", rel("0.2.1"), null))
        assertNull(UpdateCheck.banner("壞掉", rel("0.2.1"), null))
        assertNull(UpdateCheck.banner("0.2.0", null, null))
    }

    @Test
    fun `手動檢查不受略過影響`() {
        assertEquals(ManualResult.NewVersion(rel("0.2.1")), UpdateCheck.manual("0.2.0", rel("0.2.1")))
        assertEquals(ManualResult.UpToDate, UpdateCheck.manual("0.2.0", rel("0.2.0")))
        assertEquals(ManualResult.Failed, UpdateCheck.manual("0.2.0", null))
    }
}
