package com.jmh273.easyytdllite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jmh273.easyytdllite.youtube.Thumbnails

/**
 * 影片縮圖(96×54,16:9)。[withAvatar] 時右下角疊頻道頭像;[avatarUrl] 是 null(舊快取)就疊預設色塊。
 * 載入中、失敗、沒有網址都畫 surfaceVariant 色塊(add-video-thumbnails design D6)。
 */
@Composable
fun VideoThumb(id: String, withAvatar: Boolean, avatarUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier.size(96.dp, 54.dp)) {
        RemoteImage(Thumbnails.video(id), RoundedCornerShape(6.dp), Modifier.size(96.dp, 54.dp))
        if (withAvatar) {
            ChannelAvatar(avatarUrl, 20.dp, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/** 圓形頻道頭像;[url] 是 null 時只畫色塊,不發請求。 */
@Composable
fun ChannelAvatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    RemoteImage(url, CircleShape, modifier.size(size))
}

@Composable
private fun RemoteImage(url: String?, shape: Shape, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.surfaceVariant
    val clipped = modifier.clip(shape).background(color)
    if (url == null) {
        Box(clipped)
        return
    }
    val placeholder = ColorPainter(color)
    AsyncImage(
        model = url,
        contentDescription = null,
        placeholder = placeholder,
        error = placeholder,
        contentScale = ContentScale.Crop,
        modifier = clipped,
    )
}
