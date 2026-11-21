package com.jmh273.easyytdllite.audio

/** 實際套用的增益。[limited] 為真表示受峰值限制,小於 track gain。 */
data class Gain(val appliedDb: Double, val limited: Boolean)

/** 依 ReplayGain 的 track gain、峰值與目標音量決定增益(design D5;add-gain-numbering-and-history-v2 D1)。純邏輯。 */
object GainPlan {
    const val MAX_PEAK = 0.98

    /** ReplayGain 的參考音量(mp3gain 預設);track gain 就是相對它算的。 */
    const val REFERENCE_DB = 89.0

    fun plan(trackGainDb: Double, trackPeak: Double?, targetDb: Double = REFERENCE_DB): Gain {
        val desired = trackGainDb + (targetDb - REFERENCE_DB)
        if (desired <= 0) return Gain(desired, false)
        if (trackPeak == null || trackPeak <= 0) return Gain(0.0, true)
        val ceiling = 20 * kotlin.math.log10(MAX_PEAK / trackPeak)
        return if (desired <= ceiling) Gain(desired, false) else Gain(ceiling, true)
    }
}
