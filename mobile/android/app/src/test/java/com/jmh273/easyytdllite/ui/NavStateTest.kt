package com.jmh273.easyytdllite.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStateTest {
    @Test
    fun `預設停在搜尋分頁、設定頁關著`() {
        assertEquals(NavState(Tab.SEARCH, false), NavState())
    }

    @Test
    fun `關掉設定頁回到原本的分頁`() {
        val s = NavState().select(Tab.URL).openSettings()
        assertTrue(s.settingsOpen)
        assertEquals(NavState(Tab.URL, false), s.closeSettings())
    }

    @Test
    fun `分享單支到佇列、分享清單到網址,都會關掉設定頁`() {
        val inSettings = NavState(Tab.SEARCH, true)
        assertEquals(NavState(Tab.QUEUE, false), inSettings.onShareVideo())
        assertEquals(NavState(Tab.URL, false), inSettings.onShareList())
    }

    @Test
    fun `編碼後解碼回到相同狀態`() {
        for (tab in Tab.entries) for (open in listOf(false, true)) {
            val s = NavState(tab, open)
            assertEquals(s, NavState.decode(s.encode()))
        }
    }

    @Test
    fun `壞值回到預設`() {
        for (bad in listOf(null, "", "SEARCH", "LATEST:0", "URL:2", "URL:0:0", "url:0")) {
            assertEquals(bad.toString(), NavState(), NavState.decode(bad))
        }
        assertFalse(NavState.decode("QUEUE:0").settingsOpen)
    }
}
