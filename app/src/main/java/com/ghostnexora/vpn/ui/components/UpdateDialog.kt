
package com.ghostnexora.vpn.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.update.UpdateUiState

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onUpdateNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actualización disponible") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Hay una versión más reciente de Ghost Nexora VPN.",
                    color = TextSecondary
                )

                Text(
                    "La actualización se instala encima de la versión actual y conserva perfiles, logs y ajustes mientras la firma y el applicationId sean los mismos.",
                    color = TextSecondary
                )

                if (state.currentVersion.isNotBlank() || state.latestVersion.isNotBlank()) {
                    Text("Actual: ${state.currentVersion}  →  Nueva: ${state.latestVersion}")
                }

                if (state.expectedSha256.isNotBlank()) {
                    Text("Checksum SHA-256: verificación activa", color = NeonAmber)
                }

                if (state.releaseNotes.isNotBlank()) {
                    Text(state.releaseNotes)
                }

                if (state.message != null) {
                    Text(state.message!!)
                }

                if (state.error != null) {
                    Text(state.error!!)
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdateNow, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        state.downloading -> "Descargando…"
                        state.installing -> "Abriendo instalador…"
                        else -> "Descargar e instalar"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Más tarde")
            }
        }
    )
}
