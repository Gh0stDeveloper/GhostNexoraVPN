@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.dashboard

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Notes
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
import androidx.compose.ui.graphics.Brush
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
import com.ghostnexora.vpn.ui.components.HtmlNoteView
import com.ghostnexora.vpn.ui.components.HttpInjectorLogConsole
import com.ghostnexora.vpn.ui.components.LogPresentation
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
import com.ghostnexora.vpn.ui.theme.backgroundGradient
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
                .background(backgroundGradient())
                .padding(padding)
                .padding(horizontal = Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
        ) {
            CompactDashboardTabs(
                selectedPage = pager.currentPage,
                onSelect = { page -> scope.launch { pager.animateScrollToPage(page) } }
            )

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
                            val ordered = state.recentLogs
                                .filterNot { LogPresentation.isLegacyVpsMotd(it.message) }
                                .sortedWith(
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
private fun CompactDashboardTabs(selectedPage: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpaceXS)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariant.copy(alpha = 0.82f))
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DashboardTab(
            selected = selectedPage == 0,
            onClick = { onSelect(0) },
            label = "Inicio",
            icon = { Icon(Icons.Filled.Security, null, modifier = Modifier.size(17.dp)) },
            modifier = Modifier.weight(1f)
        )
        DashboardTab(
            selected = selectedPage == 1,
            onClick = { onSelect(1) },
            label = "Registro",
            icon = { Icon(Icons.AutoMirrored.Filled.Article, null, modifier = Modifier.size(17.dp)) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DashboardTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) NeonCyan.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (selected) NeonCyan else TextSecondary
        ) {
            icon()
            Spacer(Modifier.size(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
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
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
    ) {
        ConnectionHero(state, onAction)

        if (state.connectionState is VpnConnectionState.Reconnecting) {
            val reconnecting = state.connectionState
            GhostCard(backgroundColor = NeonAmber.copy(alpha = 0.08f), borderColor = NeonAmber) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
                ) {
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
        CreatorNoteSection(state.activeProfile)
        Spacer(Modifier.height(Dimens.SpaceLG))
    }
}

@Composable
private fun ConnectionHero(state: DashboardUiState, onAction: () -> Unit) {
    val color = stateColor(state.connectionState)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(color.copy(alpha = 0.13f), SurfaceVariant, SurfaceVariant.copy(alpha = 0.84f))
                )
            )
            .border(1.dp, color.copy(alpha = 0.38f), RoundedCornerShape(24.dp))
            .padding(horizontal = Dimens.SpaceXL, vertical = Dimens.SpaceLG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Text(
                state.connectionState.label().uppercase(Locale.getDefault()),
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
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
        Text(
            text = state.connectionState.actionHint(),
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PowerButton(state: VpnConnectionState, onAction: () -> Unit) {
    val color = stateColor(state)
    Box(
        modifier = Modifier
            .size(144.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.32f), CircleShape)
            .clickable(onClick = onAction),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f))
                .border(2.dp, color.copy(alpha = 0.78f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    if (state is VpnConnectionState.Reconnecting) Icons.Filled.Sync else Icons.Filled.PowerSettingsNew,
                    null,
                    tint = color,
                    modifier = Modifier.size(38.dp)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Actividad de la sesión",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text("EN VIVO", color = NeonGreen, style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
            ) {
                Metric(
                    "Descarga",
                    formatRate(stats.downloadBytesPerSecond),
                    formatBytes(stats.receivedBytes),
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    "Subida",
                    formatRate(stats.uploadBytesPerSecond),
                    formatBytes(stats.sentBytes),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
            ) {
                Metric(
                    "Tiempo",
                    elapsed.toSessionTime(),
                    stats.networkType,
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    "Latencia",
                    if (stats.latencyMs > 0) "${stats.latencyMs} ms" else "--",
                    "${stats.reconnectCount} reconexiones",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.14f))
            .padding(Dimens.SpaceMD),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace)
        )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
            ) {
                Icon(Icons.Filled.Lock, null, tint = if (protected) NeonGreen else TextTertiary)
                Column {
                    Text(
                        if (protected) "Tráfico protegido" else "Protección inactiva",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (state.isReconnecting) {
                            "El túnel permanece capturando tráfico durante la recuperación"
                        } else {
                            state.traffic.protocol
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                "IPv4 + IPv6 · DNS dentro del túnel · TLS por perfil",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
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
                Text(
                    profile?.name ?: "Sin perfil activo",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    profile?.let { "${it.serverAddress} · ${it.connectionModeLabel}" }
                        ?: "Selecciona una configuración",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (profile?.isLocked == true) {
                    Text(
                        "Parámetros protegidos por el creador",
                        color = NeonAmber,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            IconButton(onClick = onProfiles) {
                Icon(Icons.Filled.Edit, contentDescription = "Cambiar perfil", tint = NeonCyan)
            }
        }
    }
}

@Composable
private fun CreatorNoteSection(profile: VpnProfile?) {
    val note = profile?.displayNoteHtml.orEmpty()
    if (note.isBlank()) return

    GhostCard(
        backgroundColor = SurfaceVariant,
        borderColor = NeonAmber.copy(alpha = 0.65f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
            ) {
                Icon(Icons.AutoMirrored.Filled.Notes, null, tint = NeonAmber)
                Column {
                    Text("Nota del creador", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${profile?.name.orEmpty()} · Contenido completo",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                "Desliza dentro de la nota para leer toda la información",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            HtmlNoteView(
                html = note,
                modifier = Modifier.fillMaxWidth().height(420.dp)
            )
        }
    }
}

@Composable
private fun LogPage(logs: List<LogEntry>, onCopy: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Registro de conexión", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Vista completa · sin credenciales ni tokens",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.AutoMirrored.Filled.Article, "Copiar registro", tint = NeonCyan)
            }
        }
        HttpInjectorLogConsole(
            logs = logs,
            modifier = Modifier.fillMaxWidth().weight(1f),
            maxHeight = null
        )
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

private fun VpnConnectionState.actionHint(): String = when (this) {
    is VpnConnectionState.Connected -> "Toca el botón para cerrar el túnel de forma segura"
    is VpnConnectionState.Connecting -> "Toca el botón si deseas cancelar el intento"
    is VpnConnectionState.Reconnecting -> "La aplicación mantiene protegida la ruta durante la recuperación"
    is VpnConnectionState.Disconnecting -> "Liberando la interfaz VPN de Android"
    is VpnConnectionState.Error -> "Revisa el registro y toca para volver a intentar"
    VpnConnectionState.Disconnected -> "Toca el botón para activar la VPN del sistema"
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
