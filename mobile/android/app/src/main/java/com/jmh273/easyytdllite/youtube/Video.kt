package com.jmh273.easyytdllite.youtube

/** 清單中的一支影片(yt-dlp `--flat-playlist` 的一個項目)。[durationSeconds] 沒有時是 null。 */
data class Video(val id: String, val title: String, val durationSeconds: Long? = null)
