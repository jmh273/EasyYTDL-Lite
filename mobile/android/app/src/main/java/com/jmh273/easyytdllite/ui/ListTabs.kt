package com.jmh273.easyytdllite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.youtube.ListSource
import com.jmh273.easyytdllite.youtube.Video

/** 「搜尋」分頁(spec video-listing「關鍵字搜尋」)。 */
@Composable
fun SearchTab(vm: ListViewModel, onEnqueue: (List<Video>) -> Int) {
    val slot = vm.search
    val focus = LocalFocusManager.current
    fun submit() {
        if (slot.input.isBlank() || slot.loading) return
        focus.clearFocus()
        vm.load(slot, ListSource.Search(slot.input))
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InputRow(
            value = slot.input,
            onValue = { slot.input = it },
            label = R.string.list_search_label,
            button = R.string.list_search,
            enabled = slot.input.isNotBlank() && !slot.loading,
            ime = ImeAction.Search,
            onSubmit = ::submit,
        )
        VideoListArea(slot, onEnqueue)
    }
}

/**
 * 「網址」分頁(spec video-listing「貼網址列出影片」):單支影片直接交給 [onAddVideo];
 * 播放清單與頻道列出影片;不認得的網址由 [onUnsupported] 提示,不發請求。
 */
@Composable
fun UrlTab(vm: ListViewModel, onAddVideo: (String) -> Boolean, onUnsupported: () -> Unit, onEnqueue: (List<Video>) -> Int) {
    val slot = vm.url
    val focus = LocalFocusManager.current
    fun submit() {
        if (slot.input.isBlank() || slot.loading) return
        focus.clearFocus()
        when (val source = ListSource.classify(slot.input)) {
            is ListSource.SingleVideo -> if (onAddVideo(slot.input)) slot.input = ""
            is ListSource.Playlist, is ListSource.Channel -> vm.load(slot, source)
            else -> onUnsupported()
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InputRow(
            value = slot.input,
            onValue = { slot.input = it },
            label = R.string.list_url_label,
            button = R.string.list_url_go,
            enabled = slot.input.isNotBlank() && !slot.loading,
            ime = ImeAction.Go,
            onSubmit = ::submit,
        )
        VideoListArea(slot, onEnqueue)
    }
}

@Composable
private fun InputRow(value: String, onValue: (String) -> Unit, label: Int, button: Int, enabled: Boolean, ime: ImeAction, onSubmit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            label = { Text(stringResource(label)) },
            keyboardOptions = KeyboardOptions(imeAction = ime),
            keyboardActions = KeyboardActions(onAny = { onSubmit() }),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onSubmit, enabled = enabled) { Text(stringResource(button)) }
    }
}
