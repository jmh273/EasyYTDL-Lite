package com.jmh273.easyytdllite.settings

import android.content.Context
import com.jmh273.easyytdllite.queue.MediaKind
import com.jmh273.easyytdllite.video.VideoFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 下載設定(design D3;目標音量見 add-gain-numbering-and-history-v2 D7):`filesDir/settings.json`。服務每次開始新的一支前讀 [state],改設定從下一支生效。 */
class SettingsStore private constructor(private val file: File) {
    private val _state = MutableStateFlow(DownloadSettings.decode(runCatching { file.readText() }.getOrNull()))
    val state: StateFlow<DownloadSettings> = _state.asStateFlow()

    fun setConcurrency(n: Int) {
        if (n in DownloadSettings.RANGE) save(_state.value.copy(concurrency = n))
    }

    fun setTargetDb(db: Int) {
        if (db in DownloadSettings.TARGET_RANGE) save(_state.value.copy(targetDb = db))
    }

    /** 加入佇列用的格式與解析度(add-mp4-download design D3)。 */
    fun setChoice(kind: MediaKind, height: Int) {
        if (height in VideoFormat.HEIGHTS) save(_state.value.copy(kind = kind, height = height))
    }

    @Synchronized
    private fun save(next: DownloadSettings) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(DownloadSettings.encode(next))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        _state.value = next
    }

    companion object {
        @Volatile private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(File(context.applicationContext.filesDir, "settings.json")).also { instance = it }
        }
    }
}
