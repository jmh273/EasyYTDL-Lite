package com.jmh273.easyytdllite.video

/** MP4 的解析度與 yt-dlp 格式選擇(add-mp4-download design D4)。純邏輯。 */
object VideoFormat {
    /** 選單上的解析度,高到低。 */
    val HEIGHTS = listOf(1080, 720, 480, 360)
    const val DEFAULT_HEIGHT = 720

    /** 不在 [HEIGHTS] 裡的值換成 [DEFAULT_HEIGHT]。 */
    fun normalize(height: Int?): Int = height?.takeIf { it in HEIGHTS } ?: DEFAULT_HEIGHT

    /**
     * spec video-output「格式選擇」的 1–4;每一段分橫式(比高度)與直式(比寬度),也就是以短邊為上限,
     * 和 YouTube 標示的 1080p、720p 一致(add-mp4-download design D4)。
     * 全部不符合時 yt-dlp 回報格式不存在,不會改取超過上限的解析度。
     */
    fun selector(height: Int): String {
        val landscape = "[aspect_ratio>=1][height<=$height]"
        val portrait = "[aspect_ratio<1][width<=$height]"
        return listOf("bv[vcodec^=avc1]%s+ba[ext=m4a]", "bv%s+ba[ext=m4a]", "bv*%s+ba", "b%s")
            .flatMap { listOf(it.format(landscape), it.format(portrait)) }
            .joinToString("/")
    }
}
