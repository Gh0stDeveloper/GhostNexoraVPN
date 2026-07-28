@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ImportExportViewModel = hiltViewModel()
) {
    val state by viewModel.exportState.collectAsState()
    val profiles by viewModel.allProfiles.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val manualSave = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.exportToUri(it, profiles) } }

    LaunchedEffect(state.exportSuccess) {
        if (state.exportSuccess) {
            snackbar.showSnackbar("${state.exportedCount} perfil(es) exportado(s) con cifrado GNX2")
            viewModel.clearExportMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearExportMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Exportar configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLG)
        ) {
            item {
                GhostCard(
                    backgroundColor = NeonGreen.copy(alpha = 0.08f),
                    borderColor = NeonGreen,
                    contentPadding = PaddingValues(Dimens.SpaceMD)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, null, tint = NeonGreen)
                            Text("Exportación cifrada obligatoria", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "El archivo .gnx usa una clave de datos aleatoria por exportación, cifrado autenticado y una clave derivada de tu contraseña. No se exportan archivos nuevos en JSON plano.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (profiles.isEmpty()) {
                item { Text("No hay perfiles para exportar", color = TextSecondary) }
            } else {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                state.selectedIds.isEmpty() -> "Se exportarán todos (${profiles.size})"
                                else -> "Seleccionados ${state.selectedIds.size} de ${profiles.size}"
                            },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { viewModel.toggleSelectAll(profiles) }) {
                            Text(if (state.selectedIds.size == profiles.size) "Deseleccionar" else "Seleccionar todo", color = NeonCyan)
                        }
                    }
                }

                items(profiles, key = { it.id }) { profile ->
                    val selected = profile.id in state.selectedIds
                    GhostCard(
                        modifier = Modifier.clickable { viewModel.toggleProfileSelection(profile.id) },
                        backgroundColor = if (selected) NeonCyan.copy(alpha = 0.08f) else SurfaceVariant,
                        borderColor = if (selected) NeonCyan else BorderNormal,
                        contentPadding = PaddingValues(Dimens.SpaceMD)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) NeonCyan else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Icon(Icons.Filled.Check, null, tint = TextOnAccent, modifier = Modifier.size(16.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(profile.name.ifBlank { "Sin nombre" }, color = TextPrimary)
                                Text("${profile.host}:${profile.port} · ${profile.connectionModeLabel}", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::setExportPassword,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Contraseña de protección") },
                        supportingText = { Text("Mínimo 10 caracteres. No se guarda dentro del archivo.") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.passwordConfirmation,
                        onValueChange = viewModel::setExportPasswordConfirmation,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirmar contraseña") },
                        isError = state.passwordConfirmation.isNotEmpty() && state.password != state.passwordConfirmation,
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }

                if (state.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                        GhostButton(
                            text = "Guardar archivo .gnx cifrado",
                            onClick = { viewModel.exportSelected(profiles) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.passwordValid && !state.isLoading,
                            containerColor = NeonGreen,
                            contentColor = TextOnAccent
                        )
                        GhostButton(
                            text = "Elegir ubicación",
                            onClick = { manualSave.launch("ghost_nexora_${System.currentTimeMillis()}.gnx") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.passwordValid && !state.isLoading,
                            containerColor = NeonCyan,
                            contentColor = TextOnAccent
                        )
                        Text(
                            "Guarda la contraseña en un lugar seguro. Sin ella no existe un mecanismo de recuperación del archivo.",
                            color = NeonAmber,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
