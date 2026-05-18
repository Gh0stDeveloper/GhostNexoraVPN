@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.ui.theme.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
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
            // General Section
            SettingsSection(title = "General") {
                SwitchSetting(
                    title = "Reconexión automática",
                    subtitle = "Reconectar automáticamente si se pierde la conexión",
                    checked = state.autoReconnect,
                    onCheckedChange = { viewModel.toggleAutoReconnect() }
                )

                SwitchSetting(
                    title = "Reconectar al iniciar",
                    subtitle = "Iniciar VPN automáticamente al encender el dispositivo",
                    checked = state.reconnectOnBoot,
                    onCheckedChange = { viewModel.toggleReconnectOnBoot() }
                )

                SwitchSetting(
                    title = "Ventana flotante",
                    subtitle = "Mostrar indicador flotante de conexión",
                    checked = state.floatingWindow,
                    onCheckedChange = { viewModel.toggleFloatingWindow() }
                )

                SwitchSetting(
                    title = "Notificaciones",
                    subtitle = "Mostrar notificaciones persistentes",
                    checked = state.notifications,
                    onCheckedChange = { viewModel.toggleNotifications() }
                )
            }

            // Logs Section
            SettingsSection(title = "Registros") {
                ListSetting(
                    title = "Máximo de entradas",
                    value = "${state.logsMaxEntries}",
                    onClick = { /* TODO: Implementar selector */ }
                )

                GhostButton(
                    text = "Limpiar Registros",
                    onClick = { showClearDialog = true },
                    containerColor = Color.Red.copy(0.8f),
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Update Section
            SettingsSection(title = "Actualizaciones") {
                InfoRow("Verificación automática", "Al abrir la app")
                InfoRow("Instalación", "Desde GitHub Releases")
            }

            // About Section
            SettingsSection(title = "Acerca de") {
                InfoRow("Versión", "1.0.0")
                InfoRow("Desarrollado por", "GhostNexora")
            }

            Spacer(modifier = Modifier.height(Dimens.Space3XL))
        }
    }

    // Clear logs confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Limpiar Registros") },
            text = { Text("¿Estás seguro de que deseas eliminar todos los registros?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLogs()
                        showClearDialog = false
                    }
                ) {
                    Text("Limpiar", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// ==================== COMPONENTES REUTILIZABLES ====================

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = NeonCyan
        )
        GhostCard(
            backgroundColor = SurfaceVariant,
            borderColor = BorderNormal
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonCyan,
                checkedTrackColor = NeonCyan.copy(0.5f)
            )
        )
    }
}

@Composable
private fun ListSetting(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = NeonCyan
        )
        Icon(
            Icons.Filled.ChevronRight,
            null,
            tint = TextTertiary,
            modifier = Modifier.padding(start = Dimens.SpaceSM)
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}
