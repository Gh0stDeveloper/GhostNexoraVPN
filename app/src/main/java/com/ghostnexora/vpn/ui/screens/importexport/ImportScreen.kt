@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderNormal
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostButton
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.SurfaceVariant
import com.ghostnexora.vpn.ui.theme.TextOnAccent
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api

@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportExportViewModel = hiltViewModel()
) {
    val state by viewModel.importState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onFilePicked(uri)
    }

    LaunchedEffect(state.importSuccess) {
        if (state.importSuccess) {
            snackbarHostState.showSnackbar("${state.importedCount} perfil(es) importado(s) correctamente")
            viewModel.clearImportMessage()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearImportMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Importar Perfiles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Volver")
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
                    backgroundColor = NeonCyan.copy(alpha = 0.14f),
                    borderColor = NeonCyan,
                    contentPadding = PaddingValues(Dimens.SpaceMD)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(NeonCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.FileDownload, null, tint = NeonCyan, modifier = Modifier.size(Dimens.IconXL))
                        }

                        Text(
                            text = "Importar Perfiles",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Selecciona un archivo JSON exportado previamente",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                GhostButton(
                    text = if (state.isLoading) "Cargando..." else "Seleccionar Archivo JSON",
                    onClick = { pickerLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading,
                    containerColor = NeonCyan,
                    contentColor = TextOnAccent
                )
            }

            if (state.isLoading) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NeonAmber)
                }
            }

            if (state.hasFile) {
                item {
                    GhostCard(
                        backgroundColor = SurfaceVariant,
                        borderColor = BorderNormal,
                        contentPadding = PaddingValues(Dimens.SpaceMD)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                            Text("Archivo seleccionado", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                            Text(state.fileName.ifEmpty { "archivo.json" }, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (state.sourceName.isNotBlank()) {
                                Text("Origen: ${state.sourceName}", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                            }
                            state.validation?.let { validation ->
                                Text(
                                    text = validation.message,
                                    color = if (validation.isValid) NeonGreen else Color.Red,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (state.previewProfiles.isNotEmpty()) {
                item {
                    Text(
                        text = "Vista previa (${state.previewProfiles.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }

                items(state.previewProfiles.take(5), key = { it.id }) { profile ->
                    GhostCard(
                        backgroundColor = SurfaceVariant,
                        borderColor = BorderNormal,
                        contentPadding = PaddingValues(Dimens.SpaceMD)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
                            Text(profile.name.ifEmpty { "Sin nombre" }, color = TextPrimary)
                            Text(
                                text = "${profile.host}:${profile.port} • ${profile.connectionModeLabel}",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { viewModel.confirmImport(merge = false) }, enabled = state.canImport) {
                            Text("Reemplazar", color = NeonAmber)
                        }
                        TextButton(onClick = { viewModel.confirmImport(merge = true) }, enabled = state.canImport) {
                            Text("Fusionar", color = NeonCyan)
                        }
                    }
                }
            }

            if (state.importedCount > 0) {
                item {
                    GhostCard(backgroundColor = NeonGreen.copy(alpha = 0.10f), borderColor = NeonGreen) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(48.dp))
                            Text("${state.importedCount} perfiles importados", color = NeonGreen)
                        }
                    }
                }
            }

            if (state.error != null) {
                item {
                    GhostCard(backgroundColor = Color.Red.copy(alpha = 0.08f), borderColor = Color.Red) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                            Icon(Icons.Filled.Error, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                            Text(state.error ?: "Error", color = Color.Red, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Dimens.Space3XL))
                Text(
                    text = "Solo se aceptan archivos JSON generados por GhostNexoraVPN",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
