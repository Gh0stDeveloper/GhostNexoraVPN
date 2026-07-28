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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostnexora.vpn.data.model.VpnProfile
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
import com.ghostnexora.vpn.util.ProfileTechnicalSummaries
import com.ghostnexora.vpn.util.toReadableDate

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
    val snackbarHostState = remember { SnackbarHostState() }
    var profileToDelete by remember { mutableStateOf<VpnProfile?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(uiState.profileToDelete) {
        profileToDelete = uiState.profileToDelete
    }

    profileToDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Eliminar perfil") },
            text = { Text("Se eliminará '${pending.name}'. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmDelete()
                        profileToDelete = null
                    },
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
                        Text("Buscar, duplicar y clasificar configuraciones", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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
            FloatingActionButton(onClick = onCreateNew, containerColor = NeonCyan, contentColor = TextOnAccent) {
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
                                { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
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
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val summary = remember(profile) { ProfileTechnicalSummaries.from(profile) }
    GhostCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
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
                        if (isActive) Icons.Filled.CheckCircle else Icons.Filled.VpnKey,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name.ifEmpty { "Perfil sin nombre" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) Text("ACTIVO", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
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
                        Text(profile.tags.take(4).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
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
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicar", tint = TextSecondary)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = NeonCyan)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = Color.Red.copy(alpha = 0.7f))
                }
            }
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
            Text("No tienes perfiles aún", style = MaterialTheme.typography.headlineSmall, color = TextSecondary)
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