package com.jmh273.easyytdllite.service

import android.content.Context
import com.jmh273.easyytdllite.queue.Queue
import com.jmh273.easyytdllite.queue.QueueCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 佇列的唯一持有者(design D3):記憶體裡的狀態以 [state] 提供給畫面與服務,每次變更都原子寫入 `filesDir/queue.json`。
 * process 第一次載入時,把「處理中」改回「等待中」(上一個 process 中斷了它)。
 */
class QueueStore private constructor(private val file: File) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Queue> = _state.asStateFlow()

    init {
        write(_state.value)
    }

    @Synchronized
    fun update(change: (Queue) -> Queue): Queue {
        val next = change(_state.value)
        if (next != _state.value) {
            _state.value = next
            write(next)
        }
        return next
    }

    private fun load(): Queue {
        val text = runCatching { file.readText() }.getOrNull()
        return QueueCodec.decode(text).recoverAfterRestart()
    }

    /** 先寫暫存檔再改名,避免寫一半時 process 被結束而留下壞檔。 */
    private fun write(queue: Queue) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(QueueCodec.encode(queue))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        @Volatile private var instance: QueueStore? = null

        fun get(context: Context): QueueStore = instance ?: synchronized(this) {
            instance ?: QueueStore(File(context.applicationContext.filesDir, "queue.json")).also { instance = it }
        }
    }
}

/** 給畫面顯示一次的提示(例如 yt-dlp 更新結果、系統暫停下載)。 */
object AppNotice {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun post(text: String) {
        _message.value = text
    }

    fun clear() {
        _message.value = null
    }
}
