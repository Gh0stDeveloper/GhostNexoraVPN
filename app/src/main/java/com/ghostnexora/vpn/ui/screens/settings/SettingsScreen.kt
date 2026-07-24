@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.BuildConfig
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderNormal
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostButton
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.SurfaceVariant
import com.ghostnexora.vpn.ui.theme.TextOnAccent
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showClearLogs by remember { mutableStateOf(false) }
    var showLogsLimit by remember { mutableStateOf(false) }
    var showClearHosts by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXL)
        ) {
            SettingsSection("Protección") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
                ) {
                    Icon(Icons.Filled.Security, null, tint = NeonGreen)
                    Column(Modifier.weight(1f)) {
                        Text("Almacenamiento seguro", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Credenciales de perfiles cifradas con Android Keystore y AES-GCM", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                SwitchSetting(
                    "Kill Switch",
                    "Mantiene el TUN activo y bloquea salida directa si el transporte se cae",
                    state.killSwitch
                ) { viewModel.toggleKillSwitch() }
                SwitchSetting(
                    "Reconexión automática",
                    "Recupera el transporte con backoff 1s, 2s, 5s, 10s y 30s",
                    state.autoReconnect
                ) { viewModel.toggleAutoReconnect() }
                InfoRow("Servidores SSH confiables", state.knownHostCount.toString())
                GhostButton(
                    text = "Restablecer huellas SSH",
                    onClick = { showClearHosts = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
                    containerColor = Color.Red.copy(alpha = 0.75f),
                    contentColor = Color.White
                )
            }

            SettingsSection("General") {
                SwitchSetting(
                    "Reconectar al iniciar",
                    "Restaurar la VPN cuando Android reinicia el servicio",
                    state.reconnectOnBoot
                ) { viewModel.toggleReconnectOnBoot() }
                SwitchSetting(
                    "Ventana flotante",
                    "Mostrar indicador flotante de conexión",
                    state.floatingWindow
                ) { viewModel.toggleFloatingWindow() }
                SwitchSetting(
                    "Notificaciones",
                    "Mostrar estado persistente de la VPN",
                    state.notifications
                ) { viewModel.toggleNotifications() }
            }

            SettingsSection("Registros") {
                ListSetting("Máximo de entradas", state.logsMaxEntries.toString()) { showLogsLimit = true }
                Text(
                    "Las entradas se saneean antes de almacenarse o copiarse para ocultar contraseñas, tokens y cabeceras de autorización.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM)
                )
                GhostButton(
                    text = "Limpiar registros",
                    onClick = { showClearLogs = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
                    containerColor = Color.Red.copy(alpha = 0.75f),
                    contentColor = Color.White
                )
            }

            SettingsSection("Configuraciones") {
                InfoRow("Exportación", "GNX2 cifrado")
                InfoRow("Cifrado", "AES-256-GCM")
                InfoRow("Derivación", "PBKDF2-SHA256")
                Text(
                    "Los archivos nuevos requieren contraseña. JSON sin cifrar se conserva únicamente para importar configuraciones antiguas.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM)
                )
            }

            SettingsSection("Actualizaciones") {
                GhostButton(
                    text = "Buscar actualizaciones",
                    onClick = onCheckUpdates,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
                    containerColor = NeonCyan,
                    contentColor = TextOnAccent
                )
            }

            SettingsSection("Acerca de") {
                InfoRow("Versión", BuildConfig.VERSION_NAME)
                InfoRow("Desarrollado por", "Ghost Developer")
            }
            Spacer(Modifier.height(Dimens.Space3XL))
        }
    }

    if (showLogsLimit) {
        AlertDialog(
            onDismissRequest = { showLogsLimit = false },
            title = { Text("Máximo de entradas") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(250, 500, 1000).forEach { value ->
                        AssistChip(
                            onClick = {
                                viewModel.setLogsMaxEntries(value)
                                showLogsLimit = false
                            },
                            label = { Text(value.toString()) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setLogsMaxEntries(2000)
                    showLogsLimit = false
                }) { Text("2000") }
            },
            dismissButton = { TextButton(onClick = { showLogsLimit = false }) { Text("Cerrar") } }
        )
    }

    if (showClearLogs) {
        AlertDialog(
            onDismissRequest = { showClearLogs = false },
            title = { Text("Limpiar registros") },
            text = { Text("Se eliminará el historial local de eventos.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLogs()
                    showClearLogs = false
                }) { Text("Limpiar", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showClearLogs = false }) { Text("Cancelar") } }
        )
    }

    if (showClearHosts) {
        AlertDialog(
            onDismissRequest = { showClearHosts = false },
            icon = { Icon(Icons.Filled.DeleteSweep, null) },
            title = { Text("Restablecer confianza SSH") },
            text = { Text("Se eliminarán las huellas conocidas. La identidad de cada servidor tendrá que aceptarse y guardarse de nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearKnownHosts()
                    showClearHosts = false
                }) { Text("Restablecer", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showClearHosts = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
            color = NeonCyan
        )
        GhostCard(backgroundColor = SurfaceVariant, borderColor = BorderNormal) {
            Column { content() }
        }
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonCyan.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun ListSetting(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
        Icon(Icons.Filled.ChevronRight, null, tint = TextTertiary)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}
