package com.jmh273.easyytdllite.queue

import org.json.JSONArray
import org.json.JSONObject

/** 一筆下載紀錄。[uri] 是輸出檔的 MediaStore content URI;舊版留下的紀錄沒有,為 null。 */
data class HistoryEntry(val id: String, val kind: MediaKind, val uri: String?)

/**
 * 下載完成過的影片(add-gain-numbering-and-history-v2 design D4),舊到新排列,(id, kind) 不重複,最多 [LIMIT] 筆。
 * 和佇列分開保存:「清除已完成」不會清掉它。純邏輯。
 */
data class DownloadHistory(val entries: List<HistoryEntry> = emptyList()) {
    private val keys: Set<Pair<String, MediaKind>> by lazy { entries.mapTo(HashSet()) { it.id to it.kind } }

    /** 不重複的影片 id(舊到新)。 */
    val ids: List<String> get() = entries.map { it.id }.distinct()

    fun has(id: String, kind: MediaKind): Boolean = (id to kind) in keys

    /** 下載完成:同一組 (id, kind) 已存在時換成新的 [uri] 並移到最後。 */
    fun add(id: String, kind: MediaKind, uri: String?): DownloadHistory {
        val rest = entries.filterNot { it.id == id && it.kind == kind }
        return DownloadHistory((rest + HistoryEntry(id, kind, uri)).takeLast(LIMIT))
    }

    /** 從佇列併入已完成的 MP3:只補沒有的,不動已有的紀錄。 */
    fun addAll(more: List<String>): DownloadHistory {
        val fresh = more.distinct().filterNot { has(it, MediaKind.MP3) }
        if (fresh.isEmpty()) return this
        return DownloadHistory((entries + fresh.map { HistoryEntry(it, MediaKind.MP3, null) }).takeLast(LIMIT))
    }

    /**
     * 佇列中已完成的 MP3 併進來(每次啟動時做;見 HistoryStore)。MUST 只取 MP3:
     * [addAll] 記成沒有檔案位置的 MP3,而沒有位置的紀錄一律算已下載,MP4 混進來會讓那支影片在 MP3 下永遠不能勾(add-mp4-download)。
     */
    fun addDone(queue: Queue): DownloadHistory =
        addAll(queue.items.filter { it.status == Status.DONE && it.kind == MediaKind.MP3 }.map { it.id })

    /** 算「已下載」的影片 id:有 [kind] 的紀錄,而且 uri 為 null(舊紀錄)或在 [existingUris] 裡(檔案還在)。 */
    fun downloadedIds(kind: MediaKind, existingUris: Set<String>): Set<String> =
        entries.filter { it.kind == kind && (it.uri == null || it.uri in existingUris) }.mapTo(HashSet()) { it.id }

    // data class 的 equals 只看 entries;lazy 欄位不影響比較。
    override fun equals(other: Any?): Boolean = other is DownloadHistory && other.entries == entries
    override fun hashCode(): Int = entries.hashCode()

    companion object {
        const val LIMIT = 5000

        /**
         * v2:`entries` 是正式內容;另外寫一份 `ids`,讓只讀 `ids` 的舊版降版後不遺失紀錄。
         * `ids` 只放 MP3:舊版把 `ids` 都當 MP3,放進 MP4 會讓只下載過 MP4 的影片在舊版變成「已下載」(add-mp4-download design D2)。
         */
        fun encode(history: DownloadHistory): String {
            val entries = JSONArray()
            for (e in history.entries) {
                val o = JSONObject().put("id", e.id).put("kind", e.kind.name)
                e.uri?.let { o.put("uri", it) }
                entries.put(o)
            }
            return JSONObject().put("version", 2).put("entries", entries).put("ids", JSONArray(history.entries.filter { it.kind == MediaKind.MP3 }.map { it.id }.distinct())).toString()
        }

        /** 有 `entries` 讀 `entries`(壞掉或不認得格式的單筆略過);否則讀 v1 的 `ids`。空字串或壞檔回傳空紀錄。 */
        fun decode(json: String?): DownloadHistory {
            if (json.isNullOrBlank()) return DownloadHistory()
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return DownloadHistory()
            val entries = root.optJSONArray("entries")
            if (entries != null) {
                // 同一組 (id, kind) 重複時以後面的為準並排到後面,和 add 的規則相同;用 LinkedHashMap 保持線性時間。
                val byKey = LinkedHashMap<Pair<String, MediaKind>, HistoryEntry>()
                for (i in 0 until entries.length()) {
                    val o = entries.optJSONObject(i) ?: continue
                    val id = o.optString("id").ifEmpty { null } ?: continue
                    val kind = runCatching { MediaKind.valueOf(o.optString("kind")) }.getOrNull() ?: continue
                    val uri = if (o.has("uri") && !o.isNull("uri")) o.getString("uri") else null
                    val key = id to kind
                    byKey.remove(key)
                    byKey[key] = HistoryEntry(id, kind, uri)
                }
                return DownloadHistory(byKey.values.toList().takeLast(LIMIT))
            }
            val ids = root.optJSONArray("ids") ?: return DownloadHistory()
            return DownloadHistory().addAll((0 until ids.length()).mapNotNull { ids.optString(it).ifEmpty { null } })
        }
    }
}
