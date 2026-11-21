package com.jmh273.easyytdllite.service

import android.content.Context
import com.jmh273.easyytdllite.queue.DownloadHistory
import com.jmh273.easyytdllite.queue.MediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 先寫暫存檔再改名,避免寫一半時 process 被結束而留下壞檔。 */
internal fun File.writeAtomically(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        delete()
        tmp.renameTo(this)
    }
}

/**
 * 下載紀錄(design D5):`filesDir/history.json`。第一次載入時,把佇列中已完成的項目併進來,
 * 這樣在這個版本之前下載完成的影片也會被標示。
 */
class HistoryStore private constructor(private val file: File, queueStore: QueueStore) {
    private val _state = MutableStateFlow(
        DownloadHistory.decode(runCatching { file.readText() }.getOrNull())
            .addDone(queueStore.state.value),
    )
    val state: StateFlow<DownloadHistory> = _state.asStateFlow()

    init {
        file.writeAtomically(DownloadHistory.encode(_state.value))
    }

    @Synchronized
    fun add(id: String, kind: MediaKind, uri: String?) {
        val next = _state.value.add(id, kind, uri)
        if (next != _state.value) {
            _state.value = next
            file.writeAtomically(DownloadHistory.encode(next))
        }
    }

    companion object {
        @Volatile private var instance: HistoryStore? = null

        fun get(context: Context): HistoryStore = instance ?: synchronized(this) {
            instance ?: HistoryStore(
                File(context.applicationContext.filesDir, "history.json"),
                QueueStore.get(context),
            ).also { instance = it }
        }
    }
}
