package com.jmh273.easyytdllite.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ProbeCommandsTest {
    private val out = "/data/cache/probe"

    private fun ProbeCommand.flag(name: String) = options.any { it.first == name }
    private fun ProbeCommand.value(name: String) = options.single { it.first == name }.second

    @Test
    fun `兩種模式都有詳細日誌、只抽音訊轉 MP3、寫影片資訊、不下載播放清單`() {
        for (mode in JsMode.entries) {
            val cmd = ProbeCommands.build(mode, out)
            assertTrue("$mode 缺 -v", cmd.flag("-v"))
            assertTrue("$mode 缺 -x", cmd.flag("-x"))
            assertEquals("mp3", cmd.value("--audio-format"))
            assertTrue("$mode 缺 --write-info-json", cmd.flag("--write-info-json"))
            assertTrue("$mode 缺 --no-playlist", cmd.flag("--no-playlist"))
        }
    }

    @Test
    fun `輸出檔放在指定目錄`() {
        val template = ProbeCommands.build(JsMode.QUICKJS, out).value("-o")!!
        assertTrue("輸出範本「$template」不在 $out 底下", template.startsWith("$out/"))
    }

    @Test
    fun `對照組在自訂指令停用 JS 執行環境`() {
        val cmd = ProbeCommands.build(JsMode.CONTROL, out)
        assertEquals(listOf("--no-js-runtimes"), cmd.customCommands)
        // 放在 options 會排在函式庫自己加的 --js-runtimes 之前,被它蓋回去。
        assertFalse(cmd.flag("--no-js-runtimes"))
    }

    @Test
    fun `QuickJS 模式不碰 JS 執行環境設定`() {
        val cmd = ProbeCommands.build(JsMode.QUICKJS, out)
        assertEquals(emptyList<String>(), cmd.customCommands)
        assertFalse(cmd.options.any { it.first.contains("js-runtimes") })
    }

    @Test
    fun `從分享文字取出網址`() {
        assertEquals("https://youtu.be/abc123?si=x", ProbeCommands.extractUrl("看看這個 https://youtu.be/abc123?si=x"))
        assertEquals("https://www.youtube.com/watch?v=abc", ProbeCommands.extractUrl("https://www.youtube.com/watch?v=abc\n"))
        assertEquals(null, ProbeCommands.extractUrl("沒有網址"))
        assertEquals(null, ProbeCommands.extractUrl(null))
    }

    @Test
    fun `紀錄資料夾名稱含時間與模式`() {
        val t = LocalDateTime.of(2026, 11, 5, 9, 3, 7)
        assertEquals("20261105-090307-quickjs", ProbeCommands.runFolderName(t, JsMode.QUICKJS))
        assertEquals("20261105-090307-control", ProbeCommands.runFolderName(t, JsMode.CONTROL))
    }
}
