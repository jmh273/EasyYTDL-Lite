package com.jmh273.easyytdllite.youtube

/** 影片縮圖網址(add-video-thumbnails design D1)。純邏輯。 */
object Thumbnails {
    /** 320×180,16:9 沒有黑邊。不呼叫 Data API。 */
    fun video(id: String): String = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}
