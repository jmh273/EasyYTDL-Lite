package com.jmh273.easyytdllite.queue

/** 狀態名稱會存進 queue.json,發版後 MUST NOT 改名。 */
enum class Status { WAITING, RUNNING, WAITING_NETWORK, DONE, FAILED }

/** 失敗原因。名稱會存進 queue.json,發版後 MUST NOT 改名。 */
enum class Failure { NETWORK, YOUTUBE_REJECTED, BOT_CHECK, UNAVAILABLE, OTHER }

data class QueueItem(
    val id: String,
    val url: String,
    val addedAt: Long,
    val status: Status = Status.WAITING,
    val title: String? = null,
    val failure: Failure? = null,
    val networkRetries: Int = 0,
    val savedPath: String? = null,
    val kind: MediaKind = MediaKind.MP3,
    /** MP4 的解析度上限(add-mp4-download design D1);MP3 是 null。 */
    val height: Int? = null,
) {
    /** 項目的身分:同一支影片的 MP3 和 MP4 是不同項目(add-mp4-download design D1)。 */
    val key: String get() = keyOf(id, kind)

    companion object {
        fun keyOf(id: String, kind: MediaKind): String = "${id}_${kind.name}"
    }
}

/**
 * 下載佇列的狀態機。每個操作都回傳新的 [Queue],不改動原物件。純邏輯。
 * 找不到 id 或狀態不允許的操作,回傳原佇列。
 */
data class Queue(val items: List<QueueItem> = emptyList()) {

    data class AddResult(val queue: Queue, val added: Boolean)

    /**
     * 佇列只保留這一批(auto-refresh-and-session-queue D4):沒有未結束的項目時,先清掉所有完成與失敗的舊項目再加入。
     * 批次加入是連續呼叫這裡,第一支加入後就有等待中的項目,後面不會再清。
     */
    fun add(id: String, url: String, now: Long, kind: MediaKind = MediaKind.MP3, height: Int? = null): AddResult {
        val base = if (items.none { it.status in UNFINISHED }) emptyList() else items
        // 去重看 (id, kind):之後同一支影片可以分別以 MP3 和 MP4 排進佇列(add-gain-numbering-and-history-v2 D6)。
        val pending = base.any { it.id == id && it.kind == kind && it.status != Status.DONE && it.status != Status.FAILED }
        if (pending) return AddResult(this, false)
        // 這一批裡同一支的舊紀錄(已完成或失敗)保留在原處,新的排到最後。
        return AddResult(Queue(base + QueueItem(id = id, url = url, addedAt = now, kind = kind, height = height)), true)
    }

    /**
     * 瀏覽頁要標示「已在佇列」的影片 id:佇列裡還沒完成的項目(失敗的也算,可以在佇列頁籤重試)。
     * 已完成的不算:檔案刪掉之後要能從瀏覽頁重新勾選(add-gain-numbering-and-history-v2,實機 5.4 發現)。
     */
    fun inQueueIds(kind: MediaKind): Set<String> =
        items.filter { it.kind == kind && it.status != Status.DONE }.mapTo(HashSet()) { it.id }

    /** 依加入順序,第一個等待中的項目。 */
    fun next(): QueueItem? = items.firstOrNull { it.status == Status.WAITING }

    /**
     * 平行下載用(add-drawer-and-parallel-downloads design D4):依加入順序取第一個等待中,並在同一次更新裡標成處理中。
     * [running] 是呼叫端自己正在跑的工作數;到達 [limit] 或沒有等待中時回傳 null。
     */
    fun claimNext(running: Int, limit: Int): Pair<Queue, QueueItem?> {
        if (running >= limit) return this to null
        val item = next() ?: return this to null
        val next = start(item.key)
        return next to next.items.last { it.key == item.key && it.status == Status.RUNNING }
    }

    fun start(key: String): Queue = update(key, { it.status == Status.WAITING }) { it.copy(status = Status.RUNNING) }

    fun setTitle(key: String, title: String): Queue = update(key, { it.status != Status.DONE }) { it.copy(title = title) }

    fun complete(key: String, savedPath: String): Queue =
        update(key, { it.status == Status.RUNNING }) { it.copy(status = Status.DONE, savedPath = savedPath, failure = null) }

    /** 網路失敗:重試次數加 1;超過 [MAX_NETWORK_RETRIES] 次就標記失敗。 */
    fun failNetwork(key: String): Queue = update(key, { it.status == Status.RUNNING }) {
        val retries = it.networkRetries + 1
        if (retries > MAX_NETWORK_RETRIES) it.copy(status = Status.FAILED, failure = Failure.NETWORK, networkRetries = retries)
        else it.copy(status = Status.WAITING_NETWORK, networkRetries = retries)
    }

    fun fail(key: String, failure: Failure): Queue =
        update(key, { it.status == Status.RUNNING }) { it.copy(status = Status.FAILED, failure = failure) }

    fun networkRestored(): Queue = mapAll({ it.status == Status.WAITING_NETWORK }) { it.copy(status = Status.WAITING) }

    /** App 重開:處理中的項目回到等待中。 */
    fun recoverAfterRestart(): Queue = mapAll({ it.status == Status.RUNNING }) { it.copy(status = Status.WAITING) }

    /** 使用者重試:失敗回到等待中,重試次數歸零。 */
    fun retry(key: String): Queue = update(key, { it.status == Status.FAILED }) { reset(it) }

    /** 更新 yt-dlp 後:把指定原因的失敗項目全部改回等待中。 */
    fun retryAll(failure: Failure): Queue = mapAll({ it.status == Status.FAILED && it.failure == failure }) { reset(it) }

    /** 只能移除等待中、等待網路或失敗的項目。 */
    fun remove(key: String): Queue =
        Queue(items.filterNot { matches(it, key) && it.status in REMOVABLE })

    fun clearDone(): Queue = Queue(items.filterNot { it.status == Status.DONE })

    private fun reset(item: QueueItem) = item.copy(status = Status.WAITING, failure = null, networkRetries = 0)

    /** 改同一個 key 中「目前這一筆」(最後加入、狀態允許的那筆)。 */
    private fun update(key: String, allowed: (QueueItem) -> Boolean, change: (QueueItem) -> QueueItem): Queue {
        val index = items.indexOfLast { matches(it, key) && allowed(it) }
        if (index < 0) return this
        return Queue(items.toMutableList().also { it[index] = change(it[index]) })
    }

    private fun matches(item: QueueItem, key: String): Boolean = item.key == key

    private fun mapAll(match: (QueueItem) -> Boolean, change: (QueueItem) -> QueueItem): Queue =
        Queue(items.map { if (match(it)) change(it) else it })

    companion object {
        const val MAX_NETWORK_RETRIES = 3
        private val REMOVABLE = setOf(Status.WAITING, Status.WAITING_NETWORK, Status.FAILED)
        private val UNFINISHED = setOf(Status.WAITING, Status.WAITING_NETWORK, Status.RUNNING)
    }
}
