package com.ghostnexora.vpn.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    val selectedProfileId: StateFlow<String?> = _selectedProfileId.asStateFlow()

    val profiles: StateFlow<List<VpnProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyLogs: StateFlow<List<LogEntry>> = combine(
        repository.allLogs,
        _selectedProfileId
    ) { allLogs, profileId ->
        val filtered = if (profileId == null) {
            allLogs
        } else {
            allLogs.filter { it.profileId == profileId }
        }
        filtered.sortedWith(
            compareByDescending<LogEntry> { it.timestamp }.thenByDescending { it.id }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedProfileName: StateFlow<String> = combine(
        profiles,
        selectedProfileId
    ) { allProfiles, profileId ->
        when (profileId) {
            null -> "Todos los perfiles"
            else -> allProfiles.firstOrNull { it.id == profileId }?.name?.ifBlank { "Sin nombre" }
                ?: "Perfil eliminado"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Todos los perfiles")

    fun selectProfile(profileId: String?) {
        _selectedProfileId.value = profileId
    }

    fun clearFilter() {
        _selectedProfileId.update { null }
    }
}
