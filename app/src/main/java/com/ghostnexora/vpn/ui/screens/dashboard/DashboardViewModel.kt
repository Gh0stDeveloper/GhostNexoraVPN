package com.ghostnexora.vpn.ui.screens.dashboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.VpnConnectionState
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.model.VpnTrafficStats
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.service.GhostVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ProfileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var timerJob: Job? = null
    private var sessionStartTime: Long = 0L

    init {
        observeActiveProfile()
        observeAllProfiles()
        observeServiceState()
        observeTrafficStats()
        observeRecentLogs()
    }

    private fun observeActiveProfile() {
        viewModelScope.launch {
            repository.activeProfileId
                .flatMapLatest { id -> if (id.isEmpty()) flowOf(null) else repository.observeProfile(id) }
                .collectLatest { profile -> _uiState.update { it.copy(activeProfile = profile) } }
        }
    }

    private fun observeAllProfiles() {
        viewModelScope.launch {
            repository.profileCount.collectLatest { count -> _uiState.update { it.copy(hasProfiles = count > 0) } }
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            GhostVpnService.connectionState.collectLatest { state ->
                _uiState.update { current ->
                    current.copy(
                        connectionState = state,
                        sessionElapsed = when (state) {
                            is VpnConnectionState.Connected -> System.currentTimeMillis() - state.connectedSince
                            is VpnConnectionState.Reconnecting -> current.sessionElapsed
                            else -> 0L
                        }
                    )
                }
                when (state) {
                    is VpnConnectionState.Connected -> {
                        sessionStartTime = state.connectedSince
                        startSessionTimer()
                    }
                    is VpnConnectionState.Reconnecting -> if (sessionStartTime > 0) startSessionTimer()
                    is VpnConnectionState.Disconnected,
                    is VpnConnectionState.Disconnecting,
                    is VpnConnectionState.Error -> stopSessionTimer()
                    is VpnConnectionState.Connecting -> Unit
                }
            }
        }
    }

    private fun observeTrafficStats() {
        viewModelScope.launch {
            GhostVpnService.trafficStats.collectLatest { stats ->
                _uiState.update { it.copy(traffic = stats) }
            }
        }
    }

    private fun observeRecentLogs() {
        viewModelScope.launch {
            repository.getRecentLogs(200).collectLatest { logs -> _uiState.update { it.copy(recentLogs = logs) } }
        }
    }

    fun onMainAction(activity: Activity) {
        when (_uiState.value.connectionState) {
            is VpnConnectionState.Disconnected,
            is VpnConnectionState.Error -> requestConnect(activity)
            is VpnConnectionState.Connecting -> cancelConnect()
            is VpnConnectionState.Reconnecting,
            is VpnConnectionState.Connected -> disconnect()
            is VpnConnectionState.Disconnecting -> Unit
        }
    }

    private fun requestConnect(activity: Activity) {
        val profile = _uiState.value.activeProfile
        if (profile == null) {
            _uiState.update { it.copy(snackbarMessage = "Selecciona un perfil primero") }
            return
        }
        val permissionIntent = VpnService.prepare(activity)
        if (permissionIntent != null) {
            _uiState.update { it.copy(pendingVpnPermissionIntent = permissionIntent) }
            return
        }
        connect(profile)
    }

    fun onVpnPermissionGranted() {
        val profile = _uiState.value.activeProfile ?: return
        _uiState.update { it.copy(pendingVpnPermissionIntent = null) }
        connect(profile)
    }

    fun onVpnPermissionDenied() {
        _uiState.update {
            it.copy(pendingVpnPermissionIntent = null, snackbarMessage = "Permiso VPN requerido para conectar")
        }
    }

    private fun connect(profile: VpnProfile) {
        viewModelScope.launch {
            updateState(VpnConnectionState.Connecting(profile.name))
            val intent = Intent(context, GhostVpnService::class.java).apply {
                action = GhostVpnService.ACTION_CONNECT
                putExtra(GhostVpnService.EXTRA_PROFILE_ID, profile.id)
            }
            context.startForegroundService(intent)
            repository.markLastUsed(profile.id)
        }
    }

    fun disconnect() {
        stopSessionTimer()
        updateState(VpnConnectionState.Disconnecting)
        context.startService(Intent(context, GhostVpnService::class.java).apply {
            action = GhostVpnService.ACTION_DISCONNECT
        })
    }

    private fun cancelConnect() = disconnect()

    private fun startSessionTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (true) {
                if (sessionStartTime > 0) {
                    _uiState.update { it.copy(sessionElapsed = System.currentTimeMillis() - sessionStartTime) }
                }
                delay(1_000L)
            }
        }
    }

    private fun stopSessionTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch { repository.setActiveProfileId(profileId) }
    }

    private fun updateState(state: VpnConnectionState) {
        _uiState.update { it.copy(connectionState = state) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        stopSessionTimer()
        super.onCleared()
    }
}

data class DashboardUiState(
    val connectionState: VpnConnectionState = VpnConnectionState.Disconnected,
    val activeProfile: VpnProfile? = null,
    val hasProfiles: Boolean = false,
    val sessionElapsed: Long = 0L,
    val traffic: VpnTrafficStats = VpnTrafficStats(),
    val snackbarMessage: String? = null,
    val pendingVpnPermissionIntent: Intent? = null,
    val recentLogs: List<LogEntry> = emptyList()
) {
    val isConnected: Boolean get() = connectionState is VpnConnectionState.Connected
    val isReconnecting: Boolean get() = connectionState is VpnConnectionState.Reconnecting
    val isConnecting: Boolean get() = connectionState is VpnConnectionState.Connecting || isReconnecting
    val isDisconnected: Boolean get() = connectionState is VpnConnectionState.Disconnected
    val hasError: Boolean get() = connectionState is VpnConnectionState.Error
    val serverIp: String get() = (connectionState as? VpnConnectionState.Connected)?.serverIp ?: activeProfile?.host ?: "--"
}
