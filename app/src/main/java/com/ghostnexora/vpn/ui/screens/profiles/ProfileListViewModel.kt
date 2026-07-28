package com.ghostnexora.vpn.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.repository.ProfileRepository
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
    private val repository: ProfileRepository
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

    private fun uniqueCopyName(name: String): String {
        val clean = name.trim().ifBlank { "Perfil" }
        return if (clean.endsWith("(copia)", ignoreCase = true)) "$clean 2" else "$clean (copia)"
    }
}

data class ProfileListUiState(
    val profileToDelete: VpnProfile? = null,
    val snackbarMessage: String? = null
)

enum class ProfileFilter(val label: String) {
    ALL("Todos"),
    FAVORITES("Favoritos"),
    ENABLED("Activos")
}