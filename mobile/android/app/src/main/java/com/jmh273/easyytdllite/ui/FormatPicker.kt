package com.jmh273.easyytdllite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.queue.MediaKind
import com.jmh273.easyytdllite.settings.SettingsStore
import com.jmh273.easyytdllite.video.VideoFormat

/** 目前選的格式與解析度(三個頁籤與分享共用,存在設定裡;add-mp4-download design D3)。 */
data class FormatChoice(val kind: MediaKind, val height: Int)

@Composable
fun rememberFormatChoice(): FormatChoice {
    val context = LocalContext.current
    val settings by remember { SettingsStore.get(context) }.state.collectAsStateWithLifecycle()
    return FormatChoice(settings.kind, settings.height)
}

/** MP3 | MP4;MP4 時下面再一列 1080p | 720p | 480p | 360p。 */
@Composable
fun FormatPicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val choice = rememberFormatChoice()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val kinds = MediaKind.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            kinds.forEachIndexed { i, kind ->
                SegmentedButton(
                    selected = choice.kind == kind,
                    onClick = { store.setChoice(kind, choice.height) },
                    shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                ) { Text(kind.name) }
            }
        }
        if (choice.kind == MediaKind.MP4) {
            val heights = VideoFormat.HEIGHTS
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                heights.forEachIndexed { i, h ->
                    SegmentedButton(
                        selected = choice.height == h,
                        onClick = { store.setChoice(MediaKind.MP4, h) },
                        shape = SegmentedButtonDefaults.itemShape(i, heights.size),
                    ) { Text(stringResource(R.string.format_height, h)) }
                }
            }
        }
    }
}

/** 佇列項目的格式文字:「MP3」或「MP4 720p」。 */
@Composable
fun formatLabel(kind: MediaKind, height: Int?): String = when (kind) {
    MediaKind.MP3 -> "MP3"
    MediaKind.MP4 -> "MP4 " + stringResource(R.string.format_height, VideoFormat.normalize(height))
}
