package com.whatsgo.app.ui.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UpdateDialogs(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        UpdateState.Idle -> Unit

        UpdateState.Checking -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Checking for updates") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Please wait…")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(viewModel::dismiss) { Text("Cancel") } }
        )

        is UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("You're up to date") },
            text = { Text("WhatsGo ${s.version} is the latest version.") },
            confirmButton = { TextButton(viewModel::dismiss) { Text("OK") } }
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Update") },
            text = { Text(s.message) },
            confirmButton = { TextButton(viewModel::dismiss) { Text("OK") } }
        )

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Update available") },
            text = {
                Column {
                    val mb = s.info.sizeBytes / (1024f * 1024f)
                    Text(
                        buildString {
                            append("Version ${s.info.version}")
                            if (s.info.sizeBytes > 0) append("  •  ${"%.1f".format(mb)} MB")
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("What's new", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.info.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = { Button(viewModel::update) { Text("Update") } },
            dismissButton = { TextButton(viewModel::dismiss) { Text("Cancel") } }
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = viewModel::hide,
            title = { Text("Downloading update") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You can close this. The download continues in the notification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(viewModel::hide) { Text("Hide") } },
            dismissButton = { TextButton(viewModel::cancelDownload) { Text("Cancel download") } }
        )

        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Update ready") },
            text = { Text("WhatsGo ${s.version} has been downloaded.") },
            confirmButton = { Button(viewModel::install) { Text("Install") } },
            dismissButton = { TextButton(viewModel::dismiss) { Text("Later") } }
        )
    }
}