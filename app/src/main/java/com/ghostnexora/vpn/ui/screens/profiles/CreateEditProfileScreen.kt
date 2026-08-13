@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.profiles

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.TlsVerificationMode
import com.ghostnexora.vpn.ui.components.HtmlNoteDialog
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostButton
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.TextOnAccent
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.util.PayloadGenerator
import com.ghostnexora.vpn.util.PayloadUseCase

@Composable
fun CreateEditProfileScreen(
    profileId: String? = null,
    onBack: () -> Unit,
    viewModel: CreateEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNotePreview by remember { mutableStateOf(false) }

    if (showNotePreview) {
        HtmlNoteDialog(
            title = "Vista previa de la nota",
            html = state.noteHtml,
            onDismiss = { showNotePreview = false }
        )
    }

    LaunchedEffect(profileId) {
        viewModel.loadProfile(profileId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.isSaving) {
                        Text(if (state.isSaving) "Guardando" else "Guardar", color = NeonAmber)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXL)
        ) {
            GhostCard(borderColor = BorderSubtle, contentPadding = PaddingValues(Dimens.SpaceMD)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                    Text("Servidor y protocolo", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("Nombre del perfil") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.nameError != null,
                        supportingText = { state.nameError?.let { Text(it, color = Color.Red) } }
                    )

                    ModeSelector(
                        selectedMode = state.selectedMode,
                        onModeSelected = viewModel::onConnectionModeChange,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ModeInfoPanel(mode = state.selectedMode)

                    OutlinedTextField(
                        value = state.host,
                        onValueChange = viewModel::onHostChange,
                        label = { Text("Servidor / Host") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.hostError != null,
                        supportingText = { state.hostError?.let { Text(it, color = Color.Red) } }
                    )

                    OutlinedTextField(
                        value = state.port,
                        onValueChange = viewModel::onPortChange,
                        label = { Text("Puerto") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.portError != null,
                        supportingText = { state.portError?.let { Text(it, color = Color.Red) } }
                    )

                    ProtocolCredentialFields(state = state, viewModel = viewModel)
                }
            }

            GhostCard(borderColor = BorderSubtle, contentPadding = PaddingValues(Dimens.SpaceMD)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                    Text("Transporte y seguridad", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

                    if (state.selectedMode == ConnectionMode.V2RAY) {
                        SwitchRow(
                            title = "Usar TLS",
                            subtitle = "Verifica el certificado del servidor; no se permite insecure por defecto.",
                            checked = state.sslEnabled,
                            onCheckedChange = viewModel::onSslChange
                        )
                    }

                    if (state.selectedMode.requiresSni || (state.selectedMode == ConnectionMode.V2RAY && state.sslEnabled)) {
                        OutlinedTextField(
                            value = state.sni,
                            onValueChange = viewModel::onSniChange,
                            label = { Text("SNI / Host TLS") },
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                Text(
                                    if (
                                        state.selectedMode.isSsh &&
                                        state.selectedTlsVerificationMode == TlsVerificationMode.CUSTOM_SNI
                                    ) {
                                        "Se enviará este SNI aunque el certificado use otra CA o identidad."
                                    } else {
                                        "Debe coincidir con un nombre válido del certificado TLS."
                                    }
                                )
                            }
                        )
                    }

                    if (state.selectedMode.isSsh && state.selectedMode.usesTls) {
                        SwitchRow(
                            title = "Compatibilidad SNI tipo HTTP Custom",
                            subtitle = if (
                                state.selectedTlsVerificationMode == TlsVerificationMode.CUSTOM_SNI
                            ) {
                                "Activa: acepta certificados privados/autofirmados y SNI/SAN distintos. Úsala solo con perfiles confiables; SSH conserva la huella del servidor."
                            } else {
                                "Desactivada: TLS estricto exige que el certificado pertenezca al SNI."
                            },
                            checked = state.selectedTlsVerificationMode == TlsVerificationMode.CUSTOM_SNI,
                            onCheckedChange = viewModel::onCustomSniCompatibilityChange
                        )
                    }

                    if (state.selectedMode.requiresPayload) {
                        AdvancedPayloadEditor(
                            payload = state.payload,
                            host = state.host,
                            port = state.port.toIntOrNull() ?: 443,
                            sni = state.sni,
                            proxyHost = state.proxyHost,
                            proxyPort = state.proxyPort.toIntOrNull() ?: 0,
                            onPayloadChange = viewModel::onPayloadChange
                        )
                    }

                    if (state.selectedMode == ConnectionMode.V2RAY) {
                        OutlinedTextField(
                            value = state.payload,
                            onValueChange = viewModel::onPayloadChange,
                            label = { Text("Parámetros V2Ray / Xray") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            supportingText = {
                                Text("Ejemplo: net=ws | host=cdn.example.com | path=/ws | security=tls | fp=chrome")
                            }
                        )
                    }

                    if (state.selectedMode.requiresProxy) {
                        OutlinedTextField(
                            value = state.proxyHost,
                            onValueChange = viewModel::onProxyHostChange,
                            label = { Text("Proxy Host") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                            OutlinedTextField(
                                value = state.proxyPort,
                                onValueChange = viewModel::onProxyPortChange,
                                label = { Text("Puerto") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            ProxyTypeSelector(
                                selected = state.proxyType,
                                onSelected = viewModel::onProxyTypeChange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    SwitchRow(
                        title = "Perfil habilitado",
                        subtitle = "Permite seleccionar y usar este perfil para conectar.",
                        checked = state.enabled,
                        onCheckedChange = viewModel::onEnabledChange
                    )
                }
            }

            GhostCard(borderColor = BorderSubtle, contentPadding = PaddingValues(Dimens.SpaceMD)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                    Text("Metadatos", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    OutlinedTextField(
                        value = state.tags,
                        onValueChange = viewModel::onTagsChange,
                        label = { Text("Tags") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Para V2Ray usa vmess o vless para indicar el protocolo.") }
                    )
                    OutlinedTextField(
                        value = state.noteHtml,
                        onValueChange = viewModel::onNoteHtmlChange,
                        label = { Text("Nota HTML/CSS") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        supportingText = {
                            Text("Texto, estilos, tablas y enlaces de contacto seguros.")
                        }
                    )
                    TextButton(
                        onClick = { showNotePreview = true },
                        enabled = state.noteHtml.isNotBlank()
                    ) {
                        Text("Vista previa HTML", color = NeonCyan)
                    }
                }
            }

            GhostButton(
                text = if (state.isSaving) "Guardando..." else if (state.isEditMode) "Guardar cambios" else "Crear perfil",
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving,
                containerColor = NeonAmber,
                contentColor = TextOnAccent
            )

            Spacer(Modifier.height(Dimens.Space3XL))
        }
    }
}

@Composable
private fun ProtocolCredentialFields(
    state: CreateEditUiState,
    viewModel: CreateEditViewModel
) {
    val mode = state.selectedMode

    if (mode.isSsh || mode == ConnectionMode.V2RAY) {
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = {
                Text(if (mode == ConnectionMode.V2RAY) "UUID / User ID" else "Usuario SSH")
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (mode.isSsh || mode == ConnectionMode.TROJAN || mode == ConnectionMode.UDP) {
        val label = when (mode) {
            ConnectionMode.TROJAN -> "Contraseña Trojan"
            ConnectionMode.UDP -> "Auth / Contraseña Hysteria2"
            else -> "Contraseña SSH"
        }
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = viewModel::togglePasswordVisible) {
                    Icon(
                        if (state.passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (state.passwordVisible) "Ocultar" else "Mostrar"
                    )
                }
            }
        )
    }
}

@Composable
private fun ModeSelector(
    selectedMode: ConnectionMode,
    onModeSelected: (ConnectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedMode.label, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ConnectionMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(mode.label)
                            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    },
                    onClick = {
                        expanded = false
                        onModeSelected(mode)
                    }
                )
            }
        }
    }
}

@Composable
private fun ModeInfoPanel(mode: ConnectionMode) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonCyan.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(Dimens.SpaceMD),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
    ) {
        Text(mode.label, style = MaterialTheme.typography.titleSmall, color = NeonCyan)
        Text(mode.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            "Requiere: ${mode.requiredFields.joinToString(" · ")}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun ProxyTypeSelector(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.ifBlank { "HTTP" }, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("http", "socks5").forEach { type ->
                DropdownMenuItem(
                    text = { Text(if (type == "http") "HTTP CONNECT" else "SOCKS5") },
                    onClick = {
                        expanded = false
                        onSelected(type)
                    }
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PayloadPresetPanel(
    host: String,
    port: Int,
    sni: String,
    onUsePayload: (String) -> Unit
) {
    var preset by remember { mutableStateOf(PayloadUseCase.BROWSING) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonCyan.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .padding(Dimens.SpaceMD),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
    ) {
        Text("Plantillas de payload", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
        Text(
            "Generan una base editable; el servidor o proxy debe ser compatible con la estrategia de inyección elegida.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
            TextButton(onClick = {
                preset = PayloadUseCase.BROWSING
                onUsePayload(PayloadGenerator.generate(PayloadUseCase.BROWSING, host, port, sni = sni))
            }) { Text("Navegación") }
            TextButton(onClick = {
                preset = PayloadUseCase.STREAMING
                onUsePayload(PayloadGenerator.generate(PayloadUseCase.STREAMING, host, port, sni = sni))
            }) { Text("Streaming") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
            TextButton(onClick = {
                preset = PayloadUseCase.GAMING
                onUsePayload(PayloadGenerator.generate(PayloadUseCase.GAMING, host, port, sni = sni))
            }) { Text("Gaming") }
            TextButton(onClick = {
                preset = PayloadUseCase.CUSTOM
                onUsePayload(PayloadGenerator.generate(PayloadUseCase.CUSTOM, host, port, sni = sni))
            }) { Text("Custom") }
        }

        Text("Preset actual: ${preset.label}", color = NeonAmber, style = MaterialTheme.typography.labelSmall)
    }
}
