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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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

@Composable
fun CreateEditProfileScreen(
    profileId: String? = null,
    onBack: () -> Unit,
    viewModel: CreateEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
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
                .verticalScroll(scrollState)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXL)
        ) {
            GhostCard(borderColor = BorderSubtle, contentPadding = PaddingValues(Dimens.SpaceMD)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("Nombre del Perfil") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.nameError != null,
                        supportingText = { state.nameError?.let { Text(it, color = Color.Red) } }
                    )

                    OutlinedTextField(
                        value = state.host,
                        onValueChange = viewModel::onHostChange,
                        label = { Text("Servidor (Host)") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.hostError != null,
                        supportingText = { state.hostError?.let { Text(it, color = Color.Red) } }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                        OutlinedTextField(
                            value = state.port,
                            onValueChange = viewModel::onPortChange,
                            label = { Text("Puerto") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = state.portError != null,
                            supportingText = { state.portError?.let { Text(it, color = Color.Red) } }
                        )

                        ModeSelector(
                            selectedMode = state.selectedMode,
                            onModeSelected = viewModel::onConnectionModeChange,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    ModeInfoCard(mode = state.selectedMode)

                    OutlinedTextField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChange,
                        label = { Text("Usuario SSH") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text("Contraseña SSH") },
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

            GhostCard(borderColor = BorderSubtle, contentPadding = PaddingValues(Dimens.SpaceMD)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                    Text("Opciones del modo", style = MaterialTheme.typography.titleSmall, color = TextPrimary)

                    if (state.selectedMode.requiresSni) {
                        OutlinedTextField(
                            value = state.sni,
                            onValueChange = viewModel::onSniChange,
                            label = { Text("SNI / Host TLS") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (state.selectedMode.requiresPayload) {
                        OutlinedTextField(
                            value = state.payload,
                            onValueChange = viewModel::onPayloadChange,
                            label = { Text("Payload") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4
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
                                label = { Text("Proxy Puerto") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = state.proxyType,
                                onValueChange = viewModel::onProxyTypeChange,
                                label = { Text("Tipo") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    SwitchRow(
                        title = "Perfil habilitado",
                        checked = state.enabled,
                        onCheckedChange = viewModel::onEnabledChange
                    )
                }
            }

            GhostCard(borderColor = BorderSubtle, contentPadding = PaddingValues(Dimens.SpaceMD)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
                    OutlinedTextField(
                        value = state.tags,
                        onValueChange = viewModel::onTagsChange,
                        label = { Text("Tags (separados por coma)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::onNotesChange,
                        label = { Text("Notas") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }

            GhostButton(
                text = if (state.isSaving) "Guardando..." else if (state.isEditMode) "Guardar Cambios" else "Crear Perfil",
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
private fun ModeSelector(
    selectedMode: ConnectionMode,
    onModeSelected: (ConnectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedMode.label)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ConnectionMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(mode.label)
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (mode.supported) TextSecondary else NeonAmber
                            )
                        }
                    },
                    onClick = {
                        if (mode.supported) {
                            expanded = false
                            onModeSelected(mode)
                        }
                    },
                    enabled = mode.supported
                )
            }
        }
    }
}

@Composable
private fun ModeInfoCard(
    mode: ConnectionMode
) {
    GhostCard(borderColor = BorderSubtle, contentPadding = PaddingValues(Dimens.SpaceMD)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
            Text("Método seleccionado", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(mode.label, style = MaterialTheme.typography.titleMedium, color = NeonCyan)
            Text(mode.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(
                "Campos requeridos: ${mode.requiredFields.joinToString(" · ")}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            if (!mode.supported) {
                Text(
                    "Este método está documentado pero todavía no está habilitado en el motor actual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonAmber
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), color = TextPrimary)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
