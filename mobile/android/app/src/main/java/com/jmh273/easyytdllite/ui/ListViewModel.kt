package com.jmh273.easyytdllite.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jmh273.easyytdllite.queue.Failure
import com.jmh273.easyytdllite.service.Lister
import com.jmh273.easyytdllite.youtube.ListPage
import com.jmh273.easyytdllite.youtube.ListSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「搜尋」與「網址」分頁的清單狀態(薄殼;split-lite-and-list-sources design D5)。
 * 放在 ViewModel:切分頁、旋轉螢幕時,清單與勾選不會消失,也不必重新請求。
 */
class ListViewModel(app: Application) : AndroidViewModel(app) {
    private val lister = Lister(app)

    /** 一個分頁自己的清單;兩個分頁的請求互不作廢。 */
    class Slot {
        var input by mutableStateOf("")
        var loading by mutableStateOf(false)
            internal set
        var page by mutableStateOf<ListPage?>(null)
            internal set
        var failure by mutableStateOf<Failure?>(null)
            internal set
        var selected by mutableStateOf(setOf<String>())

        /** 每次請求加一;回應回來時編號不是最新的就丟掉。 */
        internal var generation = 0
    }

    val search = Slot()
    val url = Slot()

    /** 開始列出:舊的勾選與結果清掉,前一次還沒回來的結果作廢。 */
    fun load(slot: Slot, source: ListSource) {
        val gen = ++slot.generation
        slot.loading = true
        slot.failure = null
        slot.page = null
        slot.selected = emptySet()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { lister.list(source) }
            if (gen != slot.generation) return@launch
            slot.loading = false
            when (r) {
                is Lister.Result.Ok -> slot.page = r.page
                is Lister.Result.Failed -> slot.failure = r.failure
            }
        }
    }
}
