@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ghostnexora.vpn.ui.screens.settings.SettingsViewModel
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.NeonRed
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.util.PermissionHelper
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun FirstLaunchPermissionsScreen(
    onComplete: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var refreshTick by remember { mutableIntStateOf(0) }
    var showFinishDialog by remember { mutableStateOf(false) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshTick++ }

    LaunchedEffect(refreshTick) {
        viewModel.refreshPermissions()
    }

    val permissions = state.permissionStatus
    val criticalGranted = permissions.vpn && permissions.overlay && permissions.notification && permissions.battery

    Scaffold(
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXL)
        ) {
            Spacer(modifier = Modifier.height(Dimens.SpaceSM))

            GhostCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Configuración inicial",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "La app necesita permisos para VPN, ventanas flotantes, notificaciones y control de actualizaciones.",
                        color = TextSecondary
                    )

                    AssistChip(
                        onClick = { showFinishDialog = true },
                        label = { Text(if (criticalGranted) "Continuar" else "Revisar permisos") }
                    )
                }
            }

            PermissionItem(
                title = "VPN",
                subtitle = "Necesario para crear el túnel de red",
                granted = permissions.vpn,
                actionLabel = if (permissions.vpn) "Concedido" else "Solicitar",
                onClick = {
                    PermissionHelper.vpnPermissionIntent(context)?.let(vpnLauncher::launch)
                }
            )

            PermissionItem(
                title = "Mostrar sobre otras apps",
                subtitle = "Requerido por la ventana flotante",
                granted = permissions.overlay,
                actionLabel = if (permissions.overlay) "Concedido" else "Abrir ajustes",
                onClick = { overlayLauncher.launch(PermissionHelper.overlayPermissionIntent(context)) }
            )

            PermissionItem(
                title = "Notificaciones",
                subtitle = "Permite mostrar el estado persistente de la VPN",
                granted = permissions.notification,
                actionLabel = if (permissions.notification) "Concedido" else "Solicitar",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        storageLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    } else {
                        refreshTick++
                    }
                }
            )

            PermissionItem(
                title = "Optimización de batería",
                subtitle = "Evita que Android cierre el servicio VPN",
                granted = permissions.battery,
                actionLabel = if (permissions.battery) "Concedido" else "Abrir ajustes",
                onClick = { batteryLauncher.launch(PermissionHelper.batteryOptimizationIntent(context)) }
            )

            PermissionItem(
                title = "Instalar apps desconocidas",
                subtitle = "Necesario para instalar actualizaciones descargadas fuera de Play Store",
                granted = permissions.unknownSources,
                actionLabel = if (permissions.unknownSources) "Concedido" else "Abrir ajustes",
                onClick = { unknownSourcesLauncher.launch(PermissionHelper.installUnknownAppsIntent(context)) }
            )

            PermissionItem(
                title = "Almacenamiento",
                subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    "No requiere permiso manual en Android 13+; la app usa selector de archivos."
                else
                    "Necesario para importar/exportar perfiles en dispositivos antiguos.",
                granted = PermissionHelper.hasStoragePermission(context),
                actionLabel = if (PermissionHelper.hasStoragePermission(context)) "Concedido" else "Solicitar",
                onClick = {
                    val permissionsToRequest = PermissionHelper.storagePermissions()
                    if (permissionsToRequest.isNotEmpty()) {
                        storageLauncher.launch(permissionsToRequest)
                    } else {
                        refreshTick++
                    }
                }
            )

            GhostCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                    Text(
                        text = "Recomendación",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Concede primero VPN, overlay, notificaciones y batería. Las actualizaciones conservarán perfiles y ajustes si se instalan sobre la misma firma.",
                        color = TextSecondary
                    )
                    Button(
                        onClick = {
                            if (criticalGranted) {
                                showFinishDialog = true
                            } else {
                                refreshTick++
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (criticalGranted) "Finalizar configuración" else "Volver a revisar")
                    }
                }
            }
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Cerrar configuración inicial") },
            text = { Text("Al continuar, la app abrirá su panel principal. Puedes revisar estos permisos luego desde Ajustes.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.completeFirstLaunch()
                    onComplete()
                    showFinishDialog = false
                }) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showFinishDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PermissionItem(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    GhostCard(
        borderColor = if (granted) NeonGreen.copy(alpha = 0.35f) else NeonAmber.copy(alpha = 0.35f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
            ) {
                Icon(
                    imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = if (granted) NeonGreen else NeonAmber
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onClick, enabled = !granted) {
                    Text(actionLabel)
                }
            }
        }
    }
}
