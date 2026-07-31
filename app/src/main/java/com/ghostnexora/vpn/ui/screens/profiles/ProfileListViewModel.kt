package com.ghostnexora.vpn.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.security.Gnx3ProtectionMode
import com.ghostnexora.vpn.util.IndividualExportOptions
import com.ghostnexora.vpn.util.JsonManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProfileListViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val jsonManager: JsonManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(ProfileFilter.ALL)
    val activeFilter: StateFlow<ProfileFilter> = _activeFilter.asStateFlow()

    private val _uiState = MutableStateFlow(ProfileListUiState())
    val uiState: StateFlow<ProfileListUiState> = _uiState.asStateFlow()

    val activeProfileId: StateFlow<String> = repository.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    @OptIn(ExperimentalCoroutinesApi::class)
    val profiles: StateFlow<List<VpnProfile>> = combine(
        _searchQuery,
        _activeFilter
    ) { query, filter -> query to filter }
        .flatMapLatest { (query, filter) ->
            when {
                query.isNotBlank() -> repository.searchProfiles(query.trim())
                filter == ProfileFilter.FAVORITES -> repository.favoriteProfiles
                filter == ProfileFilter.ENABLED -> repository.enabledProfiles
                else -> repository.allProfiles
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun setFilter(filter: ProfileFilter) {
        _activeFilter.value = filter
    }

    fun selectActiveProfile(profileId: String) {
        viewModelScope.launch {
            repository.setActiveProfileId(profileId)
            _uiState.update { it.copy(snackbarMessage = "Perfil seleccionado") }
        }
    }

    fun toggleFavorite(profile: VpnProfile) {
        viewModelScope.launch {
            repository.setFavorite(profile.id, !profile.isFavorite)
            _uiState.update {
                it.copy(snackbarMessage = if (profile.isFavorite) "Eliminado de favoritos" else "Añadido a favoritos")
            }
        }
    }

    fun duplicateProfile(profile: VpnProfile) {
        if (profile.isLocked) {
            _uiState.update {
                it.copy(snackbarMessage = "La configuración bloqueada no se puede duplicar")
            }
            return
        }
        viewModelScope.launch {
            val copy = profile.copy(
                id = UUID.randomUUID().toString(),
                name = uniqueCopyName(profile.name),
                isFavorite = false,
                lastUsed = "",
                createdAt = System.currentTimeMillis()
            )
            repository.saveProfile(copy)
            _uiState.update { it.copy(snackbarMessage = "Perfil duplicado como \"${copy.name}\"") }
        }
    }

    fun requestDelete(profile: VpnProfile) {
        _uiState.update { it.copy(profileToDelete = profile) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(profileToDelete = null) }
    }

    fun confirmDelete() {
        val profile = _uiState.value.profileToDelete ?: return
        viewModelScope.launch {
            repository.deleteProfile(profile)
            if (activeProfileId.value == profile.id) repository.clearActiveProfile()
            _uiState.update {
                it.copy(
                    profileToDelete = null,
                    snackbarMessage = "\"${profile.name}\" eliminado"
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun openIndividualExport(profile: VpnProfile) {
        if (profile.isLocked) {
            _uiState.update {
                it.copy(snackbarMessage = "El creador bloqueó la edición y reexportación")
            }
            return
        }
        _uiState.update {
            it.copy(
                exportProfile = profile,
                exportLocked = true,
                exportUsePassword = false,
                exportPassword = "",
                exportPasswordConfirmation = "",
                exportNoteHtml = profile.displayNoteHtml,
                exportError = null
            )
        }
    }

    fun dismissIndividualExport() {
        _uiState.update {
            it.copy(
                exportProfile = null,
                exportPassword = "",
                exportPasswordConfirmation = "",
                exportError = null
            )
        }
    }

    fun setExportLocked(value: Boolean) {
        _uiState.update { it.copy(exportLocked = value, exportError = null) }
    }

    fun setExportUsePassword(value: Boolean) {
        _uiState.update {
            it.copy(
                exportUsePassword = value,
                exportPassword = if (value) it.exportPassword else "",
                exportPasswordConfirmation = if (value) it.exportPasswordConfirmation else "",
                exportError = null
            )
        }
    }

    fun setExportPassword(value: String) {
        _uiState.update { it.copy(exportPassword = value.take(256), exportError = null) }
    }

    fun setExportPasswordConfirmation(value: String) {
        _uiState.update {
            it.copy(exportPasswordConfirmation = value.take(256), exportError = null)
        }
    }

    fun setExportNoteHtml(value: String) {
        _uiState.update { it.copy(exportNoteHtml = value.take(64 * 1024), exportError = null) }
    }

    fun exportIndividual(share: Boolean) {
        val state = _uiState.value
        val profile = state.exportProfile ?: return
        if (state.exportUsePassword) {
            when {
                state.exportPassword.length < 10 -> {
                    _uiState.update {
                        it.copy(exportError = "La contraseña debe tener al menos 10 caracteres")
                    }
                    return
                }
                state.exportPassword != state.exportPasswordConfirmation -> {
                    _uiState.update { it.copy(exportError = "Las contraseñas no coinciden") }
                    return
                }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(exportInProgress = true, exportError = null) }
            val password = state.exportPassword
                .takeIf { state.exportUsePassword }
                ?.toCharArray()
            try {
                val options = IndividualExportOptions(
                    locked = state.exportLocked,
                    noteHtml = state.exportNoteHtml,
                    protectionMode = if (state.exportUsePassword) {
                        Gnx3ProtectionMode.PASSWORD
                    } else {
                        Gnx3ProtectionMode.APP_MANAGED
                    },
                    password = password
                )
                if (share) {
                    val file = jsonManager.exportIndividualToCache(profile, options)
                    _uiState.update {
                        it.copy(
                            exportInProgress = false,
                            exportProfile = null,
                            exportPassword = "",
                            exportPasswordConfirmation = "",
                            shareFilePath = file.absolutePath
                        )
                    }
                } else {
                    val uri = jsonManager.exportIndividualToDownloads(profile, options)
                    check(uri != null) { "No se pudo guardar el archivo GNX3" }
                    _uiState.update {
                        it.copy(
                            exportInProgress = false,
                            exportProfile = null,
                            exportPassword = "",
                            exportPasswordConfirmation = "",
                            snackbarMessage = "Configuración individual GNX3 guardada"
                        )
                    }
                }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        exportInProgress = false,
                        exportError = error.message?.take(160)
                            ?: "No se pudo crear la configuración GNX3"
                    )
                }
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    fun consumeSharedFile() {
        _uiState.update { it.copy(shareFilePath = null) }
    }

    private fun uniqueCopyName(name: String): String {
        val clean = name.trim().ifBlank { "Perfil" }
        return if (clean.endsWith("(copia)", ignoreCase = true)) "$clean 2" else "$clean (copia)"
    }
}

data class ProfileListUiState(
    val profileToDelete: VpnProfile? = null,
    val snackbarMessage: String? = null,
    val exportProfile: VpnProfile? = null,
    val exportLocked: Boolean = true,
    val exportUsePassword: Boolean = false,
    val exportPassword: String = "",
    val exportPasswordConfirmation: String = "",
    val exportNoteHtml: String = "",
    val exportInProgress: Boolean = false,
    val exportError: String? = null,
    val shareFilePath: String? = null
) {
    val exportPasswordValid: Boolean
        get() = !exportUsePassword ||
            (
                exportPassword.length >= 10 &&
                    exportPassword == exportPasswordConfirmation
                )
}

enum class ProfileFilter(val label: String) {
    ALL("Todos"),
    FAVORITES("Favoritos"),
    ENABLED("Activos")
}
