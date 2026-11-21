package com.jmh273.easyytdllite.ytdlp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpPolicyTest {
    @Test
    fun `內建版且這次還沒試過才先更新`() {
        assertTrue(YtDlpPolicy.shouldUpdateBeforeDownload(isBundled = true, attemptedThisRun = false))
        assertFalse(YtDlpPolicy.shouldUpdateBeforeDownload(isBundled = true, attemptedThisRun = true))
        assertFalse(YtDlpPolicy.shouldUpdateBeforeDownload(isBundled = false, attemptedThisRun = false))
    }

    @Test
    fun `新版讀不到版本號就還原`() {
        assertTrue(YtDlpPolicy.mustRestoreAfterUpdate(null))
        assertTrue(YtDlpPolicy.mustRestoreAfterUpdate("  "))
        assertTrue(YtDlpPolicy.mustRestoreAfterUpdate("Traceback (most recent call last):"))
        assertFalse(YtDlpPolicy.mustRestoreAfterUpdate("2026.10.26\n"))
    }
}
