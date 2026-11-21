package com.jmh273.easyytdllite.share

/** 從分享文字取出 YouTube 影片 id,組成標準網址。純邏輯。 */
object VideoUrl {
    private const val HOST = """(?:www\.|m\.|music\.)?youtube\.com"""
    private const val ID = """([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])"""
    private val PATTERNS = listOf(
        Regex("""https?://(?:www\.)?youtu\.be/$ID"""),
        Regex("""https?://$HOST/(?:shorts|live|embed)/$ID"""),
        Regex("""https?://$HOST/watch\?(?:[^\s#]*&)?v=$ID"""),
    )

    /** 找不到 YouTube 影片 id 時回傳 null。 */
    fun videoId(text: String?): String? {
        if (text == null) return null
        return PATTERNS.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
    }

    fun canonical(id: String): String = "https://www.youtube.com/watch?v=$id"
}
