package com.jmh273.easyytdllite.youtube

import com.jmh273.easyytdllite.share.VideoUrl

/**
 * 要列出影片的來源,以及對應的 yt-dlp 參數(split-lite-and-list-sources design D2、D3)。純邏輯。
 */
sealed class ListSource {
    /** 單支影片:也列出(只有一支),讓使用者先選格式再加入(share-video-to-url-tab design D1)。 */
    data class SingleVideo(val id: String) : ListSource() {
        override val limit get() = 1
        override fun target() = VideoUrl.canonical(id)
        override fun options() = listOf("--flat-playlist", "-J", "--no-playlist")
    }

    data class Playlist(val id: String) : ListSource() {
        override val limit get() = PLAYLIST_LIMIT
        override fun target() = "https://www.youtube.com/playlist?list=$id"
    }

    /** [path] 是 `@名稱`、`channel/UC…`、`c/名稱` 或 `user/名稱`。 */
    data class Channel(val path: String) : ListSource() {
        override val limit get() = CHANNEL_LIMIT
        // 不加 /videos 時,yt-dlp 回的是分頁(影片、Shorts、直播)的巢狀清單。
        override fun target() = "https://www.youtube.com/$path/videos"
    }

    data class Search(val query: String) : ListSource() {
        override val limit get() = SEARCH_LIMIT
        override fun target() = "ytsearch$SEARCH_LIMIT:${query.trim()}"
    }

    data object Unsupported : ListSource()

    open val limit: Int get() = 0

    open fun target(): String = throw IllegalStateException("$this 不是可列出的來源")

    open fun options(): List<String> = listOf("--flat-playlist", "-J", "--playlist-end", limit.toString())

    companion object {
        /** 播放清單最多列 200 支(design D2;🟡 自訂上限,避免上千支的清單讓手機等太久)。 */
        const val PLAYLIST_LIMIT = 200
        /** 頻道最近 30 支(使用者 2026-11-21 定)。 */
        const val CHANNEL_LIMIT = 30
        /** 搜尋 20 筆(使用者 2026-11-21 定)。 */
        const val SEARCH_LIMIT = 20

        private const val HOST = """https?://(?:www\.|m\.|music\.)?youtube\.com"""
        private val LIST = Regex("""$HOST/(?:playlist|watch)\?(?:[^\s#]*&)?list=([A-Za-z0-9_-]+)""")
        private val CHANNEL = Regex("""$HOST/(@[A-Za-z0-9._-]+|channel/UC[A-Za-z0-9_-]+|c/[A-Za-z0-9._-]+|user/[A-Za-z0-9._-]+)(?=[/?#\s]|$)""")

        /** 分類順序:有影片 id → 單支;`list=` → 播放清單(`RD` 開頭的合輯不支援);頻道路徑 → 頻道;其他不認得。 */
        fun classify(text: String?): ListSource {
            if (text.isNullOrBlank()) return Unsupported
            VideoUrl.videoId(text)?.let { return SingleVideo(it) }
            LIST.find(text)?.groupValues?.get(1)?.let { id ->
                return if (id.startsWith("RD")) Unsupported else Playlist(id)
            }
            CHANNEL.find(text)?.groupValues?.get(1)?.let { return Channel(it) }
            return Unsupported
        }
    }
}
