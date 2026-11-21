package com.jmh273.easyytdllite.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkVerifyTest {
    private fun temp(text: String) = File.createTempFile("apk", ".bin").apply { writeText(text); deleteOnExit() }

    @Test
    fun `SHA-256 與大小都相符才算通過`() {
        val f = temp("abc")
        // "abc" 的 SHA-256(FIPS 180-2 測試向量)
        val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(abc, ApkVerify.sha256Hex(f))
        assertTrue(ApkVerify.matches(f, ApkAsset("a.apk", "u", 3, abc)))
        assertFalse(ApkVerify.matches(f, ApkAsset("a.apk", "u", 4, abc)))
        assertFalse(ApkVerify.matches(f, ApkAsset("a.apk", "u", 3, abc.replaceFirst('b', 'c'))))
        assertTrue(ApkVerify.matches(f, ApkAsset("a.apk", "u", 3, abc.uppercase())))
    }

    @Test
    fun `舊檔清理 版號不大於目前的與沒完成的`() {
        val names = listOf("app-release-v0.2.0.apk", "app-release-v0.2.1.apk", "app-release-v0.2.2.apk", "app-release-v0.2.3.apk.part", "其他.txt")
        assertEquals(
            listOf("app-release-v0.2.0.apk", "app-release-v0.2.1.apk", "app-release-v0.2.3.apk.part"),
            ApkVerify.stale(names, "0.2.1"),
        )
    }
}
