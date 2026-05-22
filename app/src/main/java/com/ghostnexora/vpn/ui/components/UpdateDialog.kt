
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
                Text("Hay una versión más reciente.")
                Text("La instalación se realiza encima de la app actual y conserva perfiles, logs y ajustes mientras la firma del APK sea la misma.")
                if (state.latestVersion.isNotBlank()) {
                    Text("Versión: ${state.latestVersion}")
                }
                if (state.releaseNotes.isNotBlank()) {
                    Text(state.releaseNotes)
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdateNow, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.downloading || state.installing) "Actualizando…" else "Descargar e instalar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Más tarde")
            }
        }
    )
}
