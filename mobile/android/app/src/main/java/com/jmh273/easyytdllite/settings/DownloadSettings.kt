package com.jmh273.easyytdllite.settings

import com.jmh273.easyytdllite.queue.MediaKind
import com.jmh273.easyytdllite.video.VideoFormat
import org.json.JSONObject

/** 下載設定(spec app-settings「同時下載支數」「目標音量」)。純邏輯。 */
data class DownloadSettings(
    val concurrency: Int,
    val targetDb: Int = DEFAULT_TARGET_DB,
    /** 加入佇列時用的格式與 MP4 解析度:上次的選擇(add-mp4-download design D3)。 */
    val kind: MediaKind = MediaKind.MP3,
    val height: Int = VideoFormat.DEFAULT_HEIGHT,
) {
    companion object {
        val RANGE = 1..3
        val TARGET_RANGE = 75..95
        const val DEFAULT_TARGET_DB = 89
        val DEFAULT = DownloadSettings(2, DEFAULT_TARGET_DB)

        fun encode(s: DownloadSettings): String =
            JSONObject().put("version", 1).put("concurrency", s.concurrency).put("targetDb", s.targetDb)
                .put("kind", s.kind.name).put("height", s.height).toString()

        /** 逐欄判斷:壞檔回傳預設值;某一欄缺少或不合法時只把那一欄換成預設值。 */
        fun decode(json: String?): DownloadSettings {
            if (json.isNullOrBlank()) return DEFAULT
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return DEFAULT
            return DownloadSettings(
                concurrency = o.intIn("concurrency", RANGE) ?: DEFAULT.concurrency,
                targetDb = o.intIn("targetDb", TARGET_RANGE) ?: DEFAULT.targetDb,
                kind = runCatching { MediaKind.valueOf(o.getString("kind")) }.getOrNull() ?: DEFAULT.kind,
                height = o.intIn("height", VideoFormat.HEIGHTS) ?: DEFAULT.height,
            )
        }

        /** 只接受 JSON 整數(不接受小數、字串),而且在 [range] 內。 */
        private fun JSONObject.intIn(key: String, range: Iterable<Int>): Int? {
            val v = opt(key)
            val n = when (v) {
                is Int -> v
                is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else null
                else -> null
            }
            return n?.takeIf { it in range }
        }
    }
}
