package com.jmh273.easyytdllite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.service.AppNotice
import com.jmh273.easyytdllite.service.UpdateChecker
import com.jmh273.easyytdllite.update.ManualResult
import com.jmh273.easyytdllite.update.UpdateCheck
import com.jmh273.easyytdllite.settings.DownloadSettings
import com.jmh273.easyytdllite.settings.SettingsStore
import kotlinx.coroutines.launch

/**
 * 設定頁(薄殼,spec app-settings):yt-dlp、下載、關於(含檢查新版與開放原始碼授權)。yt-dlp 的操作在 [YtDlpActions]。
 */
@Composable
fun SettingsScreen(versionName: String, onOpenProbe: (() -> Unit)?) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore.get(context) }
    val settings by settingsStore.state.collectAsStateWithLifecycle()
    val notice by AppNotice.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var ytdlpVersion by remember { mutableStateOf<String?>(null) }
    var previous by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun refreshVersion() {
        ytdlpVersion = YtDlpActions.version(context)
        previous = YtDlpActions.previous(context)
    }
    LaunchedEffect(Unit) { refreshVersion() }
    LaunchedEffect(notice) { if (notice != null) refreshVersion() }

    fun ytdlpAction(action: suspend () -> Unit) {
        busy = true
        scope.launch {
            action()
            refreshVersion()
            busy = false
        }
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // yt-dlp
        Text(stringResource(R.string.settings_ytdlp), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.ytdlp_version, ytdlpVersion ?: stringResource(R.string.ytdlp_version_unknown)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { ytdlpAction { YtDlpActions.update(context) } }, enabled = !busy) {
                Text(stringResource(R.string.ytdlp_update))
            }
            previous?.let { prev ->
                OutlinedButton(onClick = { ytdlpAction { YtDlpActions.revert(context) } }, enabled = !busy) {
                    Text(stringResource(R.string.ytdlp_revert, prev))
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        HorizontalDivider()

        // 下載
        Text(stringResource(R.string.settings_download), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_concurrency), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (n in DownloadSettings.RANGE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.selectable(selected = settings.concurrency == n, onClick = { settingsStore.setConcurrency(n) }).padding(end = 16.dp),
                ) {
                    RadioButton(selected = settings.concurrency == n, onClick = { settingsStore.setConcurrency(n) })
                    Text(stringResource(R.string.settings_concurrency_n, n))
                }
            }
        }
        Text(stringResource(R.string.settings_concurrency_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.settings_target_db), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { settingsStore.setTargetDb(settings.targetDb - 1) },
                enabled = settings.targetDb > DownloadSettings.TARGET_RANGE.first,
            ) { Text(stringResource(R.string.settings_target_db_down)) }
            Text(stringResource(R.string.settings_target_db_value, settings.targetDb), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { settingsStore.setTargetDb(settings.targetDb + 1) },
                enabled = settings.targetDb < DownloadSettings.TARGET_RANGE.last,
            ) { Text(stringResource(R.string.settings_target_db_up)) }
        }
        Text(
            stringResource(R.string.settings_target_db_hint, DownloadSettings.TARGET_RANGE.first, DownloadSettings.TARGET_RANGE.last, DownloadSettings.DEFAULT_TARGET_DB),
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()

        // 關於
        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.main_version, versionName), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.main_disclaimer), style = MaterialTheme.typography.bodySmall)
        UpdateCheckRow(versionName)
        var licensesOpen by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { licensesOpen = true }) { Text(stringResource(R.string.licenses_open)) }
        if (licensesOpen) LicensesDialog { licensesOpen = false }
        if (onOpenProbe != null) {
            OutlinedButton(onClick = onOpenProbe) { Text(stringResource(R.string.main_open_probe)) }
        }
    }
}

/** 手動「檢查新版」與結果(spec app-update「手動檢查新版」)。 */
@Composable
private fun UpdateCheckRow(versionName: String) {
    val context = LocalContext.current
    val checker = remember { UpdateChecker.get(context) }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ManualResult?>(null) }
    OutlinedButton(
        onClick = {
            checking = true
            scope.launch {
                result = UpdateCheck.manual(versionName, checker.checkNow())
                checking = false
            }
        },
        enabled = !checking,
    ) { Text(stringResource(if (checking) R.string.update_checking else R.string.update_check)) }
    when (val r = result) {
        null -> {}
        ManualResult.UpToDate -> Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodyMedium)
        ManualResult.Failed -> Text(stringResource(R.string.update_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        is ManualResult.NewVersion -> Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.update_available, r.release.version.toString()), style = MaterialTheme.typography.bodyMedium)
                UpdateButtons(r.release)
            }
            UpdateStatus(r.release)
        }
    }
}
