package com.jmh273.easyytdllite.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSelectionTest {
    private val videos = listOf("a", "b", "c", "d").map { Video(it, "影片$it") }
    private val sel = VideoSelection(inQueue = setOf("b", "c"), downloaded = setOf("c", "d"))

    @Test
    fun `已下載優先於已在佇列`() {
        assertEquals(VideoState.SELECTABLE, sel.state("a"))
        assertEquals(VideoState.IN_QUEUE, sel.state("b"))
        assertEquals(VideoState.DOWNLOADED, sel.state("c"))
        assertEquals(VideoState.DOWNLOADED, sel.state("d"))
    }

    @Test
    fun `只有可勾選的能切換`() {
        assertEquals(setOf("a"), sel.toggle(emptySet(), "a"))
        assertEquals(emptySet<String>(), sel.toggle(setOf("a"), "a"))
        assertEquals(emptySet<String>(), sel.toggle(emptySet(), "b"))
        assertEquals(emptySet<String>(), sel.toggle(emptySet(), "d"))
    }

    @Test
    fun `依清單順序回傳仍可勾選的已選影片`() {
        // 勾選之後 c 才被加入佇列(例如從分享加入):不再回傳
        val later = VideoSelection(inQueue = setOf("c"), downloaded = emptySet())
        assertEquals(listOf("a", "b"), later.toAdd(videos, setOf("b", "a", "c")).map { it.id })
    }

    @Test
    fun `全選只勾可以勾選的影片`() {
        assertEquals(setOf("a"), sel.selectAll(videos))
        assertEquals(setOf("a", "b", "c", "d"), VideoSelection(emptySet(), emptySet()).selectAll(videos))
    }
}
