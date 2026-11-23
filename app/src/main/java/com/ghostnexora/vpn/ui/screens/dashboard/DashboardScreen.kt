@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.dashboard

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.ghostnexora.vpn.ui.components.ProfileNoteContent
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
import kotlinx.coroutines.delay
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

    val trafficHistory = rememberTrafficHistory(state)

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
                            trafficHistory = trafficHistory,
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
    trafficHistory: List<TrafficSample>,
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

        Text(
            stringResource(R.string.dashboard_server),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium
        )
        ActiveProfileCard(state.activeProfile, onProfiles)
        ConnectionFacts(state)
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
        LiveStatsCard(state.traffic, state.sessionElapsed, trafficHistory)
        val note = state.activeProfile?.displayNoteHtml.orEmpty()
        if (note.isNotBlank()) {
            ProfileNoteContent(
                note = note,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariant, RoundedCornerShape(16.dp))
                    .padding(Dimens.SpaceMD)
            )
        }
        Spacer(Modifier.height(Dimens.SpaceLG))
    }
}

@Composable
private fun ConnectionHero(state: DashboardUiState, onAction: () -> Unit) {
    val connection = state.connectionState
    val color = stateColor(connection)
    val enabled = connection !is VpnConnectionState.Connecting && connection !is VpnConnectionState.Disconnecting
    val action = stringResource(connection.actionLabelRes)
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.12f), SurfaceVariant)))
            .border(1.dp, color.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.background(color.copy(alpha = 0.11f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(color, CircleShape))
                Text(stringResource(connection.labelRes), color = color, style = MaterialTheme.typography.labelLarge)
            }
            Column(
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = action, onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier.size(86.dp)
                        .border(1.dp, color.copy(alpha = 0.22f), CircleShape)
                        .padding(7.dp)
                        .border(2.dp, color.copy(alpha = if (enabled) 0.8f else 0.42f), CircleShape)
                        .background(color.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, null, tint = color, modifier = Modifier.size(33.dp))
                }
                Text(action, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                connectionSubtitle(state),
                color = TextSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ActiveProfileCard(profile: VpnProfile?, onProfiles: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(SurfaceVariant)
            .border(1.dp, BorderSubtle, shape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.dashboard_change_profile), onClick = onProfiles)
            .padding(Dimens.SpaceMD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
    ) {
        Box(Modifier.size(42.dp).background(NeonGreen.copy(alpha = 0.11f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Dns, null, tint = NeonGreen, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                profile?.name ?: stringResource(R.string.dashboard_no_profile),
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                when {
                    profile == null -> stringResource(R.string.dashboard_choose_profile)
                    profile.isLocked -> stringResource(R.string.dashboard_locked)
                    else -> profile.serverAddress
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary)
    }
}

@Composable
private fun ConnectionFacts(state: DashboardUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
        ConnectionFact(Icons.Filled.Key, stringResource(R.string.dashboard_protocol),
            state.activeProfile?.connectionModeLabel ?: "—", Modifier.weight(1f))
        ConnectionFact(Icons.Filled.SignalCellularAlt, stringResource(R.string.dashboard_network),
            state.traffic.networkType.takeIf { state.isConnected && it != "--" }
                ?: state.defaultNetworkLabel.ifBlank { "—" }, Modifier.weight(1f))
        ConnectionFact(Icons.Filled.Timer, stringResource(R.string.dashboard_latency),
            if (state.traffic.latencyMs > 0) "${state.traffic.latencyMs} ms" else "—", Modifier.weight(1f))
    }
}

@Composable
private fun ConnectionFact(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier
) {
    Column(
        modifier = modifier.background(SurfaceVariant, RoundedCornerShape(14.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = NeonGreen, modifier = Modifier.size(17.dp))
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.labelMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LiveStatsCard(stats: VpnTrafficStats, elapsed: Long, history: List<TrafficSample>) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = { expanded = !expanded }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
        ) {
            Text(stringResource(R.string.dashboard_session_activity), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(Modifier.weight(1f), color = BorderSubtle)
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                stringResource(if (expanded) R.string.dashboard_collapse_traffic else R.string.dashboard_expand_traffic)
            )
        }
        if (expanded) {
            GhostCard(backgroundColor = SurfaceVariant, borderColor = BorderSubtle) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    Metric(
                        stringResource(R.string.dashboard_download), formatRate(stats.downloadBytesPerSecond),
                        formatBytes(stats.receivedBytes), NeonGreen, Modifier.weight(1f)
                    )
                    Metric(
                        stringResource(R.string.dashboard_upload), formatRate(stats.uploadBytesPerSecond),
                        formatBytes(stats.sentBytes), TextSecondary, Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Dimens.SpaceMD))
                if (history.size >= 2) {
                    TrafficChart(history)
                    Spacer(Modifier.height(Dimens.SpaceSM))
                }
                Text(
                    stringResource(R.string.dashboard_session_duration, elapsed.toSessionTime(), stats.reconnectCount),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private data class TrafficSample(val download: Long, val upload: Long)

@Composable
private fun rememberTrafficHistory(state: DashboardUiState): List<TrafficSample> {
    var history by remember { mutableStateOf(emptyList<TrafficSample>()) }
    val stats by rememberUpdatedState(state.traffic)
    LaunchedEffect((state.connectionState as? VpnConnectionState.Connected)?.connectedSince) {
        history = emptyList()
        if (state.isConnected) {
            while (true) {
                history = (history + TrafficSample(stats.downloadBytesPerSecond, stats.uploadBytesPerSecond)).takeLast(30)
                delay(1_000)
            }
        }
    }
    return history
}

@Composable
private fun TrafficChart(history: List<TrafficSample>) {
    val maxRate = history.maxOf { maxOf(it.download, it.upload) }.coerceAtLeast(1L)
    Canvas(Modifier.fillMaxWidth().height(62.dp)) {
        for (line in 1..3) {
            val y = size.height * line / 3
            drawLine(BorderSubtle, Offset(0f, y), Offset(size.width, y))
        }
        fun drawSeries(color: Color, rate: (TrafficSample) -> Long) {
            val path = Path()
            history.forEachIndexed { index, sample ->
                val x = size.width * index / history.lastIndex
                val y = size.height * (1f - 0.92f * rate(sample).coerceAtLeast(0L) / maxRate)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
        drawSeries(TextSecondary, TrafficSample::upload)
        drawSeries(NeonGreen, TrafficSample::download)
    }
}

@Composable
private fun Metric(label: String, value: String, detail: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier.background(color.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
            .padding(Dimens.SpaceSM),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = color, style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace))
        Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
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
    is VpnConnectionState.Connected -> stringResource(R.string.dashboard_tunnel_active)
    is VpnConnectionState.Reconnecting -> stringResource(R.string.dashboard_rebuilding_route)
    is VpnConnectionState.Connecting -> stringResource(R.string.dashboard_preparing_route)
    is VpnConnectionState.Disconnecting -> stringResource(R.string.dashboard_releasing_route)
    is VpnConnectionState.Error -> connection.message
    VpnConnectionState.Disconnected -> stringResource(R.string.dashboard_waiting)
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
