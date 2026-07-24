@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.importexport

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportExportViewModel = hiltViewModel()
) {
    val state by viewModel.importState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onFilePicked)
    }

    LaunchedEffect(state.importSuccess) {
        if (state.importSuccess) {
            snackbar.showSnackbar("${state.importedCount} perfil(es) importado(s)")
            viewModel.clearImportMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearImportMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Importar configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
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
                    backgroundColor = NeonCyan.copy(alpha = 0.10f),
                    borderColor = NeonCyan,
                    contentPadding = PaddingValues(Dimens.SpaceMD)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                            Icon(Icons.Filled.Lock, null, tint = NeonCyan)
                            Text("Importación protegida GNX2", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Los archivos .gnx se autentican y descifran únicamente con la contraseña usada al exportarlos. También se mantienen compatibles los JSON antiguos y enlaces de protocolo.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::setImportPassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contraseña del archivo .gnx") },
                    supportingText = { Text("No se almacena. Solo se usa durante el descifrado.") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }

            item {
                GhostButton(
                    text = if (state.isLoading) "Procesando…" else "Seleccionar archivo",
                    onClick = { picker.launch(arrayOf("application/octet-stream", "application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading,
                    containerColor = NeonCyan,
                    contentColor = TextOnAccent
                )
            }

            item {
                GhostButton(
                    text = "Importar desde portapapeles",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val raw = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (raw.isBlank()) scope.launch { snackbar.showSnackbar("El portapapeles está vacío") }
                        else viewModel.onTextProvided(raw)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading,
                    containerColor = NeonAmber,
                    contentColor = TextOnAccent
                )
            }

            if (state.passwordRequired && state.selectedUri != null) {
                item {
                    GhostButton(
                        text = "Descifrar de nuevo",
                        onClick = viewModel::retryEncryptedImport,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.password.length >= 10 && !state.isLoading,
                        containerColor = NeonGreen,
                        contentColor = TextOnAccent
                    )
                }
            }

            if (state.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

            if (state.hasFile || state.sourceName.isNotBlank()) {
                item {
                    GhostCard(backgroundColor = SurfaceVariant, borderColor = BorderNormal) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
                            Text(state.fileName.ifBlank { "Contenido importado" }, color = TextPrimary)
                            Text(state.sourceName, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                            state.validation?.let {
                                Text(it.message, color = if (it.isValid) NeonGreen else NeonAmber, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (state.previewProfiles.isNotEmpty()) {
                item { Text("Vista previa (${state.previewProfiles.size})", color = TextPrimary, style = MaterialTheme.typography.titleMedium) }
                items(state.previewProfiles.take(8), key = { it.id }) { profile ->
                    GhostCard(backgroundColor = SurfaceVariant, borderColor = BorderNormal) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
                            Text(profile.name.ifBlank { "Sin nombre" }, color = TextPrimary)
                            Text("${profile.host}:${profile.port} · ${profile.connectionModeLabel}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { viewModel.confirmImport(false) }, enabled = state.canImport) {
                            Text("Reemplazar", color = NeonAmber)
                        }
                        TextButton(onClick = { viewModel.confirmImport(true) }, enabled = state.canImport) {
                            Text("Fusionar", color = NeonCyan)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Formato recomendado: .gnx cifrado. Los JSON sin cifrar solo se aceptan para migrar configuraciones antiguas.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
