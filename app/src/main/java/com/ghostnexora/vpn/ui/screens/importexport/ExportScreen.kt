@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.importexport

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderNormal
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostButton
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.SurfaceElevated
import com.ghostnexora.vpn.ui.theme.SurfaceVariant
import com.ghostnexora.vpn.ui.theme.TextOnAccent
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary

@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ImportExportViewModel = hiltViewModel()
) {
    val state by viewModel.exportState.collectAsState()
    val profiles by viewModel.allProfiles.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val manualSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUri(uri, profiles)
        }
    }

    LaunchedEffect(state.exportSuccess) {
        if (state.exportSuccess) {
            snackbarHostState.showSnackbar("${state.exportedCount} perfil(es) exportado(s)")
            viewModel.clearExportMessage()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearExportMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Exportar Perfiles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXXL)
        ) {
            item {
                GhostCard(
                    backgroundColor = NeonAmber.copy(alpha = 0.14f),
                    borderColor = NeonAmber,
                    contentPadding = PaddingValues(Dimens.SpaceMD)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(NeonAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.FileUpload, null, tint = NeonAmber, modifier = Modifier.size(Dimens.IconLG))
                        }
                        Column {
                            Text(
                                "Exportar Perfiles",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                "Selecciona los perfiles a exportar como JSON",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            if (profiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.Space3XL),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
                        ) {
                            Icon(Icons.Filled.FolderOff, null, tint = TextTertiary, modifier = Modifier.size(Dimens.Space4XL))
                            Text("No hay perfiles para exportar", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text("Crea o importa perfiles primero", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = when {
                                state.selectedIds.isEmpty() -> "Ninguno seleccionado (se exportarán todos)"
                                state.selectedIds.size == profiles.size -> "Todos seleccionados"
                                else -> "${state.selectedIds.size} de ${profiles.size} seleccionados"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        TextButton(onClick = { viewModel.toggleSelectAll(profiles) }) {
                            Text(
                                text = if (state.selectedIds.size == profiles.size) "Deseleccionar todo" else "Seleccionar todo",
                                color = NeonCyan,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                items(items = profiles, key = { it.id }) { profile ->
                    val isSelected = profile.id in state.selectedIds
                    GhostCard(
                        backgroundColor = if (isSelected) NeonCyan.copy(alpha = 0.08f) else SurfaceVariant,
                        borderColor = if (isSelected) NeonCyan else BorderNormal,
                        contentPadding = PaddingValues(Dimens.SpaceMD),
                        modifier = Modifier.clickable { viewModel.toggleProfileSelection(profile.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) NeonCyan else SurfaceElevated)
                                    .border(
                                        width = Dimens.BorderNormal,
                                        color = if (isSelected) NeonCyan else BorderNormal,
                                        shape = RoundedCornerShape(6.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, null, tint = TextOnAccent, modifier = Modifier.size(14.dp))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(NeonCyan.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.VpnKey, null, tint = if (isSelected) NeonCyan else TextTertiary, modifier = Modifier.size(Dimens.IconSM))
                            }

                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = profile.name.ifEmpty { "Sin nombre" },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${profile.host}:${profile.port} • ${profile.connectionModeLabel}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (profile.tags.isNotEmpty()) {
                                Text(text = "${profile.tags.size} tag(s)", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                            }
                        }
                    }
                }

                item {
                    val label = when {
                        state.selectedIds.isEmpty() -> "Guardar todos en Descargas"
                        state.selectedIds.size == 1 -> "Guardar 1 perfil en Descargas"
                        else -> "Guardar ${state.selectedIds.size} perfiles en Descargas"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                        if (state.isLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NeonAmber)
                        }

                        GhostButton(
                            text = label,
                            onClick = { viewModel.exportSelected(profiles) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading && profiles.isNotEmpty(),
                            containerColor = NeonAmber,
                            contentColor = TextOnAccent
                        )

                        GhostButton(
                            text = "Elegir ubicación manual",
                            onClick = {
                                val suggestedName = "ghost_nexora_export_${System.currentTimeMillis()}.json"
                                manualSaveLauncher.launch(suggestedName)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading && profiles.isNotEmpty(),
                            containerColor = NeonCyan,
                            contentColor = TextOnAccent
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
                        ) {
                            Icon(Icons.Filled.Info, null, tint = TextTertiary, modifier = Modifier.size(Dimens.IconXS))
                            Text(
                                "Por defecto se guardará en Descargas/GhostNexoraVPN",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(Dimens.Space3XL)) }
        }
    }
}
