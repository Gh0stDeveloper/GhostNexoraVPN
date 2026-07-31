@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.dashboard

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.VpnConnectionState
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.model.VpnTrafficStats
import com.ghostnexora.vpn.ui.components.HttpInjectorLogConsole
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.NeonRed
import com.ghostnexora.vpn.ui.theme.SurfaceVariant
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary
import com.ghostnexora.vpn.util.httpInjectorLine
import com.ghostnexora.vpn.util.toSessionTime
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DashboardScreen(
    onNavigateToProfiles: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 2 })
    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onVpnPermissionGranted()
        else viewModel.onVpnPermissionDenied()
    }

    LaunchedEffect(state.pendingVpnPermissionIntent) {
        state.pendingVpnPermissionIntent?.let(vpnPermission::launch)
    }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceSM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
        ) {
            TabRow(selectedTabIndex = pager.currentPage, containerColor = Color.Transparent) {
                Tab(
                    selected = pager.currentPage == 0,
                    onClick = { scope.launch { pager.animateScrollToPage(0) } },
                    text = { Text("Inicio") },
                    icon = { Icon(Icons.Filled.Security, null) }
                )
                Tab(
                    selected = pager.currentPage == 1,
                    onClick = { scope.launch { pager.animateScrollToPage(1) } },
                    text = { Text("Registro") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Article, null) }
                )
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                if (page == 0) {
                    OverviewPage(
                        state = state,
                        onAction = { activity?.let(viewModel::onMainAction) },
                        onProfiles = onNavigateToProfiles
                    )
                } else {
                    LogPage(
                        logs = state.recentLogs,
                        onCopy = {
                            val ordered = state.recentLogs.sortedWith(
                                compareBy<LogEntry> { it.timestamp }.thenBy { it.id }
                            )
                            clipboard.setText(
                                AnnotatedString(ordered.joinToString("\n") { it.httpInjectorLine() })
                            )
                            scope.launch { snackbar.showSnackbar("Registro saneado copiado") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewPage(
    state: DashboardUiState,
    onAction: () -> Unit,
    onProfiles: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLG)
    ) {
        Spacer(Modifier.height(Dimens.SpaceSM))
        Text(
            text = state.connectionState.label().uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelLarge,
            color = stateColor(state.connectionState),
            letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing
        )
        Text(
            text = state.activeProfile?.name ?: "Selecciona un servidor",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = connectionSubtitle(state),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        PowerButton(state.connectionState, onAction)

        if (state.connectionState is VpnConnectionState.Reconnecting) {
            val reconnecting = state.connectionState
            GhostCard(backgroundColor = NeonAmber.copy(alpha = 0.08f), borderColor = NeonAmber) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    Icon(Icons.Filled.Sync, null, tint = NeonAmber)
                    Column {
                        Text("Kill Switch activo", color = NeonAmber, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Intento ${reconnecting.attempt}. El TUN permanece activo para impedir salida directa.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        LiveStatsCard(state.traffic, state.sessionElapsed)
        SecurityStatusCard(state)
        ActiveProfileCard(state.activeProfile, onProfiles)
        Spacer(Modifier.height(Dimens.SpaceXL))
    }
}

@Composable
private fun PowerButton(state: VpnConnectionState, onAction: () -> Unit) {
    val color = stateColor(state)
    Box(
        modifier = Modifier
            .size(152.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onAction),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(116.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (state is VpnConnectionState.Reconnecting) Icons.Filled.Sync else Icons.Filled.PowerSettingsNew,
                    null,
                    tint = color,
                    modifier = Modifier.size(42.dp)
                )
                Text(state.actionLabel(), color = color, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun LiveStatsCard(stats: VpnTrafficStats, elapsed: Long) {
    GhostCard(backgroundColor = SurfaceVariant, borderColor = BorderSubtle) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
            Text("Sesión", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Descarga", formatRate(stats.downloadBytesPerSecond), formatBytes(stats.receivedBytes))
                Metric("Subida", formatRate(stats.uploadBytesPerSecond), formatBytes(stats.sentBytes), Alignment.End)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Tiempo", elapsed.toSessionTime(), stats.networkType)
                Metric(
                    "Latencia",
                    if (stats.latencyMs > 0) "${stats.latencyMs} ms" else "--",
                    "${stats.reconnectCount} reconexiones",
                    Alignment.End
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, detail: String, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = alignment) {
        Text(label, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace))
        Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SecurityStatusCard(state: DashboardUiState) {
    val protected = state.isConnected || state.isReconnecting
    GhostCard(
        backgroundColor = if (protected) NeonGreen.copy(alpha = 0.06f) else SurfaceVariant,
        borderColor = if (protected) NeonGreen else BorderSubtle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                Icon(Icons.Filled.Lock, null, tint = if (protected) NeonGreen else TextTertiary)
                Column {
                    Text(if (protected) "Tráfico protegido" else "Protección inactiva", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.isReconnecting) "El túnel permanece capturando tráfico durante la recuperación"
                        else state.traffic.protocol,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text("IPv4 + IPv6 · DNS dentro del túnel · TLS por perfil", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ActiveProfileCard(profile: VpnProfile?, onProfiles: () -> Unit) {
    GhostCard(backgroundColor = SurfaceVariant, borderColor = BorderSubtle) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SignalCellularAlt, null, tint = NeonCyan)
            Spacer(Modifier.size(Dimens.SpaceMD))
            Column(Modifier.weight(1f)) {
                Text(profile?.name ?: "Sin perfil activo", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    profile?.let { "${it.host}:${it.port} · ${it.connectionModeLabel}" } ?: "Selecciona una configuración",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onProfiles) {
                Icon(Icons.Filled.Edit, contentDescription = "Cambiar perfil", tint = NeonCyan)
            }
        }
    }
}

@Composable
private fun LogPage(logs: List<LogEntry>, onCopy: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
    ) {
        GhostCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = SurfaceVariant,
            borderColor = BorderSubtle,
            contentPadding = PaddingValues(Dimens.SpaceMD)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Registro de conexión", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text("Sin credenciales ni tokens", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onCopy) { Icon(Icons.AutoMirrored.Filled.Article, "Copiar registro", tint = NeonCyan) }
                }
                HttpInjectorLogConsole(logs = logs, modifier = Modifier.fillMaxWidth(), maxHeight = 620)
            }
        }
    }
}

private fun stateColor(state: VpnConnectionState): Color = when (state) {
    is VpnConnectionState.Connected -> NeonGreen
    is VpnConnectionState.Reconnecting -> NeonAmber
    is VpnConnectionState.Connecting -> NeonCyan
    is VpnConnectionState.Disconnecting -> NeonAmber
    is VpnConnectionState.Error -> NeonRed
    VpnConnectionState.Disconnected -> TextTertiary
}

private fun connectionSubtitle(state: DashboardUiState): String = when (val connection = state.connectionState) {
    is VpnConnectionState.Connected -> "${state.serverIp} · ${state.traffic.protocol}"
    is VpnConnectionState.Reconnecting -> "Recuperando transporte sin liberar el TUN"
    is VpnConnectionState.Connecting -> "Preparando túnel seguro"
    is VpnConnectionState.Disconnecting -> "Cerrando rutas y transporte"
    is VpnConnectionState.Error -> connection.message
    VpnConnectionState.Disconnected -> state.activeProfile?.connectionModeLabel ?: "VPN lista para conectar"
}

private fun formatRate(bytes: Long): String = "${formatBytes(bytes)}/s"

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
