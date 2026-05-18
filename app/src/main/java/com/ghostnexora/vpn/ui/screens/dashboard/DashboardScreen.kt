package com.ghostnexora.vpn.ui.screens.dashboard

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.data.model.VpnConnectionState
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.MonoStyle
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.NeonRed
import com.ghostnexora.vpn.ui.theme.SurfaceVariant
import com.ghostnexora.vpn.ui.theme.TextOnAccent
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary
import com.ghostnexora.vpn.util.toSessionTime

@Composable
fun DashboardScreen(
    onNavigateToProfiles: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted()
        } else {
            viewModel.onVpnPermissionDenied()
        }
    }

    LaunchedEffect(uiState.pendingVpnPermissionIntent) {
        uiState.pendingVpnPermissionIntent?.let(vpnPermissionLauncher::launch)
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
                .padding(Dimens.ScreenPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXXL)
        ) {
            Spacer(Modifier.height(Dimens.SpaceSM))

            ConnectionSection(
                state = uiState.connectionState,
                onAction = { activity?.let(viewModel::onMainAction) }
            )

            if (uiState.isConnected) {
                SessionInfoRow(
                    elapsed = uiState.sessionElapsed,
                    serverIp = uiState.serverIp
                )
            }

            ActiveProfileCard(
                profile = uiState.activeProfile,
                connectionState = uiState.connectionState,
                onChangeProfile = onNavigateToProfiles
            )

            QuickActionsRow(
                isConnected = uiState.isConnected,
                onViewProfiles = onNavigateToProfiles,
                onDisconnect = viewModel::disconnect
            )

            Spacer(Modifier.height(Dimens.SpaceLG))
        }
    }
}

@Composable
private fun ConnectionSection(
    state: VpnConnectionState,
    onAction: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXL)
    ) {
        Text(
            text = state.label(),
            style = MaterialTheme.typography.headlineMedium,
            color = stateColor(state)
        )
        MainActionButton(state = state, onClick = onAction)
    }
}

@Composable
private fun MainActionButton(
    state: VpnConnectionState,
    onClick: () -> Unit
) {
    val color = stateColor(state)
    Box(
        modifier = Modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(4.dp, color.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = stateIcon(state),
            contentDescription = state.actionLabel(),
            tint = color,
            modifier = Modifier.size(82.dp)
        )
    }
}

@Composable
private fun SessionInfoRow(
    elapsed: Long,
    serverIp: String
) {
    GhostCard(
        backgroundColor = NeonGreen.copy(alpha = 0.12f),
        borderColor = NeonGreen,
        contentPadding = PaddingValues(horizontal = Dimens.SpaceXXL, vertical = Dimens.SpaceMD)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SessionInfoItem(Icons.Filled.Timer, "Tiempo", elapsed.toSessionTime(), NeonGreen)
            androidx.compose.material3.VerticalDivider(
                modifier = Modifier.height(40.dp),
                color = BorderSubtle
            )
            SessionInfoItem(Icons.Filled.Language, "Servidor", serverIp.ifEmpty { "—" }, NeonCyan)
        }
    }
}

@Composable
private fun SessionInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(color = tint), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActiveProfileCard(
    profile: VpnProfile?,
    connectionState: VpnConnectionState,
    onChangeProfile: () -> Unit
) {
    GhostCard(borderColor = BorderSubtle) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceMD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(NeonCyan.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.VpnKey, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(32.dp))
            }

            Spacer(Modifier.width(Dimens.SpaceMD))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.name ?: "Sin perfil activo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (profile != null) {
                    Text(
                        text = "${profile.host}:${profile.port} • ${profile.connectionModeLabel}",
                        style = MonoStyle.copy(color = TextSecondary)
                    )
                } else {
                    Text(
                        text = "Selecciona o crea un perfil",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }

            if (!connectionState.isConnected) {
                IconButton(onClick = onChangeProfile) {
                    Icon(Icons.Filled.Edit, contentDescription = "Cambiar perfil", tint = NeonCyan)
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    isConnected: Boolean,
    onViewProfiles: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
    ) {
        QuickActionCard(
            icon = Icons.Filled.List,
            label = "Perfiles",
            color = NeonCyan,
            modifier = Modifier.weight(1f),
            onClick = onViewProfiles
        )

        QuickActionCard(
            icon = Icons.Filled.PowerSettingsNew,
            label = if (isConnected) "Desconectar" else "Desconectado",
            color = if (isConnected) NeonRed else TextTertiary,
            modifier = Modifier.weight(1f),
            enabled = isConnected,
            onClick = { if (isConnected) onDisconnect() }
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    GhostCard(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        backgroundColor = color.copy(alpha = 0.12f),
        borderColor = BorderSubtle,
        contentPadding = PaddingValues(Dimens.SpaceMD)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

private fun stateIcon(state: VpnConnectionState): ImageVector = when (state) {
    is VpnConnectionState.Connected -> Icons.Filled.Lock
    is VpnConnectionState.Connecting -> Icons.Filled.Sync
    is VpnConnectionState.Disconnecting -> Icons.Filled.Sync
    is VpnConnectionState.Error -> Icons.Filled.PowerSettingsNew
    VpnConnectionState.Disconnected -> Icons.Filled.PowerSettingsNew
}

private fun stateColor(state: VpnConnectionState): Color = when (state) {
    is VpnConnectionState.Connected -> NeonGreen
    is VpnConnectionState.Connecting -> NeonAmber
    is VpnConnectionState.Disconnecting -> NeonAmber
    is VpnConnectionState.Error -> NeonRed
    VpnConnectionState.Disconnected -> NeonCyan
}
