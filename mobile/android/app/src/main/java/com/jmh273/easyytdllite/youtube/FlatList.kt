package com.jmh273.easyytdllite.youtube

import org.json.JSONObject

/** 一次列出的結果:清單標題與影片(依 yt-dlp 回傳的順序)。 */
data class ListPage(val title: String, val videos: List<Video>)

/** 解析 `yt-dlp --flat-playlist -J` 的輸出(split-lite-and-list-sources design D2)。純邏輯。 */
object FlatList {
    private val NOT_DOWNLOADABLE = setOf("is_live", "is_upcoming")
    private val VIDEO_ID = Regex("""^[A-Za-z0-9_-]{11}$""")

    /**
     * [stdout] 整段是 JSON 就用整段;前面有雜訊時取最後一行以 `{` 開頭的 JSON。都不是時回傳 null。
     * 只收 id 是 11 字元的影片項目;頻道、子清單(搜尋結果裡會有)、直播中與尚未開播的略過;重複的 id 只留第一個。
     */
    fun parse(stdout: String?): ListPage? {
        if (stdout == null) return null
        val lastLine = stdout.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("{") }
        val root = listOfNotNull(stdout.trim(), lastLine).firstNotNullOfOrNull { text ->
            runCatching { JSONObject(text) }.getOrNull()
        } ?: return null
        // 單支影片(share-video-to-url-tab design D1):沒有 entries,根物件本身就是影片;當成只有一支、標題留空的清單。
        if (!root.has("entries") && root.optString("_type", "video") == "video" && VIDEO_ID.matches(root.optString("id"))) {
            val v = toVideo(root)
            return ListPage("", listOfNotNull(v))
        }
        val entries = root.optJSONArray("entries")
        val seen = HashSet<String>()
        val videos = ArrayList<Video>()
        if (entries != null) {
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val ieKey = e.optString("ie_key")
                if (ieKey.isNotEmpty() && ieKey != "Youtube") continue
                val v = toVideo(e) ?: continue
                if (!seen.add(v.id)) continue
                videos += v
            }
        }
        return ListPage(root.optString("title").takeUnless { root.isNull("title") }.orEmpty(), videos)
    }

    /** 一個影片物件;id 不是 11 字元,或直播中、尚未開播(下載不會結束;已結束的直播照收)時是 null。 */
    private fun toVideo(e: JSONObject): Video? {
        val id = e.optString("id")
        if (!VIDEO_ID.matches(id)) return null
        if (e.optString("live_status") in NOT_DOWNLOADABLE) return null
        val title = e.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: id
        val duration = if (e.isNull("duration")) null else e.optDouble("duration").takeIf { !it.isNaN() }?.toLong()
        return Video(id, title, duration)
    }

    /** 「3:35」「1:02:03」;沒有長度時是 null。 */
    fun durationText(seconds: Long?): String? {
        if (seconds == null) return null
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
