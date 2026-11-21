package com.jmh273.easyytdllite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jmh273.easyytdllite.R
import com.jmh273.easyytdllite.about.OpenSourceNotices

/** 「開放原始碼授權」對話框(spec app-settings)。名單在 [OpenSourceNotices]。 */
@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.licenses_close)) } },
        title = { Text(stringResource(R.string.licenses_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.licenses_app, OpenSourceNotices.APP.license, OpenSourceNotices.SOURCE_URL))
                Text(stringResource(R.string.licenses_intro))
                for (n in OpenSourceNotices.ENTRIES) {
                    Column {
                        Text("${n.name} — ${n.license}", style = MaterialTheme.typography.bodyMedium)
                        Text(n.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    )
}
