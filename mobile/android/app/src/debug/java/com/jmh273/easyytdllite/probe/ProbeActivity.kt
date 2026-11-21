package com.jmh273.easyytdllite.probe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.ui.EasyYtdlTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** debug 版限定的下載探測畫面(薄殼:動作都交給 [ProbeRunner])。 */
class ProbeActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readShare(intent)
        val runner = ProbeRunner(this)
        setContent {
            EasyYtdlTheme {
                Surface(Modifier.fillMaxSize()) { ProbeScreen(runner, sharedUrl) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShare(intent)
    }

    // 分享進來只填網址,不自動下載(請求額度有限)。
    private fun readShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            ProbeCommands.extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let { sharedUrl = it }
        }
    }
}

@Composable
private fun ProbeScreen(runner: ProbeRunner, sharedUrl: String) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(sharedUrl) }
    var mode by remember { mutableStateOf(JsMode.QUICKJS) }
    var busy by remember { mutableStateOf(true) }
    var version by remember { mutableStateOf("…") }
    var output by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    val initFailed = stringResource(R.string.probe_init_failed, "%s")
    val errorFmt = stringResource(R.string.probe_error, "%s")

    LaunchedEffect(sharedUrl) { if (sharedUrl.isNotEmpty()) url = sharedUrl }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                runner.init()
                version = runner.ytdlpVersion()
            } catch (e: Exception) {
                output = initFailed.format(e.toString())
            }
        }
        busy = false
    }

    fun run(action: () -> String) {
        busy = true
        scope.launch {
            output = withContext(Dispatchers.IO) {
                try {
                    action()
                } catch (e: Exception) {
                    errorFmt.format(e.toString())
                }
            }
            version = withContext(Dispatchers.IO) { runCatching { runner.ytdlpVersion() }.getOrElse { it.toString() } }
            busy = false
        }
    }

    val modeLabel = stringResource(if (mode == JsMode.QUICKJS) R.string.probe_mode_quickjs else R.string.probe_mode_control)
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.probe_download_confirm_title)) },
            text = { Text(stringResource(R.string.probe_download_confirm_body, modeLabel, url)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    val target = url.trim()
                    val chosen = mode
                    run { runner.download(target, chosen) }
                }) { Text(stringResource(R.string.probe_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.probe_cancel)) } },
        )
    }

    Column(
        Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.probe_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.probe_ytdlp_version, version))
        OutlinedTextField(
            value = url, onValueChange = { url = it }, singleLine = true,
            label = { Text(stringResource(R.string.probe_url_label)) }, modifier = Modifier.fillMaxWidth(),
        )
        for (m in JsMode.entries) {
            Row(
                Modifier.fillMaxWidth().selectable(selected = mode == m, onClick = { mode = m }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == m, onClick = { mode = m })
                Text(stringResource(if (m == JsMode.QUICKJS) R.string.probe_mode_quickjs else R.string.probe_mode_control))
            }
        }
        Button(onClick = { confirming = true }, enabled = !busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.probe_download))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { run { runner.checkFfmpeg() } }, enabled = !busy) { Text(stringResource(R.string.probe_check_ffmpeg)) }
            OutlinedButton(onClick = { run { runner.ytdlpVersion() } }, enabled = !busy) { Text(stringResource(R.string.probe_read_version)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { run { runner.update() } }, enabled = !busy) { Text(stringResource(R.string.probe_update)) }
            OutlinedButton(onClick = { run { runner.revertToBundled() } }, enabled = !busy) { Text(stringResource(R.string.probe_revert)) }
        }
        if (busy) Text(stringResource(R.string.probe_running))
        SelectionContainer {
            Text(output, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
