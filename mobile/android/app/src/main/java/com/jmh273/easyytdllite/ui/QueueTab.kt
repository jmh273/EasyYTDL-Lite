package com.jmh273.easyytdllite.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.queue.Failure
import com.jmh273.easyytdllite.queue.QueueItem
import com.jmh273.easyytdllite.queue.Status
import com.jmh273.easyytdllite.service.AppNotice
import com.jmh273.easyytdllite.service.DownloadService
import com.jmh273.easyytdllite.service.Pipeline
import com.jmh273.easyytdllite.service.QueueStore
import com.jmh273.easyytdllite.ytdlp.FailureClassifier
import kotlinx.coroutines.launch

/**
 * 「佇列」分頁(薄殼,spec download-queue「佇列畫面」):佇列清單、重試、移除、清除已完成。
 * 貼網址集中在「網址」分頁。yt-dlp 的版本與更新、退回在設定頁;這裡只保留「YouTube 拒絕」時的更新提示。
 */
@Composable
fun QueueTab() {
    val context = LocalContext.current
    val store = remember { QueueStore.get(context) }
    val queue by store.state.collectAsStateWithLifecycle()
    val notice by AppNotice.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var ytdlpVersion by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    var logFor by remember { mutableStateOf<QueueItem?>(null) }
    val notificationsOff = android.os.Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        queue.items.isNotEmpty()
    val suggestUpdate = queue.items.any { it.status == Status.FAILED && it.failure != null && FailureClassifier.suggestsUpdate(it.failure) }

    // 版本只在需要提示更新時讀(執行 yt-dlp --version 要一點時間)。
    LaunchedEffect(suggestUpdate, notice) { if (suggestUpdate) ytdlpVersion = YtDlpActions.version(context) }

    logFor?.let { item ->
        val text = remember(item) { Pipeline.readLog(context, item.key) }
        AlertDialog(
            onDismissRequest = { logFor = null },
            title = { Text(stringResource(R.string.queue_log_title)) },
            text = {
                SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                    Text(text.ifEmpty { stringResource(R.string.queue_log_empty) }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { logFor = null }) { Text(stringResource(R.string.queue_close)) } },
        )
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.queue_save_folder), style = MaterialTheme.typography.bodySmall)
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        if (suggestUpdate) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ytdlp_suggest, ytdlpVersion ?: "?"))
                    OutlinedButton(onClick = {
                        updating = true
                        scope.launch {
                            YtDlpActions.update(context)
                            ytdlpVersion = YtDlpActions.version(context)
                            updating = false
                        }
                    }, enabled = !updating) { Text(stringResource(R.string.ytdlp_update)) }
                    if (updating) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        if (notificationsOff) Text(stringResource(R.string.queue_no_notification), style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        for (item in queue.items.asReversed()) {
            QueueRow(
                item,
                onRetry = { store.update { it.retry(item.key) }; DownloadService.start(context) },
                onRemove = { store.update { it.remove(item.key) } },
                onShowLog = { logFor = item },
            )
        }
        if (queue.items.any { it.status == Status.DONE }) {
            OutlinedButton(onClick = { store.update { it.clearDone() } }) { Text(stringResource(R.string.queue_clear_done)) }
        }
    }
}

@Composable
private fun QueueRow(item: QueueItem, onRetry: () -> Unit, onRemove: () -> Unit, onShowLog: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            VideoThumb(item.id, withAvatar = false, avatarUrl = null, modifier = Modifier.padding(end = 12.dp))
            QueueRowBody(item, onRetry, onRemove, onShowLog)
        }
    }
}

@Composable
private fun QueueRowBody(item: QueueItem, onRetry: () -> Unit, onRemove: () -> Unit, onShowLog: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(item.title ?: item.url, style = MaterialTheme.typography.titleSmall)
        Text(formatLabel(item.kind, item.height) + " · " + statusText(item), style = MaterialTheme.typography.bodySmall)
        if (item.status == Status.RUNNING) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.status == Status.FAILED) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.queue_retry)) }
                TextButton(onClick = onShowLog) { Text(stringResource(R.string.queue_show_log)) }
            }
            if (item.status in setOf(Status.WAITING, Status.WAITING_NETWORK, Status.FAILED)) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.queue_remove)) }
            }
        }
    }
}

@Composable
private fun statusText(item: QueueItem): String = when (item.status) {
    Status.WAITING -> stringResource(R.string.status_waiting)
    Status.RUNNING -> stringResource(R.string.status_running)
    Status.WAITING_NETWORK -> stringResource(R.string.status_waiting_network)
    Status.DONE -> stringResource(R.string.status_done)
    Status.FAILED -> stringResource(
        when (item.failure) {
            Failure.NETWORK -> R.string.failure_network
            Failure.YOUTUBE_REJECTED -> R.string.failure_youtube_rejected
            Failure.BOT_CHECK -> R.string.failure_bot_check
            Failure.UNAVAILABLE -> R.string.failure_unavailable
            Failure.OTHER, null -> R.string.failure_other
        },
    )
}
