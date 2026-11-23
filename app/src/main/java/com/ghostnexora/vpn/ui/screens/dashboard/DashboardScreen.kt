@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.dashboard

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ghostnexora.vpn.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.NetworkPreferences
import com.ghostnexora.vpn.data.model.VpnConnectionState
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.model.VpnTrafficStats
import com.ghostnexora.vpn.ui.components.HtmlNoteView
import com.ghostnexora.vpn.ui.components.VpnLogConsole
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
import com.ghostnexora.vpn.util.vpnLogLine
import com.ghostnexora.vpn.util.toSessionTime
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DashboardScreen(
    lifecycleOwner: LifecycleOwner,
    onNavigateToProfiles: () -> Unit,
    logsExpanded: Boolean = false,
    onLogsExpanded: (Boolean) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val network by viewModel.networkPreferences.collectAsState(initial = null)
    var showClearLogs by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = logsExpanded) { onLogsExpanded(false) }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 2 })
    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onVpnPermissionGranted()
        else viewModel.onVpnPermissionDenied()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, lifecycleOwner = lifecycleOwner) {
        viewModel.refreshRuntime()
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val copiedMessage = stringResource(R.string.dashboard_logs_copied)
    val copyLogs = {
        val ordered = state.recentLogs
            .filterNot { LogPresentation.isLegacyVpsMotd(it.message) }
            .sortedWith(compareBy<LogEntry> { it.timestamp }.thenBy { it.id })
        clipboard.setText(AnnotatedString(ordered.joinToString("\n") { it.vpnLogLine() }))
        scope.launch { snackbar.showSnackbar(copiedMessage) }
        Unit
    }

    if (showClearLogs) {
        AlertDialog(
            onDismissRequest = { showClearLogs = false },
            title = { Text(stringResource(R.string.dashboard_logs_clear)) },
            text = { Text(stringResource(R.string.dashboard_logs_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearLogs = false
                    viewModel.clearLogs()
                }) { Text(stringResource(R.string.dashboard_logs_clear), color = NeonRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogs = false }) {
                    Text(stringResource(R.string.btn_cancel_action))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = BackgroundDark,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!logsExpanded) {
                CompactDashboardTabs(
                    selectedPage = pager.currentPage,
                    onSelect = { page -> scope.launch { pager.animateScrollToPage(page) } }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (logsExpanded) {
                LogPage(state.recentLogs, true, copyLogs, { showClearLogs = true }) {
                    onLogsExpanded(false)
                }
            } else {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    if (page == 0) {
                        OverviewPage(
                            state = state,
                            network = network,
                            onDnsChange = viewModel::setVpnDnsEnabled,
                            onAction = { viewModel.onMainAction { vpnPermission.launch(it) } },
                            onProfiles = onNavigateToProfiles
                        )
                    } else {
                        LogPage(state.recentLogs, false, copyLogs, { showClearLogs = true }) {
                            onLogsExpanded(true)
                        }
                    }
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
            .selectableGroup()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceSM)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariant.copy(alpha = 0.82f))
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DashboardTab(
            selected = selectedPage == 0,
            onClick = { onSelect(0) },
            label = stringResource(R.string.nav_dashboard),
            icon = { Icon(Icons.Filled.Home, null, modifier = Modifier.size(19.dp)) },
            modifier = Modifier.weight(1f).testTag("dashboard-home-tab")
        )
        DashboardTab(
            selected = selectedPage == 1,
            onClick = { onSelect(1) },
            label = stringResource(R.string.dashboard_log_tab),
            icon = { Icon(Icons.AutoMirrored.Filled.Article, null, modifier = Modifier.size(17.dp)) },
            modifier = Modifier.weight(1f).testTag("dashboard-log-tab")
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
            .background(if (selected) NeonGreen.copy(alpha = 0.14f) else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (selected) NeonGreen else TextSecondary
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
    network: NetworkPreferences?,
    onDnsChange: (Boolean) -> Unit,
    onAction: () -> Unit,
    onProfiles: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
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
        ActiveProfileCard(state.activeProfile, onProfiles)
        if (network != null) {
            val dnsAccessibilityLabel = stringResource(R.string.vpn_dns_enabled)
            GhostCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = TextSecondary)
                    Spacer(Modifier.size(Dimens.SpaceMD))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.vpn_dns_enabled), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(
                                if (network.vpnDnsEnabled) R.string.vpn_dns_managed else R.string.vpn_dns_system
                            ),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Checkbox(
                        checked = network.vpnDnsEnabled,
                        onCheckedChange = onDnsChange,
                        modifier = Modifier.semantics {
                            contentDescription = dnsAccessibilityLabel
                        }
                    )
                }
                Text(
                    stringResource(R.string.vpn_dns_next_session),
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        val noteHtml = state.activeProfile?.displayNoteHtml.orEmpty()
        if (noteHtml.isNotBlank()) HtmlNoteView(html = noteHtml, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Dimens.SpaceLG))
    }
}

@Composable
private fun ConnectionHero(state: DashboardUiState, onAction: () -> Unit) {
    val connection = state.connectionState
    val color = stateColor(connection)
    val enabled = connection !is VpnConnectionState.Connecting && connection !is VpnConnectionState.Disconnecting
    GhostCard(
        borderColor = color.copy(alpha = 0.28f),
        backgroundColor = SurfaceVariant,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                    Text(stringResource(connection.labelRes), color = color, style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    stringResource(R.string.dashboard_connection_title),
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(connectionSubtitle(state), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Security, null, tint = color, modifier = Modifier.size(27.dp))
            }
        }
        Spacer(Modifier.height(Dimens.SpaceMD))
        Button(
            onClick = onAction,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isConnected) color.copy(alpha = 0.14f) else color,
                contentColor = if (state.isConnected) color else Color.Black
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Filled.PowerSettingsNew, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(connection.actionLabelRes))
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
                    stringResource(R.string.dashboard_session_activity),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
            ) {
                Metric(
                    stringResource(R.string.dashboard_download),
                    formatRate(stats.downloadBytesPerSecond),
                    formatBytes(stats.receivedBytes),
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    stringResource(R.string.dashboard_upload),
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
                    stringResource(R.string.dashboard_elapsed),
                    elapsed.toSessionTime(),
                    stats.networkType,
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    stringResource(R.string.dashboard_latency),
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
private fun ActiveProfileCard(profile: VpnProfile?, onProfiles: () -> Unit) {
    GhostCard(backgroundColor = SurfaceVariant, borderColor = BorderSubtle) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SignalCellularAlt, null, tint = NeonCyan)
            Spacer(Modifier.size(Dimens.SpaceMD))
            Column(Modifier.weight(1f)) {
                Text(
                    profile?.name ?: stringResource(R.string.dashboard_no_profile),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    profile?.let { "${it.serverAddress} · ${it.connectionModeLabel}" }
                        ?: stringResource(R.string.dashboard_choose_profile),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (profile?.isLocked == true) {
                    Text(
                        stringResource(R.string.dashboard_locked),
                        color = NeonAmber,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            IconButton(onClick = onProfiles) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.dashboard_change_profile), tint = NeonCyan)
            }
        }
    }
}

@Composable
private fun LogPage(
    logs: List<LogEntry>,
    expanded: Boolean,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onToggleExpanded: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Dimens.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.dashboard_log_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = onCopy, enabled = logs.isNotEmpty()) {
                Icon(Icons.Filled.ContentCopy, stringResource(R.string.dashboard_logs_copy))
            }
            IconButton(onClick = onClear, enabled = logs.isNotEmpty()) {
                Icon(Icons.Filled.DeleteOutline, stringResource(R.string.dashboard_logs_clear))
            }
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    if (expanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    stringResource(if (expanded) R.string.dashboard_logs_shrink else R.string.dashboard_logs_expand)
                )
            }
        }
        VpnLogConsole(
            logs = logs,
            modifier = Modifier.fillMaxWidth().weight(1f),
            maxHeight = null,
            embedded = true
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

@Composable
private fun connectionSubtitle(state: DashboardUiState): String = when (val connection = state.connectionState) {
    is VpnConnectionState.Connected -> "${state.serverIp} · ${state.traffic.protocol}"
    is VpnConnectionState.Reconnecting -> stringResource(R.string.dashboard_rebuilding_route)
    is VpnConnectionState.Connecting -> stringResource(R.string.dashboard_preparing_route)
    is VpnConnectionState.Disconnecting -> stringResource(R.string.dashboard_releasing_route)
    is VpnConnectionState.Error -> connection.message
    VpnConnectionState.Disconnected -> state.activeProfile?.connectionModeLabel ?: stringResource(R.string.dashboard_waiting)
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
