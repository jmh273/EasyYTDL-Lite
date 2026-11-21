package com.jmh273.easyytdllite.queue

import com.jmh273.easyytdllite.video.VideoFormat
import org.json.JSONArray
import org.json.JSONObject

/** 佇列與 JSON 互轉。純邏輯(org.json 在 Android 內建,JVM 測試用 org.json:json)。 */
object QueueCodec {
    fun encode(queue: Queue): String {
        val array = JSONArray()
        for (item in queue.items) {
            val o = JSONObject()
                .put("id", item.id)
                .put("url", item.url)
                .put("addedAt", item.addedAt)
                .put("status", item.status.name)
                .put("networkRetries", item.networkRetries)
                .put("kind", item.kind.name)
            item.title?.let { o.put("title", it) }
            item.failure?.let { o.put("failure", it.name) }
            item.savedPath?.let { o.put("savedPath", it) }
            item.height?.let { o.put("height", it) }
            array.put(o)
        }
        return JSONObject().put("version", 1).put("items", array).toString()
    }

    /** 空字串、壞檔或格式不符時回傳空佇列,不丟例外;不認得的項目略過。 */
    fun decode(json: String?): Queue {
        if (json.isNullOrBlank()) return Queue()
        val array = runCatching { JSONObject(json).getJSONArray("items") }.getOrNull() ?: return Queue()
        val items = (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                // 舊檔沒有 kind:當作 MP3;不認得的格式(較新版本寫的)整項略過。
                val kind = if (o.has("kind")) MediaKind.valueOf(o.getString("kind")) else MediaKind.MP3
                QueueItem(
                    id = o.getString("id"),
                    url = o.getString("url"),
                    addedAt = o.getLong("addedAt"),
                    status = Status.valueOf(o.getString("status")),
                    title = if (o.has("title")) o.getString("title") else null,
                    failure = if (o.has("failure")) Failure.valueOf(o.getString("failure")) else null,
                    networkRetries = o.optInt("networkRetries", 0),
                    savedPath = if (o.has("savedPath")) o.getString("savedPath") else null,
                    kind = kind,
                    // MP4 才有解析度;缺少或不合法時用預設值(add-mp4-download design D1)。
                    height = if (kind == MediaKind.MP4) VideoFormat.normalize(if (o.has("height")) o.optInt("height") else null) else null,
                )
            }.getOrNull()
        }
        return Queue(items)
    }
}
