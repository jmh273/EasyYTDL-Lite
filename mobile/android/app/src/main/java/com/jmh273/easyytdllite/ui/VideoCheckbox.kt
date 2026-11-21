package com.jmh273.easyytdllite.ui

import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * 影片清單勾選框的顏色(spec subscription-browse「勾選加入佇列」;design D1、D2)。
 * 不能勾選的影片(已在佇列、已下載)畫成填滿的灰色方塊:預設的停用外觀只是淡色空框,和「還沒勾」分不出來。
 * 停用的列一定是未勾選,所以只改「停用 + 未勾選」的填色與框線;其他狀態沿用預設。
 */
@Composable
fun videoCheckboxColors(): CheckboxColors {
    val gray = MaterialTheme.colorScheme.outline
    return CheckboxDefaults.colors().copy(
        disabledUncheckedBoxColor = gray,
        disabledUncheckedBorderColor = gray,
    )
}
