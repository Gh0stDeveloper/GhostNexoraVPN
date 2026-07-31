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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.ghostnexora.vpn.ui.components.HtmlNoteDialog
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
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
    var notePreview by remember { mutableStateOf<Pair<String, String>?>(null) }
    val qrScanner = remember(context) { GmsBarcodeScanning.getClient(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onFilePicked)
    }

    notePreview?.let { (title, html) ->
        HtmlNoteDialog(
            title = title,
            html = html,
            onDismiss = { notePreview = null }
        )
    }

    LaunchedEffect(state.importSuccess) {
        if (state.importSuccess) {
            val skipped = state.skippedDuplicateCount
            snackbar.showSnackbar(
                if (skipped > 0) {
                    "${state.importedCount} perfil(es) importado(s) · $skipped duplicado(s) omitido(s)"
                } else {
                    "${state.importedCount} perfil(es) importado(s)"
                }
            )
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
                    backgroundColor = NeonCyan.copy(alpha = 0.10f),
                    borderColor = NeonCyan,
                    contentPadding = PaddingValues(Dimens.SpaceMD)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                            Icon(Icons.Filled.Lock, null, tint = NeonCyan)
                            Text("Importación protegida y verificable", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Admite perfiles individuales GNX3, GNX2, vmess://, vless://, trojan://, hysteria2://, hy2://, ssh://, JSON Xray, QR, portapapeles y archivos. Siempre muestra una vista previa antes de guardar.",
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
                    label = { Text("Contraseña del archivo (si tiene)") },
                    supportingText = { Text("GNX3 administrado por la app no requiere escribir contraseña.") },
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
                    text = "Escanear código QR",
                    onClick = {
                        qrScanner.startScan()
                            .addOnSuccessListener { barcode ->
                                val raw = barcode.rawValue.orEmpty().trim()
                                if (raw.isNotEmpty()) viewModel.onTextProvided(raw, "QR")
                                else scope.launch { snackbar.showSnackbar("El QR no contiene una configuración legible") }
                            }
                            .addOnFailureListener { error ->
                                scope.launch {
                                    snackbar.showSnackbar(error.message?.take(120) ?: "No se pudo abrir el escáner QR")
                                }
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading,
                    containerColor = NeonGreen,
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

            if (
                state.passwordRequired &&
                (state.selectedUri != null || state.selectedRawText != null)
            ) {
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
                            if (state.duplicateCount > 0) {
                                Text(
                                    "Al fusionar se omitirán ${state.duplicateCount} configuración(es) idéntica(s). Reemplazar conserva una sola copia de cada configuración importada.",
                                    color = NeonAmber,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (state.previewProfiles.isNotEmpty()) {
                item { Text("Vista previa técnica (${state.previewProfiles.size})", color = TextPrimary, style = MaterialTheme.typography.titleMedium) }
                itemsIndexed(state.previewProfiles.take(12), key = { _, profile -> profile.id }) { index, profile ->
                    val summary = state.technicalSummaries.getOrNull(index)
                    GhostCard(backgroundColor = SurfaceVariant, borderColor = BorderNormal) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
                            Text(profile.name.ifBlank { "Sin nombre" }, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("${summary?.protocol ?: profile.connectionModeLabel} · ${summary?.server ?: profile.serverAddress}", color = NeonCyan, style = MaterialTheme.typography.bodySmall)
                            if (profile.isLocked) {
                                Text(
                                    "El creador bloqueó la visualización, edición, duplicación y reexportación de los parámetros.",
                                    color = NeonAmber,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else summary?.let {
                                TechnicalRow("Transporte", it.transport)
                                TechnicalRow("Seguridad", it.security)
                                if (it.sni.isNotBlank()) TechnicalRow("SNI", it.sni)
                                if (it.hostHeader.isNotBlank()) TechnicalRow("Host", it.hostHeader)
                                if (it.path.isNotBlank()) TechnicalRow("Path", it.path)
                                if (it.serviceName.isNotBlank()) TechnicalRow("Service", it.serviceName)
                                if (it.proxy.isNotBlank()) TechnicalRow("Proxy", it.proxy)
                                it.warnings.forEach { warning ->
                                    Text(warning, color = NeonAmber, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (profile.displayNoteHtml.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        notePreview = (
                                            profile.name.ifBlank { "Nota del creador" }
                                            ) to profile.displayNoteHtml
                                    }
                                ) {
                                    Text("Ver nota HTML del creador", color = NeonCyan)
                                }
                            }
                        }
                    }
                }
                if (state.previewProfiles.size > 12) {
                    item {
                        Text(
                            "Se muestran 12 de ${state.previewProfiles.size} perfiles. Todos se validarán al importar.",
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { viewModel.confirmImport(false) }, enabled = state.canImport) {
                            Text("Reemplazar", color = NeonAmber)
                        }
                        TextButton(onClick = { viewModel.confirmImport(true) }, enabled = state.canImport) {
                            Text("Fusionar sin duplicados", color = NeonCyan)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Formato recomendado: GNX3 individual o GNX2 de respaldo. Ninguna protección cliente impide de forma absoluta la extracción por un usuario que controla el dispositivo.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun TechnicalRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}
