@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.profiles

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.ui.components.HtmlNoteDialog
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostButton
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.MonoStyle
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.SurfaceVariant
import com.ghostnexora.vpn.ui.theme.TextOnAccent
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary
import com.ghostnexora.vpn.util.JsonManager
import com.ghostnexora.vpn.util.ProfileTechnicalSummaries
import com.ghostnexora.vpn.util.shareFile
import com.ghostnexora.vpn.util.toReadableDate
import java.io.File

@Composable
fun ProfileListScreen(
    onBack: () -> Unit,
    onCreateNew: () -> Unit,
    onEditProfile: (String) -> Unit,
    viewModel: ProfileListViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val filter by viewModel.activeFilter.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var noteToShow by remember { mutableStateOf<VpnProfile?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(uiState.shareFilePath) {
        uiState.shareFilePath?.let { path ->
            runCatching {
                context.shareFile(File(path), JsonManager.MIME_GNX)
            }.onFailure {
                snackbarHostState.showSnackbar("No se pudo abrir el menú para compartir")
            }
            viewModel.consumeSharedFile()
        }
    }

    noteToShow?.let { profile ->
        HtmlNoteDialog(
            title = profile.name.ifBlank { "Nota del creador" },
            html = profile.displayNoteHtml,
            onDismiss = { noteToShow = null }
        )
    }

    uiState.exportProfile?.let { profile ->
        IndividualExportDialog(
            profile = profile,
            state = uiState,
            onDismiss = viewModel::dismissIndividualExport,
            onLockedChange = viewModel::setExportLocked,
            onUsePasswordChange = viewModel::setExportUsePassword,
            onPasswordChange = viewModel::setExportPassword,
            onPasswordConfirmationChange = viewModel::setExportPasswordConfirmation,
            onNoteChange = viewModel::setExportNoteHtml,
            onSave = { viewModel.exportIndividual(share = false) },
            onShare = { viewModel.exportIndividual(share = true) }
        )
    }

    uiState.profileToDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Eliminar perfil") },
            text = { Text("Se eliminará '${pending.name}'. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mis perfiles")
                        Text(
                            "Buscar, exportar y clasificar configuraciones",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNew,
                containerColor = NeonCyan,
                contentColor = TextOnAccent
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo perfil")
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
                .padding(horizontal = Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Host, nombre, protocolo o etiqueta") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
                ) {
                    ProfileFilter.entries.forEach { option ->
                        AssistChip(
                            onClick = { viewModel.setFilter(option) },
                            label = { Text(option.label) },
                            leadingIcon = if (filter == option) {
                                {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            if (profiles.isEmpty()) {
                item {
                    if (query.isBlank() && filter == ProfileFilter.ALL) {
                        EmptyProfilesState(onCreateNew)
                    } else {
                        EmptyFilteredState(onClear = {
                            viewModel.clearSearch()
                            viewModel.setFilter(ProfileFilter.ALL)
                        })
                    }
                }
            } else {
                items(items = profiles, key = VpnProfile::id) { profile ->
                    ProfileItem(
                        profile = profile,
                        isActive = profile.id == activeProfileId,
                        onSelect = { viewModel.selectActiveProfile(profile.id) },
                        onFavorite = { viewModel.toggleFavorite(profile) },
                        onDuplicate = { viewModel.duplicateProfile(profile) },
                        onExport = { viewModel.openIndividualExport(profile) },
                        onViewNote = { noteToShow = profile },
                        onEdit = { onEditProfile(profile.id) },
                        onDelete = { viewModel.requestDelete(profile) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(Dimens.Space3XL)) }
        }
    }
}

@Composable
private fun ProfileItem(
    profile: VpnProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onViewNote: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val summary = remember(profile) { ProfileTechnicalSummaries.from(profile) }
    GhostCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        backgroundColor = if (isActive) NeonCyan.copy(alpha = 0.08f) else SurfaceVariant,
        borderColor = if (isActive) NeonCyan else BorderSubtle,
        contentPadding = PaddingValues(Dimens.SpaceMD)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NeonCyan.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when {
                            profile.isLocked -> Icons.Filled.Lock
                            isActive -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.VpnKey
                        },
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.name.ifEmpty { "Perfil sin nombre" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) {
                            Text("ACTIVO", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        }
                        if (profile.isLocked) {
                            Text("BLOQUEADO", style = MaterialTheme.typography.labelSmall, color = NeonAmber)
                        }
                    }
                    Text(
                        text = "${summary.server} · ${summary.protocol} · ${summary.transport}",
                        style = MonoStyle.copy(color = TextSecondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${summary.security}${summary.sni.takeIf(String::isNotBlank)?.let { " · SNI $it" }.orEmpty()}",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profile.tags.isNotEmpty()) {
                        Text(
                            profile.tags.take(4).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                    Text(
                        text = "Creado: ${profile.createdAt.toReadableDate()}${profile.lastUsed.takeIf(String::isNotBlank)?.let { " · Usado: $it" }.orEmpty()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    summary.warnings.firstOrNull()?.let {
                        Text(it, color = NeonAmber, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (profile.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (profile.isFavorite) NeonAmber else TextTertiary
                    )
                }
                if (profile.displayNoteHtml.isNotBlank()) {
                    IconButton(onClick = onViewNote) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = "Ver nota del creador",
                            tint = NeonAmber
                        )
                    }
                }
                IconButton(onClick = onExport, enabled = !profile.isLocked) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Exportar configuración individual",
                        tint = if (profile.isLocked) TextTertiary else NeonCyan
                    )
                }
                IconButton(onClick = onDuplicate, enabled = !profile.isLocked) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Duplicar",
                        tint = if (profile.isLocked) TextTertiary else TextSecondary
                    )
                }
                IconButton(onClick = onEdit, enabled = !profile.isLocked) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Editar",
                        tint = if (profile.isLocked) TextTertiary else NeonCyan
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar",
                        tint = Color.Red.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun IndividualExportDialog(
    profile: VpnProfile,
    state: ProfileListUiState,
    onDismiss: () -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onUsePasswordChange: (Boolean) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmationChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    var showPreview by remember(profile.id) { mutableStateOf(false) }
    if (showPreview) {
        HtmlNoteDialog(
            title = "Vista previa de la nota",
            html = state.exportNoteHtml,
            onDismiss = { showPreview = false }
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (!state.exportInProgress && !showPreview) onDismiss()
        },
        title = {
            Column {
                Text("Exportar archivo GNX3")
                Text(
                    profile.name.ifBlank { "Perfil sin nombre" },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
            ) {
                Text(
                    "Permisos después de importar",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall
                )
                ExportChoiceRow(
                    selected = !state.exportLocked,
                    title = "Configuración editable",
                    description = "El usuario podrá ver y modificar host, SSH, SNI, proxy y payload.",
                    enabled = !state.exportInProgress,
                    onClick = { onLockedChange(false) }
                )
                ExportChoiceRow(
                    selected = state.exportLocked,
                    title = "Configuración bloqueada",
                    description = "Oculta los parámetros, impide editar, duplicar y volver a exportar.",
                    enabled = !state.exportInProgress,
                    onClick = { onLockedChange(true) }
                )

                Text(
                    "Protección del archivo",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall
                )
                ExportChoiceRow(
                    selected = !state.exportUsePassword,
                    title = "Cifrado automático por la aplicación",
                    description = "Se importa sin escribir contraseña en APK oficiales firmadas por el desarrollador.",
                    enabled = !state.exportInProgress,
                    onClick = { onUsePasswordChange(false) }
                )
                ExportChoiceRow(
                    selected = state.exportUsePassword,
                    title = "Contraseña personalizada",
                    description = "El receptor deberá introducir exactamente esta contraseña para importar.",
                    enabled = !state.exportInProgress,
                    onClick = { onUsePasswordChange(true) }
                )

                if (state.exportUsePassword) {
                    OutlinedTextField(
                        value = state.exportPassword,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Contraseña de importación") },
                        supportingText = { Text("Mínimo 10 caracteres") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !state.exportInProgress
                    )
                    OutlinedTextField(
                        value = state.exportPasswordConfirmation,
                        onValueChange = onPasswordConfirmationChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirmar contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = state.exportPasswordConfirmation.isNotBlank() &&
                            state.exportPassword != state.exportPasswordConfirmation,
                        enabled = !state.exportInProgress
                    )
                }

                OutlinedTextField(
                    value = state.exportNoteHtml,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nota del creador · texto o HTML/CSS") },
                    supportingText = {
                        Text("Se mostrará completa en la pantalla Inicio del perfil importado.")
                    },
                    minLines = 5,
                    maxLines = 10,
                    enabled = !state.exportInProgress
                )
                TextButton(
                    onClick = { showPreview = true },
                    enabled = state.exportNoteHtml.isNotBlank() && !state.exportInProgress
                ) {
                    Text("Vista previa de la nota", color = NeonCyan)
                }

                Text(
                    "Resultado: ${if (state.exportLocked) "bloqueada" else "editable"} · " +
                        if (state.exportUsePassword) "protegida con contraseña" else "cifrado automático",
                    color = NeonAmber,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "GNX3 usa AES-GCM autenticado, claves derivadas y un nonce/IV aleatorio nuevo en cada exportación. El IV no se reutiliza ni se guarda como constante fija.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                state.exportError?.let {
                    Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
                if (state.exportInProgress) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onShare,
                enabled = state.exportPasswordValid && !state.exportInProgress
            ) {
                Text("Compartir")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onSave,
                    enabled = state.exportPasswordValid && !state.exportInProgress
                ) {
                    Text("Guardar")
                }
                TextButton(onClick = onDismiss, enabled = !state.exportInProgress) {
                    Text("Cancelar")
                }
            }
        }
    )
}

@Composable
private fun ExportChoiceRow(
    selected: Boolean,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(description, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyProfilesState(onCreateNew: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space4XL),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXL)
        ) {
            Icon(Icons.Filled.VpnKey, null, tint = TextTertiary, modifier = Modifier.size(72.dp))
            Text(
                "No tienes perfiles aún",
                style = MaterialTheme.typography.headlineSmall,
                color = TextSecondary
            )
            Text(
                text = "Crea tu primer perfil para comenzar\na usar la VPN",
                style = MaterialTheme.typography.bodyLarge,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
            GhostButton(
                text = "Crear primer perfil",
                onClick = onCreateNew,
                containerColor = NeonCyan,
                contentColor = TextOnAccent
            )
        }
    }
}

@Composable
private fun EmptyFilteredState(onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space4XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
    ) {
        Text("No hay perfiles que coincidan", color = TextSecondary)
        TextButton(onClick = onClear) { Text("Limpiar búsqueda y filtros") }
    }
}
