package com.jmh273.easyytdllite.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun k(id: String, kind: MediaKind = MediaKind.MP3) = QueueItem.keyOf(id, kind)

class QueueTest {
    private fun q(vararg ids: String): Queue =
        ids.fold(Queue()) { acc, id -> acc.add(id, "u/$id", 0).queue }

    private fun Queue.of(id: String) = items.single { it.id == id }

    @Test
    fun `加入時依 id 去重,只擋未完成的`() {
        val first = Queue().add("a", "u/a", 1)
        assertTrue(first.added)
        assertEquals(Status.WAITING, first.queue.of("a").status)
        assertFalse(first.queue.add("a", "u/a", 2).added)
        val running = first.queue.start(k("a"))
        assertFalse(running.add("a", "u/a", 2).added)
        // 已完成的可以再加一次(例如想重新下載);佇列沒有未結束的項目,所以舊的完成紀錄被清掉(auto-refresh-and-session-queue D4)
        val done = running.complete(k("a"), "Music/x.mp3")
        val again = done.add("a", "u/a", 3)
        assertTrue(again.added)
        assertEquals(listOf(Status.WAITING), again.queue.items.map { it.status })
    }

    @Test
    fun `依加入順序取下一個等待中的`() {
        val queue = q("a", "b", "c").start(k("a"))
        assertEquals("b", queue.next()?.id)
        assertNull(Queue().next())
        assertNull(q("a").start(k("a")).next())
    }

    @Test
    fun `完成記下存檔位置,標題可以更新`() {
        val queue = q("a").start(k("a")).setTitle(k("a"), "歌名").complete(k("a"), "Music/EasyYTDL-Lite/2026-11-05/歌名.mp3")
        with(queue.of("a")) {
            assertEquals(Status.DONE, status)
            assertEquals("歌名", title)
            assertEquals("Music/EasyYTDL-Lite/2026-11-05/歌名.mp3", savedPath)
        }
    }

    @Test
    fun `網路失敗最多重試 3 次,第 4 次標記失敗`() {
        var queue = q("a")
        repeat(3) { n ->
            queue = queue.start(k("a")).failNetwork(k("a"))
            assertEquals(Status.WAITING_NETWORK, queue.of("a").status)
            assertEquals(n + 1, queue.of("a").networkRetries)
            queue = queue.networkRestored()
            assertEquals(Status.WAITING, queue.of("a").status)
        }
        queue = queue.start(k("a")).failNetwork(k("a"))
        assertEquals(Status.FAILED, queue.of("a").status)
        assertEquals(Failure.NETWORK, queue.of("a").failure)
    }

    @Test
    fun `其他失敗直接標記並保留原因`() {
        val queue = q("a").start(k("a")).fail(k("a"), Failure.YOUTUBE_REJECTED)
        assertEquals(Status.FAILED, queue.of("a").status)
        assertEquals(Failure.YOUTUBE_REJECTED, queue.of("a").failure)
    }

    @Test
    fun `App 重開時處理中的回到等待中`() {
        val queue = q("a", "b").start(k("a")).recoverAfterRestart()
        assertEquals(Status.WAITING, queue.of("a").status)
        assertEquals(Status.WAITING, queue.of("b").status)
    }

    @Test
    fun `重試讓失敗回到等待中並歸零重試次數`() {
        var queue = q("a")
        repeat(4) { queue = queue.start(k("a")).failNetwork(k("a")).networkRestored() }
        assertEquals(Status.FAILED, queue.of("a").status)
        queue = queue.retry(k("a"))
        assertEquals(Status.WAITING, queue.of("a").status)
        assertEquals(0, queue.of("a").networkRetries)
        assertNull(queue.of("a").failure)
    }

    @Test
    fun `更新後只把指定原因的失敗改回等待中`() {
        val queue = q("a", "b", "c")
            .start(k("a")).fail(k("a"), Failure.YOUTUBE_REJECTED)
            .start(k("b")).fail(k("b"), Failure.UNAVAILABLE)
            .retryAll(Failure.YOUTUBE_REJECTED)
        assertEquals(Status.WAITING, queue.of("a").status)
        assertEquals(Status.FAILED, queue.of("b").status)
        assertEquals(Status.WAITING, queue.of("c").status)
    }

    @Test
    fun `處理中的不能移除,已完成的由清除處理`() {
        val queue = q("a", "b", "c").start(k("a")).complete(k("a"), "p").start(k("b"))
        assertEquals(3, queue.remove(k("b")).items.size)
        assertEquals(listOf("a", "b"), queue.remove(k("c")).items.map { it.id })
        assertEquals(listOf("b", "c"), queue.clearDone().items.map { it.id })
    }

    @Test
    fun `找不到 id 時佇列不變`() {
        val queue = q("a")
        assertEquals(queue, queue.start(k("x")).complete(k("x"), "p").fail(k("x"), Failure.OTHER).retry(k("x")))
    }

    @Test
    fun `已完成的項目不算已在佇列,其他狀態都算`() {
        var q = Queue()
        for (id in listOf("w", "r", "n", "d", "f")) q = q.add(id, "u/$id", 0).queue
        q = q.start(k("r")).start(k("n")).failNetwork(k("n")).start(k("d")).complete(k("d"), "p").start(k("f")).fail(k("f"), Failure.OTHER)
        assertEquals(setOf("w", "r", "n", "f"), q.inQueueIds(MediaKind.MP3))
    }

    @Test
    fun `加入時記下格式,預設 MP3`() {
        assertEquals(MediaKind.MP3, Queue().add("a", "u/a", 1).queue.items.single().kind)
        assertEquals(MediaKind.MP3, Queue().add("a", "u/a", 1, MediaKind.MP3).queue.items.single().kind)
    }
}

class QueueClaimTest {
    private fun q(vararg ids: String): Queue = ids.fold(Queue()) { acc, id -> acc.add(id, "u/$id", 0).queue }

    @Test
    fun `依加入順序取第一個等待中並標成處理中`() {
        val (next, item) = q("a", "b").claimNext(running = 0, limit = 2)
        assertEquals("a", item?.id)
        assertEquals(Status.RUNNING, next.items.single { it.id == "a" }.status)
        assertEquals(Status.WAITING, next.items.single { it.id == "b" }.status)
    }

    @Test
    fun `連續取兩次拿到不同項目`() {
        val (q1, first) = q("a", "b", "c").claimNext(0, 2)
        val (q2, second) = q1.claimNext(1, 2)
        assertEquals(listOf("a", "b"), listOf(first?.id, second?.id))
        assertEquals(listOf(Status.RUNNING, Status.RUNNING, Status.WAITING), q2.items.map { it.status })
    }

    @Test
    fun `到達上限時不取`() {
        val start = q("a", "b")
        val (same, item) = start.claimNext(running = 2, limit = 2)
        assertNull(item)
        assertEquals(start, same)
    }

    @Test
    fun `跳過等待網路、處理中、完成與失敗`() {
        val base = q("net", "run", "done", "bad", "ok")
            .start(k("net")).failNetwork(k("net"))
            .start(k("run"))
            .start(k("done")).complete(k("done"), "x")
            .start(k("bad")).fail(k("bad"), Failure.OTHER)
        val (_, item) = base.claimNext(0, 3)
        assertEquals("ok", item?.id)
    }

    @Test
    fun `沒有等待中時回傳 null`() {
        assertNull(Queue().claimNext(0, 2).second)
        assertNull(q("a").start(k("a")).complete(k("a"), "x").claimNext(0, 2).second)
    }

    // ── 同一支影片的兩種格式(add-mp4-download design D1) ──

    private fun both(): Queue {
        val mp3 = Queue().add("a", "u/a", 1).queue
        val mp4 = mp3.add("a", "u/a", 2, MediaKind.MP4, 720)
        assertTrue(mp4.added)
        return mp4.queue
    }

    private fun Queue.of(id: String, kind: MediaKind) = items.single { it.id == id && it.kind == kind }

    @Test
    fun `同一支影片可以分別以兩種格式加入,MP4 記下解析度`() {
        val queue = both()
        assertEquals(listOf(MediaKind.MP3, MediaKind.MP4), queue.items.map { it.kind })
        assertEquals(720, queue.of("a", MediaKind.MP4).height)
        assertNull(queue.of("a", MediaKind.MP3).height)
        assertFalse(queue.add("a", "u/a", 3, MediaKind.MP4, 1080).added)
    }

    @Test
    fun `key 由 id 與格式組成`() {
        assertEquals("a-b_c_MP4", QueueItem(id = "a-b_c", url = "u", addedAt = 0, kind = MediaKind.MP4).key)
    }

    @Test
    fun `兩種格式同時處理,各自完成與失敗`() {
        var queue = both()
        val (q1, first) = queue.claimNext(0, 2)
        val (q2, second) = q1.claimNext(1, 2)
        assertEquals(MediaKind.MP3, first?.kind)
        assertEquals(MediaKind.MP4, second?.kind)
        queue = q2.complete(k("a", MediaKind.MP4), "Movies/x.mp4")
        assertEquals(Status.DONE, queue.of("a", MediaKind.MP4).status)
        assertEquals(Status.RUNNING, queue.of("a", MediaKind.MP3).status)
        queue = queue.fail(k("a"), Failure.OTHER)
        assertEquals(Status.FAILED, queue.of("a", MediaKind.MP3).status)
        assertEquals(Status.DONE, queue.of("a", MediaKind.MP4).status)
    }

    @Test
    fun `只移除指定格式的項目`() {
        val queue = both().remove(k("a", MediaKind.MP4))
        assertEquals(listOf(MediaKind.MP3), queue.items.map { it.kind })
    }

    @Test
    fun `標題與重試只動指定格式`() {
        val running = both().start(k("a")).start(k("a", MediaKind.MP4))
        val titled = running.setTitle(k("a", MediaKind.MP4), "影片")
        assertEquals("影片", titled.of("a", MediaKind.MP4).title)
        assertNull(titled.of("a", MediaKind.MP3).title)
        val failed = titled.fail(k("a"), Failure.OTHER).fail(k("a", MediaKind.MP4), Failure.OTHER).retry(k("a", MediaKind.MP4))
        assertEquals(Status.WAITING, failed.of("a", MediaKind.MP4).status)
        assertEquals(Status.FAILED, failed.of("a", MediaKind.MP3).status)
        // 排在前面的 MP3 也要找得到(不是「同一個 id 的最後一筆」)
        val mp3Titled = running.setTitle(k("a"), "歌")
        assertEquals("歌", mp3Titled.of("a", MediaKind.MP3).title)
        assertNull(mp3Titled.of("a", MediaKind.MP4).title)
        val mp3Retried = failed.fail(k("a", MediaKind.MP4), Failure.OTHER).let { it.start(k("a", MediaKind.MP4)).fail(k("a", MediaKind.MP4), Failure.OTHER) }.retry(k("a"))
        assertEquals(Status.WAITING, mp3Retried.of("a", MediaKind.MP3).status)
        assertEquals(Status.FAILED, mp3Retried.of("a", MediaKind.MP4).status)
    }

    @Test
    fun `已在佇列依格式分開`() {
        val queue = Queue().add("a", "u/a", 1).queue
        assertEquals(setOf("a"), queue.inQueueIds(MediaKind.MP3))
        assertEquals(emptySet<String>(), queue.inQueueIds(MediaKind.MP4))
    }

    // ── 只保留這一批(auto-refresh-and-session-queue D4) ──

    private fun item(id: String, status: Status) = QueueItem(id = id, url = "u/$id", addedAt = 0, status = status)

    @Test
    fun `沒有未結束的項目時加入,完成與失敗都清掉`() {
        val old = Queue(listOf(item("d1", Status.DONE), item("f1", Status.FAILED), item("d2", Status.DONE)))
        val r = old.add("n", "u/n", 9)
        assertTrue(r.added)
        assertEquals(listOf("n"), r.queue.items.map { it.id })
    }

    @Test
    fun `有等待中、處理中或等待網路時加入,原項目都保留`() {
        for (unfinished in listOf(Status.WAITING, Status.RUNNING, Status.WAITING_NETWORK)) {
            val old = Queue(listOf(item("d1", Status.DONE), item("f1", Status.FAILED), item("x", unfinished)))
            val r = old.add("n", "u/n", 9)
            assertEquals("$unfinished", listOf("d1", "f1", "x", "n"), r.queue.items.map { it.id })
        }
    }

    @Test
    fun `一批連續加入只清一次`() {
        val old = Queue(listOf(item("d1", Status.DONE), item("f1", Status.FAILED)))
        val batch = listOf("a", "b", "c").fold(old) { acc, id -> acc.add(id, "u/$id", 1).queue }
        assertEquals(listOf("a", "b", "c"), batch.items.map { it.id })
    }

    @Test
    fun `重試不會清掉其他項目`() {
        val old = Queue(listOf(item("d1", Status.DONE), item("f1", Status.FAILED)))
        assertEquals(listOf("d1", "f1"), old.retry(k("f1")).items.map { it.id })
        assertEquals(listOf("d1", "f1"), old.retryAll(Failure.OTHER).items.map { it.id })
    }
}
