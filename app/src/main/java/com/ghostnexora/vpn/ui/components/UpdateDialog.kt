package com.ghostnexora.vpn.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SecurityUpdateGood
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ghostnexora.vpn.update.UpdateUiState

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onUpdateNow: () -> Unit
) {
    val busy = state.downloading || state.installing
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.SecurityUpdateGood, contentDescription = null) },
        title = { Text("Verified update available") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Installed", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${state.currentVersion} (${state.currentVersionCode})",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column {
                        Text("Available", style = MaterialTheme.typography.labelMedium)
                        Text(
                            buildString {
                                append(state.latestVersion.ifBlank { "New release" })
                                if (state.latestVersionCode > 0) append(" (${state.latestVersionCode})")
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    "The APK is downloaded from GitHub Releases, checked against release metadata, and verified as a newer build of this package before Android opens the installer."
                )
                if (state.needsInstallPermission) {
                    Text(
                        "Android requires one-time permission to install updates from this source.",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (state.downloading) {
                    LinearProgressIndicator(
                        progress = { state.downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Downloading: ${state.downloadProgress}%")
                }
                if (state.releaseNotes.isNotBlank()) {
                    Text("Release notes", fontWeight = FontWeight.SemiBold)
                    Text(state.releaseNotes, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdateNow, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        state.needsInstallPermission -> "Allow installation"
                        state.downloading -> "Downloading…"
                        state.installing -> "Opening installer…"
                        else -> "Download and install"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Remind me for the next release")
            }
        }
    )
}
