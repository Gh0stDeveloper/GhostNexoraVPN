package com.ghostnexora.vpn.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.util.PermissionHelper
import com.ghostnexora.vpn.util.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ProfileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ── UI State ──────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // ══════════════════════════════════════════════════════════════════════
    // INIT — Cargar preferencias actuales
    // ══════════════════════════════════════════════════════════════════════

    init {
        loadSettings()
        loadPermissionStatus()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                repository.autoReconnect,
                repository.floatingWindow,
                repository.notifications,
                repository.reconnectOnBoot,
                repository.logsMaxEntries
            ) { reconnect, floating, notifs, boot, maxLogs ->
                SettingsUiState(
                    autoReconnect     = reconnect,
                    floatingWindow    = floating,
                    notifications     = notifs,
                    reconnectOnBoot   = boot,
                    logsMaxEntries    = maxLogs,
                    permissionStatus  = PermissionHelper.permissionStatus(context)
                )
            }.collectLatest { state ->
                _uiState.value = state
            }
        }
    }

    private fun loadPermissionStatus() {
        _uiState.update {
            it.copy(permissionStatus = PermissionHelper.permissionStatus(context))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TOGGLE SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoReconnect(enabled) }
    }

    fun toggleAutoReconnect() {
        setAutoReconnect(!_uiState.value.autoReconnect)
    }

    fun setFloatingWindow(enabled: Boolean) {
        viewModelScope.launch { repository.setFloatingWindow(enabled) }
    }

    fun toggleFloatingWindow() {
        setFloatingWindow(!_uiState.value.floatingWindow)
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { repository.setNotifications(enabled) }
    }

    fun toggleNotifications() {
        setNotifications(!_uiState.value.notifications)
    }

    fun setReconnectOnBoot(enabled: Boolean) {
        viewModelScope.launch { repository.setReconnectOnBoot(enabled) }
    }

    fun toggleReconnectOnBoot() {
        setReconnectOnBoot(!_uiState.value.reconnectOnBoot)
    }

    fun setLogsMaxEntries(max: Int) {
        viewModelScope.launch {
            repository.setNotifications(true) // placeholder
            _uiState.update { it.copy(logsMaxEntries = max) }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // LIMPIAR DATOS
    // ══════════════════════════════════════════════════════════════════════

    fun requestClearAll() {
        _uiState.update { it.copy(showClearDialog = true) }
    }

    fun dismissClearDialog() {
        _uiState.update { it.copy(showClearDialog = false) }
    }

    fun confirmClearAll() {
        viewModelScope.launch {
            repository.clearAllData()
            _uiState.update {
                it.copy(
                    showClearDialog = false,
                    snackbarMessage = "Todos los datos eliminados"
                )
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PERMISOS
    // ══════════════════════════════════════════════════════════════════════

    fun refreshPermissions() {
        _uiState.update {
            it.copy(permissionStatus = PermissionHelper.permissionStatus(context))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            _uiState.update {
                it.copy(snackbarMessage = "Registros eliminados")
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// UI STATE
// ══════════════════════════════════════════════════════════════════════════

data class SettingsUiState(
    // Preferencias
    val autoReconnect: Boolean      = false,
    val floatingWindow: Boolean     = true,
    val notifications: Boolean      = true,
    val reconnectOnBoot: Boolean    = false,
    val logsMaxEntries: Int         = 500,

    // Permisos
    val permissionStatus: PermissionStatus = PermissionStatus(
        vpn          = false,
        overlay      = false,
        notification = false,
        battery      = false
    ),

    // UI
    val showClearDialog: Boolean    = false,
    val snackbarMessage: String?    = null
)
