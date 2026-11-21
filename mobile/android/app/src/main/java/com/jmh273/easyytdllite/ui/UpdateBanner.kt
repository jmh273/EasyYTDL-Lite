package com.jmh273.easyytdllite.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jmh273.easyytdllite.service.UpdateChecker
import com.jmh273.easyytdllite.update.UpdateCheck

/** 主畫面分頁列下方的新版提示(spec app-update「新版提示」)。沒有要提示的版本時不佔位置。 */
@Composable
fun UpdateBanner(versionName: String) {
    val context = LocalContext.current
    val checker = remember { UpdateChecker.get(context) }
    val latest by checker.latest.collectAsStateWithLifecycle()
    val skipped by checker.skipped.collectAsStateWithLifecycle()
    val show = UpdateCheck.banner(versionName, latest, skipped) ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
        ) {
            Text(stringResource(R.string.update_available, show.version.toString()), modifier = Modifier.weight(1f))
            TextButton(onClick = { openReleasePage(context, show.url) }) { Text(stringResource(R.string.update_open)) }
            TextButton(onClick = { checker.skip(show.version) }) { Text(stringResource(R.string.update_skip)) }
        }
    }
}

/** 用瀏覽器開 Release 頁;App 不自己下載安裝 APK。 */
fun openReleasePage(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
