package com.ghostnexora.vpn.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.model.DnsMode
import com.ghostnexora.vpn.data.model.IpMode
import com.ghostnexora.vpn.data.model.NetworkPreferences
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.diagnostics.ConnectionDiagnosticsEngine
import com.ghostnexora.vpn.diagnostics.DiagnosticReport
import com.ghostnexora.vpn.diagnostics.DiagnosticStep
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val knownHostStore: KnownHostStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val diagnostics = ConnectionDiagnosticsEngine(context, repository)
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
                Triple(pair.first, pair.second, firstLaunch)
            }.combine(repository.networkPreferences) { values, networkPreferences ->
                val (snapshot, maxLogs, firstLaunch) = values
                SettingsUiState(
                    autoReconnect = snapshot.autoReconnect,
                    killSwitch = snapshot.killSwitch,
                    floatingWindow = snapshot.floatingWindow,
                    notifications = snapshot.notifications,
                    reconnectOnBoot = snapshot.reconnectOnBoot,
                    logsMaxEntries = maxLogs,
                    networkPreferences = networkPreferences,
                    permissionStatus = PermissionHelper.permissionStatus(context),
                    knownHostCount = knownHostStore.count(),
                    firstLaunch = firstLaunch,
                    diagnosticRunning = _uiState.value.diagnosticRunning,
                    diagnosticSteps = _uiState.value.diagnosticSteps,
                    diagnosticReport = _uiState.value.diagnosticReport,
                    snackbarMessage = _uiState.value.snackbarMessage,
                    initialized = true
                )
            }.collectLatest { state -> _uiState.value = state }
        }
    }

    fun toggleAutoReconnect() = viewModelScope.launch { repository.setAutoReconnect(!_uiState.value.autoReconnect) }
    fun toggleKillSwitch() = viewModelScope.launch { repository.setKillSwitch(!_uiState.value.killSwitch) }
    fun toggleFloatingWindow() = viewModelScope.launch { repository.setFloatingWindow(!_uiState.value.floatingWindow) }
    fun toggleNotifications() = viewModelScope.launch { repository.setNotifications(!_uiState.value.notifications) }
    fun toggleReconnectOnBoot() = viewModelScope.launch { repository.setReconnectOnBoot(!_uiState.value.reconnectOnBoot) }

    fun setIpMode(value: IpMode) = viewModelScope.launch {
        repository.setIpMode(value)
        _uiState.update { it.copy(snackbarMessage = "IP mode updated to ${value.label}") }
    }

    fun setTunMtu(value: Int) = viewModelScope.launch {
        repository.setTunMtu(value)
        _uiState.update { it.copy(snackbarMessage = "TUN MTU updated to ${value.coerceIn(1280, 1500)}") }
    }

    fun setDnsMode(value: DnsMode) = viewModelScope.launch {
        repository.setDnsMode(value)
        _uiState.update { it.copy(snackbarMessage = "DNS updated to ${value.label}") }
    }

    fun setCustomDns(primary: String, secondary: String) = viewModelScope.launch {
        val first = primary.trim()
        val second = secondary.trim()
        if (first.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Primary DNS cannot be empty") }
            return@launch
        }
        repository.setCustomDns(first, second)
        repository.setDnsMode(DnsMode.CUSTOM)
        _uiState.update { it.copy(snackbarMessage = "Custom DNS saved") }
    }

    fun setReconnectMaxAttempts(value: Int) = viewModelScope.launch {
        repository.setReconnectMaxAttempts(value)
        _uiState.update { it.copy(snackbarMessage = "Reconnect limit updated") }
    }

    fun runDiagnostics() {
        if (_uiState.value.diagnosticRunning) return
        viewModelScope.launch {
            val profileId = repository.activeProfileId.first()
            val profile = profileId.takeIf(String::isNotBlank)?.let { repository.getProfileById(it) }
                ?: repository.getLastUsedProfile()
            if (profile == null) {
                _uiState.update { it.copy(snackbarMessage = "Select or connect a profile before running diagnostics") }
                return@launch
            }

            _uiState.update {
                it.copy(diagnosticRunning = true, diagnosticSteps = emptyList(), diagnosticReport = null)
            }
            val report = diagnostics.run(profile, _uiState.value.networkPreferences) { step ->
                _uiState.update { current -> current.copy(diagnosticSteps = current.diagnosticSteps + step) }
            }
            _uiState.update {
                it.copy(
                    diagnosticRunning = false,
                    diagnosticReport = report,
                    snackbarMessage = if (report.successful) "Diagnostics passed" else "Diagnostics found a connection problem"
                )
            }
        }
    }

    fun clearDiagnosticReport() {
        _uiState.update { it.copy(diagnosticSteps = emptyList(), diagnosticReport = null) }
    }

    fun setLogsMaxEntries(max: Int) {
        val clamped = max.coerceIn(100, 5_000)
        viewModelScope.launch {
            repository.setLogsMaxEntries(clamped)
            repository.trimLogs(clamped)
            _uiState.update { it.copy(logsMaxEntries = clamped, snackbarMessage = "Limit updated to $clamped") }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            _uiState.update { it.copy(snackbarMessage = "Logs deleted") }
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
                    "Trusted SSH fingerprints removed"
                } else {
                    "Could not clear the trusted SSH store"
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
    val floatingWindow: Boolean = false,
    val notifications: Boolean = true,
    val reconnectOnBoot: Boolean = false,
    val logsMaxEntries: Int = 500,
    val networkPreferences: NetworkPreferences = NetworkPreferences(),
    val knownHostCount: Int = 0,
    val permissionStatus: PermissionStatus = PermissionStatus(
        vpn = false,
        overlay = false,
        notification = false,
        battery = false
    ),
    val diagnosticRunning: Boolean = false,
    val diagnosticSteps: List<DiagnosticStep> = emptyList(),
    val diagnosticReport: DiagnosticReport? = null,
    val snackbarMessage: String? = null,
    val firstLaunch: Boolean = true,
    val initialized: Boolean = false
)