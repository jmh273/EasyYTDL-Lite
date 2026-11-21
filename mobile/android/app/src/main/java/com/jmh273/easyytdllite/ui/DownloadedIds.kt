package com.jmh273.easyytdllite.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmh273.easyytdllite.queue.MediaKind
import com.jmh273.easyytdllite.service.HistoryStore
import com.jmh273.easyytdllite.service.MediaPresence

/**
 * 「已下載」的影片 id(spec subscription-browse「勾選加入佇列」;design D5):有 [kind] 的紀錄而且檔案還在。
 * 回到畫面(ON_RESUME,使用者可能剛在別的 App 刪了檔案)與下載紀錄變動時重新查 MediaStore。
 * 還沒查到或查詢失敗時沿用上一次的結果;一次都沒查到時,把有檔案位置的紀錄也當成已下載(寧可暫時不能勾,不要整排誤放行)。
 */
@Composable
fun rememberDownloadedIds(kind: MediaKind): Set<String> {
    val context = LocalContext.current
    val history by remember { HistoryStore.get(context) }.state.collectAsStateWithLifecycle()
    var existing by remember { mutableStateOf<Set<String>?>(null) }
    var resumes by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    LaunchedEffect(history, resumes) {
        MediaPresence.existingUris(context)?.let { existing = it }
    }

    val known = existing ?: history.entries.mapNotNullTo(HashSet()) { it.uri }
    return remember(history, known, kind) { history.downloadedIds(kind, known) }
}
