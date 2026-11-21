package com.jmh273.easyytdllite.video

/**
 * MP4 的兩段式進度(add-mp4-download design D6):yt-dlp 先下載影片軌再下載音軌,回報的進度會從 0 跑到 100 兩次。
 * 第一段對應 0–[SPLIT],第二段對應 [SPLIT]–100。純邏輯,不可變。
 */
data class MergeProgress(val second: Boolean = false, val last: Float = 0f, val shown: Float = 0f) {
    /** 收到 yt-dlp 回報的 [p](0–100),回傳新狀態;[shown] 是要顯示的整體進度。 */
    fun next(p: Float): MergeProgress {
        // 從接近完成掉回接近 0:第一個檔案下載完,開始下載第二個。開頭的小幅抖動(3 → 2)不算。
        val nowSecond = second || (last >= 90f && p < 10f)
        val mapped = if (nowSecond) SPLIT + p * (100f - SPLIT) / 100f else p * SPLIT / 100f
        return MergeProgress(nowSecond, p, maxOf(shown, mapped))
    }

    companion object {
        const val SPLIT = 85f
    }
}
