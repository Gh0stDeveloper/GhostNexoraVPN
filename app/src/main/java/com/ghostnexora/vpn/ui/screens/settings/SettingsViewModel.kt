package com.ghostnexora.vpn.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.security.KnownHostStore
import com.ghostnexora.vpn.util.PermissionHelper
import com.ghostnexora.vpn.util.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val knownHostStore: KnownHostStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        refreshPermissions()
        refreshKnownHosts()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                repository.autoReconnect,
                repository.killSwitch,
                repository.floatingWindow,
                repository.notifications,
                repository.reconnectOnBoot
            ) { reconnect, killSwitch, floating, notifications, boot ->
                SettingsSnapshot(reconnect, killSwitch, floating, notifications, boot)
            }.combine(repository.logsMaxEntries) { snapshot, maxLogs ->
                snapshot to maxLogs
            }.combine(repository.isFirstLaunch) { pair, firstLaunch ->
                val (snapshot, maxLogs) = pair
                SettingsUiState(
                    autoReconnect = snapshot.autoReconnect,
                    killSwitch = snapshot.killSwitch,
                    floatingWindow = snapshot.floatingWindow,
                    notifications = snapshot.notifications,
                    reconnectOnBoot = snapshot.reconnectOnBoot,
                    logsMaxEntries = maxLogs,
                    permissionStatus = PermissionHelper.permissionStatus(context),
                    knownHostCount = knownHostStore.count(),
                    firstLaunch = firstLaunch
                )
            }.collectLatest { state -> _uiState.value = state }
        }
    }

    fun toggleAutoReconnect() {
        viewModelScope.launch { repository.setAutoReconnect(!_uiState.value.autoReconnect) }
    }

    fun toggleKillSwitch() {
        viewModelScope.launch { repository.setKillSwitch(!_uiState.value.killSwitch) }
    }

    fun toggleFloatingWindow() {
        viewModelScope.launch { repository.setFloatingWindow(!_uiState.value.floatingWindow) }
    }

    fun toggleNotifications() {
        viewModelScope.launch { repository.setNotifications(!_uiState.value.notifications) }
    }

    fun toggleReconnectOnBoot() {
        viewModelScope.launch { repository.setReconnectOnBoot(!_uiState.value.reconnectOnBoot) }
    }

    fun setLogsMaxEntries(max: Int) {
        val clamped = max.coerceIn(100, 5_000)
        viewModelScope.launch {
            repository.setLogsMaxEntries(clamped)
            repository.trimLogs(clamped)
            _uiState.update { it.copy(logsMaxEntries = clamped, snackbarMessage = "Límite actualizado a $clamped") }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            _uiState.update { it.copy(snackbarMessage = "Registros eliminados") }
        }
    }

    fun refreshKnownHosts() {
        _uiState.update { it.copy(knownHostCount = knownHostStore.count()) }
    }

    fun clearKnownHosts() {
        val cleared = knownHostStore.clear()
        _uiState.update {
            it.copy(
                knownHostCount = knownHostStore.count(),
                snackbarMessage = if (cleared) {
                    "Servidores SSH confiables eliminados. Se verificará su identidad en la próxima conexión."
                } else {
                    "No se pudo limpiar el almacén de servidores SSH"
                }
            )
        }
    }

    fun refreshPermissions() {
        _uiState.update { it.copy(permissionStatus = PermissionHelper.permissionStatus(context)) }
    }

    fun completeFirstLaunch() {
        viewModelScope.launch { repository.setFirstLaunchDone() }
        _uiState.update { it.copy(firstLaunch = false) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

private data class SettingsSnapshot(
    val autoReconnect: Boolean,
    val killSwitch: Boolean,
    val floatingWindow: Boolean,
    val notifications: Boolean,
    val reconnectOnBoot: Boolean
)

data class SettingsUiState(
    val autoReconnect: Boolean = true,
    val killSwitch: Boolean = true,
    val floatingWindow: Boolean = true,
    val notifications: Boolean = true,
    val reconnectOnBoot: Boolean = false,
    val logsMaxEntries: Int = 500,
    val knownHostCount: Int = 0,
    val permissionStatus: PermissionStatus = PermissionStatus(
        vpn = false,
        overlay = false,
        notification = false,
        battery = false
    ),
    val snackbarMessage: String? = null,
    val firstLaunch: Boolean = true
)
