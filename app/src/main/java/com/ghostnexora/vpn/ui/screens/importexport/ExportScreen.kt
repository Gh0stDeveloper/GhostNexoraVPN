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
import androidx.compose.material3.FilterChip
import com.ghostnexora.vpn.util.BundleProtection
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
import androidx.compose.ui.res.stringResource
import com.ghostnexora.vpn.R
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
    val exportableProfiles = profiles.filterNot { it.isLocked }
    val snackbar = remember { SnackbarHostState() }
    val manualSave = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.exportToUri(it, profiles) } }

    val exportMessage = stringResource(R.string.export_count, state.exportedCount)
    LaunchedEffect(state.exportSuccess) {
        if (state.exportSuccess) {
            snackbar.showSnackbar(exportMessage)
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
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
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
                            Text(stringResource(R.string.export_encrypted), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            stringResource(when (state.protection) {
                                BundleProtection.APP_MANAGED -> R.string.export_portable_detail
                                BundleProtection.PASSWORD -> R.string.export_password_detail
                                BundleProtection.DEVICE_BOUND -> R.string.export_device_detail
                            }),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (profiles.isEmpty()) {
                item { Text(stringResource(R.string.export_empty), color = TextSecondary) }
            } else {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                state.selectedIds.isEmpty() ->
                                    stringResource(R.string.export_all_count, exportableProfiles.size)
                                else ->
                                    stringResource(R.string.export_selected_count, state.selectedIds.size, exportableProfiles.size)
                            },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { viewModel.toggleSelectAll(profiles) }) {
                            Text(
                                if (
                                    exportableProfiles.isNotEmpty() &&
                                    state.selectedIds.size == exportableProfiles.size
                                ) {
                                    stringResource(R.string.export_deselect)
                                } else {
                                    stringResource(R.string.export_select)
                                },
                                color = NeonCyan
                            )
                        }
                    }
                }

                items(profiles, key = { it.id }) { profile ->
                    val selected = profile.id in state.selectedIds
                    GhostCard(
                        modifier = Modifier.clickable(
                            enabled = !profile.isLocked
                        ) { viewModel.toggleProfileSelection(profile.id) },
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
                                if (profile.isLocked) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        null,
                                        tint = NeonAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        null,
                                        tint = TextOnAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(profile.name.takeIf(String::isNotBlank) ?: stringResource(R.string.profile_unnamed), color = TextPrimary)
                                Text(
                                    if (profile.isLocked) {
                                        stringResource(R.string.export_locked)
                                    } else {
                                        "${profile.host}:${profile.port} · ${profile.connectionModeLabel}"
                                    },
                                    color = if (profile.isLocked) NeonAmber else TextTertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
                        BundleProtection.entries.forEach { mode ->
                            FilterChip(
                                selected = state.protection == mode,
                                onClick = { viewModel.setExportProtection(mode) },
                                label = {
                                    Text(stringResource(when (mode) {
                                        BundleProtection.APP_MANAGED -> R.string.export_portable
                                        BundleProtection.PASSWORD -> R.string.export_password
                                        BundleProtection.DEVICE_BOUND -> R.string.export_device
                                    }))
                                },
                                enabled = !state.isLoading
                            )
                        }
                    }
                }

                if (state.protection == BundleProtection.PASSWORD) {
                    item {
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = viewModel::setExportPassword,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.export_password_label)) },
                            supportingText = { Text(stringResource(R.string.export_password_rules)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = state.passwordConfirmation,
                            onValueChange = viewModel::setExportPasswordConfirmation,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.export_repeat)) },
                            isError = state.passwordConfirmation.isNotEmpty() && state.password != state.passwordConfirmation,
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    }

                }

                if (state.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                        GhostButton(
                            text = stringResource(R.string.export_save),
                            onClick = { viewModel.exportSelected(profiles) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = exportableProfiles.isNotEmpty() &&
                                state.passwordValid &&
                                !state.isLoading,
                            containerColor = NeonGreen,
                            contentColor = TextOnAccent
                        )
                        GhostButton(
                            text = stringResource(R.string.export_location),
                            onClick = { manualSave.launch("ghost_nexora_${System.currentTimeMillis()}.gnx") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = exportableProfiles.isNotEmpty() &&
                                state.passwordValid &&
                                !state.isLoading,
                            containerColor = NeonCyan,
                            contentColor = TextOnAccent
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
