package com.jmh273.easyytdllite.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.queue.Failure
import com.jmh273.easyytdllite.service.QueueStore
import com.jmh273.easyytdllite.youtube.FlatList
import com.jmh273.easyytdllite.youtube.Video
import com.jmh273.easyytdllite.youtube.VideoSelection
import com.jmh273.easyytdllite.youtube.VideoState

/**
 * 「搜尋」與「網址」分頁共用的清單區(spec video-listing「勾選加入佇列」「清單保留與讀取失敗」):
 * 讀取中、失敗原因、全選、「加入佇列(n)」與可勾選的影片列。
 * [onEnqueue] 把影片加入佇列並開始下載,回傳實際加入的支數。
 */
@Composable
fun VideoListArea(slot: ListViewModel.Slot, onEnqueue: (List<Video>) -> Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val queue by remember { QueueStore.get(context) }.state.collectAsStateWithLifecycle()
    val choice = rememberFormatChoice()
    val downloaded = rememberDownloadedIds(choice.kind)
    val selection = VideoSelection(inQueue = queue.inQueueIds(choice.kind), downloaded = downloaded)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (slot.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        slot.failure?.let { Text(failureText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        val page = slot.page ?: return@Column
        if (page.title.isNotBlank()) Text(page.title, style = MaterialTheme.typography.titleSmall)
        val videos = page.videos
        val count = selection.toAdd(videos, slot.selected).size
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { slot.selected = selection.selectAll(videos) }, enabled = videos.isNotEmpty()) {
                Text(stringResource(R.string.list_select_all))
            }
            Button(
                onClick = {
                    val n = onEnqueue(selection.toAdd(videos, slot.selected))
                    slot.selected = emptySet()
                    Toast.makeText(context, context.getString(R.string.list_added, n), Toast.LENGTH_SHORT).show()
                },
                enabled = count > 0,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.list_add_selected, count)) }
        }
        if (videos.isEmpty()) Text(stringResource(R.string.list_no_videos), style = MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.fillMaxWidth()) {
            items(videos, key = { it.id }) { v ->
                VideoRow(v, selection.state(v.id), v.id in slot.selected) { slot.selected = selection.toggle(slot.selected, v.id) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun VideoRow(v: Video, state: VideoState, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = state == VideoState.SELECTABLE, onClick = onToggle),
    ) {
        Checkbox(
            checked = checked && state == VideoState.SELECTABLE,
            onCheckedChange = { onToggle() },
            enabled = state == VideoState.SELECTABLE,
            colors = videoCheckboxColors(),
        )
        VideoThumb(v.id, withAvatar = false, avatarUrl = null, modifier = Modifier.padding(end = 8.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(v.title, style = MaterialTheme.typography.bodyMedium)
            val parts = listOfNotNull(
                FlatList.durationText(v.durationSeconds),
                when (state) {
                    VideoState.SELECTABLE -> null
                    VideoState.IN_QUEUE -> stringResource(R.string.list_in_queue)
                    VideoState.DOWNLOADED -> stringResource(R.string.list_downloaded)
                },
            )
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun failureText(f: Failure): String = stringResource(
    when (f) {
        Failure.NETWORK -> R.string.list_failed_network
        Failure.YOUTUBE_REJECTED, Failure.BOT_CHECK -> R.string.list_failed_rejected
        Failure.UNAVAILABLE -> R.string.list_failed_unavailable
        Failure.OTHER -> R.string.list_failed_other
    },
)
