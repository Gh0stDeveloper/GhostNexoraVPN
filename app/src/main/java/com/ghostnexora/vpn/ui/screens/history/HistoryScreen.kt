package com.ghostnexora.vpn.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderNormal
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: (() -> Unit)? = null,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val logs by viewModel.historyLogs.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val selectedProfileId by viewModel.selectedProfileId.collectAsState()
    val selectedProfileName by viewModel.selectedProfileName.collectAsState()

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Historial de sesiones") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLG)
        ) {
            item {
                GhostCard(
                    backgroundColor = SurfaceVariant,
                    borderColor = BorderNormal,
                    contentPadding = PaddingValues(Dimens.SpaceMD)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = NeonCyan)
                            Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                                Text(
                                    text = "Registro por perfil",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Sesiones, reconexiones y eventos asociados al perfil activo o al filtro elegido.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Text(
                            text = "Filtro actual: $selectedProfileName",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                            StatPill(label = "Eventos", value = logs.size.toString(), accent = NeonCyan)
                            StatPill(
                                label = "Perfiles",
                                value = profiles.size.toString(),
                                accent = NeonGreen
                            )
                            StatPill(
                                label = "Errores",
                                value = logs.count { it.level == LogLevel.ERROR }.toString(),
                                accent = NeonRed
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    Text(
                        text = "Filtrar por perfil",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )

                    Row(
                        modifier = androidx.compose.ui.Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
                    ) {
                        FilterChip(
                            selected = selectedProfileId == null,
                            onClick = { viewModel.clearFilter() },
                            label = { Text("Todos") },
                            leadingIcon = {
                                Icon(Icons.Filled.FilterAltOff, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(Dimens.IconSM))
                            }
                        )

                        profiles.forEach { profile ->
                            FilterChip(
                                selected = selectedProfileId == profile.id,
                                onClick = { viewModel.selectProfile(profile.id) },
                                label = { Text(profile.name.ifBlank { profile.host }) }
                            )
                        }
                    }
                }
            }

            if (logs.isEmpty()) {
                item {
                    GhostCard(
                        backgroundColor = SurfaceVariant,
                        borderColor = BorderNormal,
                        contentPadding = PaddingValues(Dimens.SpaceXL)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                            Text(
                                text = "No hay eventos registrados",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Las sesiones de conexión, reconexión y errores aparecerán aquí cuando el servicio VPN escriba eventos en Room.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(logs, key = { it.id }) { log ->
                    GhostCard(
                        backgroundColor = SurfaceVariant,
                        borderColor = BorderNormal,
                        contentPadding = PaddingValues(Dimens.SpaceMD)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                                Text(
                                    text = log.level.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (log.level) {
                                        LogLevel.DEBUG -> TextTertiary
                                        LogLevel.INFO -> TextSecondary
                                        LogLevel.SUCCESS -> NeonGreen
                                        LogLevel.WARNING -> NeonAmber
                                        LogLevel.ERROR -> NeonRed
                                    }
                                )
                                Text(
                                    text = log.dateTimeFormatted,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary
                                )
                            }

                            if (log.tag.isNotBlank()) {
                                Text(
                                    text = log.tag,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonCyan
                                )
                            }

                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = androidx.compose.ui.Modifier.height(Dimens.Space3XL)) }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color
) {
    GhostCard(
        backgroundColor = accent.copy(alpha = 0.10f),
        borderColor = accent,
        contentPadding = PaddingValues(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
}
