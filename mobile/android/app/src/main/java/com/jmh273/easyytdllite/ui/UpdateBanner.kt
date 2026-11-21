package com.jmh273.easyytdllite.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.service.UpdateChecker
import com.jmh273.easyytdllite.service.UpdateInstaller
import com.jmh273.easyytdllite.update.LatestRelease
import com.jmh273.easyytdllite.update.UpdateCheck
import kotlinx.coroutines.launch

/** 主畫面分頁列下方的新版提示(spec app-update「新版提示」)。沒有要提示的版本時不佔位置。 */
@Composable
fun UpdateBanner(versionName: String) {
    val context = LocalContext.current
    val checker = remember { UpdateChecker.get(context) }
    val latest by checker.latest.collectAsStateWithLifecycle()
    val skipped by checker.skipped.collectAsStateWithLifecycle()
    val show = UpdateCheck.banner(versionName, latest, skipped) ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.update_available, show.version.toString()), modifier = Modifier.weight(1f))
                UpdateButtons(show)
                TextButton(onClick = { checker.skip(show.version) }) { Text(stringResource(R.string.update_skip)) }
            }
            UpdateStatus(show)
        }
    }
}

/**
 * 「更新」(有可用 APK 時,App 內下載並交給系統安裝)與「前往下載」(沒有 APK、或更新失敗時的退路)。
 * spec app-update「App 內更新」。
 */
@Composable
fun UpdateButtons(release: LatestRelease) {
    val context = LocalContext.current
    val installer = remember { UpdateInstaller.get(context) }
    val state by installer.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val apk = release.apk
    if (apk == null || state is UpdateInstaller.State.Failed) {
        TextButton(onClick = { openReleasePage(context, release.url) }) { Text(stringResource(R.string.update_open)) }
    }
    if (apk != null) {
        TextButton(
            onClick = { scope.launch { installer.update(apk) } },
            enabled = state !is UpdateInstaller.State.Downloading,
        ) { Text(stringResource(if (state is UpdateInstaller.State.Failed) R.string.update_retry else R.string.update_install)) }
    }
}

/** 下載進度、等待允許安裝、失敗的說明列。閒置時不佔位置。 */
@Composable
fun UpdateStatus(release: LatestRelease) {
    if (release.apk == null) return
    val context = LocalContext.current
    val installer = remember { UpdateInstaller.get(context) }
    val state by installer.state.collectAsStateWithLifecycle()
    when (val s = state) {
        UpdateInstaller.State.Idle -> {}
        is UpdateInstaller.State.Downloading -> Column(Modifier.padding(bottom = 8.dp)) {
            Text(stringResource(R.string.update_downloading, s.percent), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        UpdateInstaller.State.NeedPermission ->
            Text(stringResource(R.string.update_need_permission), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        UpdateInstaller.State.Failed ->
            Text(
                stringResource(R.string.update_install_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
    }
}

/** 用瀏覽器開 Release 頁(沒有可用 APK、或 App 內更新失敗時的退路)。 */
fun openReleasePage(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
