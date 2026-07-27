@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.BuildConfig
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostButton
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.SurfaceDark
import com.ghostnexora.vpn.ui.theme.TextOnAccent
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.util.PermissionHelper

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showClearLogs by remember { mutableStateOf(false) }
    var showLogsLimit by remember { mutableStateOf(false) }
    var showClearHosts by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }

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
                title = {
                    Column {
                        Text("Settings", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Security, permissions and maintenance",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = BackgroundDark
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
            SettingsSection("Protection") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
                ) {
                    Icon(Icons.Filled.Security, null, tint = NeonGreen)
                    Column(Modifier.weight(1f)) {
                        Text("Secure storage", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Profile credentials are encrypted with Android Keystore and AES-GCM",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                SwitchSetting(
                    "Kill Switch",
                    "Blocks direct traffic if a previously verified transport fails",
                    state.killSwitch
                ) { viewModel.toggleKillSwitch() }
                SwitchSetting(
                    "Automatic reconnection",
                    "Recovers the transport with protected exponential backoff",
                    state.autoReconnect
                ) { viewModel.toggleAutoReconnect() }
                InfoRow("Trusted SSH servers", state.knownHostCount.toString())
                GhostButton(
                    text = "Reset SSH fingerprints",
                    onClick = { showClearHosts = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
                    containerColor = Color.Red.copy(alpha = 0.75f),
                    contentColor = Color.White
                )
            }

            SettingsSection("Permissions and special access") {
                PermissionAccessRow(
                    title = "VPN authorization",
                    description = "Allows Android to create the encrypted system tunnel",
                    granted = state.permissionStatus.vpn,
                    onClick = {
                        PermissionHelper.vpnPermissionIntent(context)?.let(vpnLauncher::launch)
                            ?: viewModel.refreshPermissions()
                    }
                )
                PermissionAccessRow(
                    title = "Notifications",
                    description = "Required for the persistent VPN status on Android 13+",
                    granted = state.permissionStatus.notification,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.refreshPermissions()
                        }
                    }
                )
                PermissionAccessRow(
                    title = "Battery optimization exemption",
                    description = "Helps Android keep an active VPN service alive",
                    granted = state.permissionStatus.battery,
                    onClick = {
                        batteryLauncher.launch(PermissionHelper.batteryOptimizationIntent(context))
                    }
                )
                PermissionAccessRow(
                    title = "Display over other apps",
                    description = "Optional access used only by the floating connection control",
                    granted = state.permissionStatus.overlay,
                    onClick = {
                        overlayLauncher.launch(PermissionHelper.overlayPermissionIntent(context))
                    }
                )
                PermissionAccessRow(
                    title = "Install app updates",
                    description = "Requested only when installing an APK from GitHub Releases",
                    granted = state.permissionStatus.unknownSources,
                    onClick = {
                        installLauncher.launch(PermissionHelper.installUnknownAppsIntent(context))
                    }
                )
            }

            SettingsSection("General") {
                SwitchSetting(
                    "Reconnect on boot",
                    "Restore the VPN after Android restarts the service",
                    state.reconnectOnBoot
                ) { viewModel.toggleReconnectOnBoot() }
                SwitchSetting(
                    "Floating control",
                    "Show the optional connection overlay",
                    state.floatingWindow
                ) { viewModel.toggleFloatingWindow() }
                SwitchSetting(
                    "Notifications",
                    "Show persistent VPN connection status",
                    state.notifications
                ) { viewModel.toggleNotifications() }
            }

            SettingsSection("Logs") {
                ListSetting("Maximum entries", state.logsMaxEntries.toString()) { showLogsLimit = true }
                Text(
                    "Entries are sanitized before storage and copying to redact passwords, tokens and authorization headers.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM)
                )
                GhostButton(
                    text = "Clear logs",
                    onClick = { showClearLogs = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
                    containerColor = Color.Red.copy(alpha = 0.75f),
                    contentColor = Color.White
                )
            }

            SettingsSection("Encrypted configurations") {
                InfoRow("Export format", "GNX2")
                InfoRow("Content encryption", "AES-256-GCM")
                InfoRow("Password derivation", "PBKDF2-HMAC-SHA256")
                Text(
                    "New exports require a password. Plain JSON is accepted only for legacy migration.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM)
                )
            }

            SettingsSection("Updates") {
                InfoRow("Installed version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text(
                    "Automatic checks are rate-limited and dismissed releases are remembered. Manual checks always contact GitHub.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM)
                )
                GhostButton(
                    text = "Check for updates",
                    onClick = onCheckUpdates,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
                    containerColor = NeonCyan,
                    contentColor = TextOnAccent
                )
            }

            SettingsSection("About") {
                InfoRow("Version", BuildConfig.VERSION_NAME)
                InfoRow("Developed by", "Ghost Developer")
                InfoRow("GitHub", "@Gh0stDeveloper")
                InfoRow("Telegram", "@Gh0stDeveloper")
            }
            Spacer(Modifier.height(Dimens.Space3XL))
        }
    }

    if (showLogsLimit) {
        AlertDialog(
            onDismissRequest = { showLogsLimit = false },
            title = { Text("Maximum log entries") },
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
            dismissButton = { TextButton(onClick = { showLogsLimit = false }) { Text("Close") } }
        )
    }

    if (showClearLogs) {
        AlertDialog(
            onDismissRequest = { showClearLogs = false },
            title = { Text("Clear logs") },
            text = { Text("The local event history will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLogs()
                    showClearLogs = false
                }) { Text("Clear", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showClearLogs = false }) { Text("Cancel") } }
        )
    }

    if (showClearHosts) {
        AlertDialog(
            onDismissRequest = { showClearHosts = false },
            icon = { Icon(Icons.Filled.DeleteSweep, null) },
            title = { Text("Reset SSH trust") },
            text = { Text("Known host fingerprints will be removed and verified again on the next connection.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearKnownHosts()
                    showClearHosts = false
                }) { Text("Reset", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showClearHosts = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PermissionAccessRow(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    GhostCard(
        modifier = Modifier.padding(horizontal = Dimens.SpaceSM, vertical = 4.dp),
        borderColor = if (granted) NeonGreen.copy(alpha = 0.35f) else NeonAmber.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = if (granted) NeonGreen else NeonAmber
            )
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onClick, enabled = !granted) {
                Text(if (granted) "Granted" else "Open")
            }
        }
    }
}
