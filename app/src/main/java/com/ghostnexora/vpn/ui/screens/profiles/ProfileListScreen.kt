@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.profiles

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.ghostnexora.vpn.ui.theme.BorderNormal
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostButton
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.SurfaceVariant
import com.ghostnexora.vpn.ui.theme.TextOnAccent
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary
import com.ghostnexora.vpn.ui.theme.MonoStyle
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

    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            title = { Text("Eliminar Perfil") },
            text = {
                Text("¿Estás seguro de que deseas eliminar '${profileToDelete?.name}'?\nEsta acción no se puede deshacer.")
            },
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
                TextButton(onClick = { viewModel.dismissDelete() }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Mis Perfiles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNew, containerColor = NeonCyan, contentColor = TextOnAccent) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo Perfil")
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
        ) {
            if (profiles.isEmpty()) {
                item { EmptyProfilesState(onCreateNew) }
            } else {
                items(items = profiles, key = { it.id }) { profile ->
                    ProfileItem(
                        profile = profile,
                        isActive = profile.id == activeProfileId,
                        onSelect = { viewModel.selectActiveProfile(profile.id) },
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
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GhostCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        backgroundColor = if (isActive) NeonCyan.copy(alpha = 0.08f) else SurfaceVariant,
        borderColor = if (isActive) NeonCyan else BorderSubtle,
        contentPadding = PaddingValues(Dimens.SpaceMD)
    ) {
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
                if (isActive) {
                    Icon(Icons.Filled.CheckCircle, null, tint = NeonCyan, modifier = Modifier.size(28.dp))
                } else {
                    Icon(Icons.Filled.VpnKey, null, tint = NeonCyan, modifier = Modifier.size(28.dp))
                }
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
                    if (isActive) {
                        Text(
                            text = "ACTIVO",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }
                }
                Text(
                    text = "${profile.host}:${profile.port} • ${profile.connectionModeLabel}",
                    style = MonoStyle.copy(color = TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
                        profile.tags.take(3).forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
                Text(
                    text = "Creado: ${profile.createdAt.toReadableDate()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Space4XL),
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
                text = "Crear Primer Perfil",
                onClick = onCreateNew,
                containerColor = NeonCyan,
                contentColor = TextOnAccent
            )
        }
    }
}
