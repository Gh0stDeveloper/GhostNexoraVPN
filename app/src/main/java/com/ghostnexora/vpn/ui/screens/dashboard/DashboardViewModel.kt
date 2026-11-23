package com.ghostnexora.vpn.ui.screens.dashboard

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.R
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.data.model.VpnConnectionState
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.model.VpnTrafficStats
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.service.GhostVpnService
import com.ghostnexora.vpn.service.VpnServiceContract
import com.ghostnexora.vpn.util.PhysicalNetworkLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ProfileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    val networkPreferences = repository.networkPreferences
    private var timerJob: Job? = null
    private var connectJob: Job? = null
    private var sessionStartTime: Long = 0L
    private var lastConnectRequestElapsed: Long = 0L
    private var pendingPermissionProfileId: String? = null
    private var runtimeReceiverRegistered = false
    private var runtimeLifetime: IBinder? = null
    private var runtimeDeathRecipient: IBinder.DeathRecipient? = null

    private val runtimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            val event = intent ?: return
            when (event.action) {
                VpnServiceContract.ACTION_RUNTIME_STATE -> {
                    val state = VpnServiceContract.stateFrom(event) ?: return
                    val lifetime = VpnServiceContract.runtimeLifetimeFrom(event) ?: return
                    if (state is VpnConnectionState.Disconnected) {
                        clearRuntimeLifetime()
                        handleServiceState(state)
                    } else if (watchRuntimeLifetime(lifetime, VpnServiceContract.runtimePidFrom(event))) {
                        handleServiceState(state)
                    }
                }
                VpnServiceContract.ACTION_TRAFFIC_STATS ->
                    VpnServiceContract.trafficFrom(event)?.let { stats ->
                        _uiState.update { it.copy(traffic = stats) }
                    }
            }
        }
    }

    init {
        registerRuntimeReceiver()
        observeActiveProfile()
        observeAllProfiles()
        observeRecentLogs()
        observeDefaultNetwork()
    }

    private fun observeDefaultNetwork() {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val defaultLabels = callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    trySend(PhysicalNetworkLabel.from(context, capabilities))
                }

                override fun onLost(network: Network) {
                    trySend(PhysicalNetworkLabel.from(context, null))
                }
            }
            trySend(PhysicalNetworkLabel.from(context, manager.activeNetwork?.let(manager::getNetworkCapabilities)))
            manager.registerDefaultNetworkCallback(callback)
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }
        viewModelScope.launch {
            defaultLabels.collectLatest { label -> _uiState.update { it.copy(defaultNetworkLabel = label) } }
        }
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

    private fun observeRecentLogs() {
        viewModelScope.launch {
            repository.getRecentLogs(200).collectLatest { logs -> _uiState.update { it.copy(recentLogs = logs) } }
        }
    }

    fun setVpnDnsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setVpnDnsEnabled(enabled) }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            _uiState.update { it.copy(snackbarMessage = context.getString(R.string.dashboard_logs_cleared)) }
        }
    }

    fun onMainAction(launchVpnPermission: (Intent) -> Unit) {
        when (_uiState.value.connectionState) {
            is VpnConnectionState.Disconnected,
            is VpnConnectionState.Error -> requestConnect(launchVpnPermission)
            is VpnConnectionState.Connecting -> Unit
            is VpnConnectionState.Reconnecting,
            is VpnConnectionState.Connected -> disconnect()
            is VpnConnectionState.Disconnecting -> Unit
        }
    }

    private fun requestConnect(launchVpnPermission: (Intent) -> Unit) {
        if (pendingPermissionProfileId != null) return
        val profile = _uiState.value.activeProfile
        if (profile == null) {
            _uiState.update { it.copy(snackbarMessage = "Selecciona un perfil primero") }
            return
        }
        val permissionIntent = VpnService.prepare(context)
        if (permissionIntent != null) {
            pendingPermissionProfileId = profile.id
            launchVpnPermission(permissionIntent)
            return
        }
        connect(profile)
    }

    fun onVpnPermissionGranted() {
        val profileId = pendingPermissionProfileId ?: return
        pendingPermissionProfileId = null
        val profile = _uiState.value.activeProfile
        if (profile?.id == profileId) connect(profile)
    }

    fun onVpnPermissionDenied() {
        pendingPermissionProfileId = null
        _uiState.update { it.copy(snackbarMessage = "Permiso VPN requerido para conectar") }
    }

    private fun connect(profile: VpnProfile) {
        val now = SystemClock.elapsedRealtime()
        val remaining = CONNECT_REQUEST_COOLDOWN_MS - (now - lastConnectRequestElapsed)
        if (remaining > 0L || connectJob?.isActive == true) {
            _uiState.update { it.copy(snackbarMessage = "Espera un momento antes de volver a conectar") }
            return
        }
        lastConnectRequestElapsed = now

        connectJob = viewModelScope.launch {
            updateState(VpnConnectionState.Connecting(profile.name))
            repository.log(
                LogLevel.INFO,
                "Solicitud de conexión enviada al motor VPN protegido",
                profile.id,
                "VPN"
            )

            try {
                val intent = Intent(context, GhostVpnService::class.java).apply {
                    action = GhostVpnService.ACTION_CONNECT
                    putExtra(GhostVpnService.EXTRA_PROFILE_ID, profile.id)
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (cancelled: CancellationException) {
                updateState(VpnConnectionState.Disconnected)
                throw cancelled
            } catch (error: Throwable) {
                val detail = error.message.orEmpty().replace('\n', ' ').take(180)
                val message = "Android no pudo iniciar el servicio VPN" +
                    detail.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
                repository.log(LogLevel.ERROR, message, profile.id, "VPN")
                _uiState.update {
                    it.copy(
                        connectionState = VpnConnectionState.Error(message, profile.name),
                        snackbarMessage = message
                    )
                }
            } finally {
                connectJob = null
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        stopSessionTimer()
        updateState(VpnConnectionState.Disconnecting)
        context.startService(Intent(context, GhostVpnService::class.java).apply {
            action = GhostVpnService.ACTION_DISCONNECT
            putExtra(GhostVpnService.EXTRA_DISCONNECT_SOURCE, "panel principal")
        })
    }

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
        clearRuntimeLifetime()
        connectJob?.cancel()
        stopSessionTimer()
        if (runtimeReceiverRegistered) {
            runCatching { context.unregisterReceiver(runtimeReceiver) }
            runtimeReceiverRegistered = false
        }
        super.onCleared()
    }

    private fun registerRuntimeReceiver() {
        val filter = IntentFilter().apply {
            addAction(VpnServiceContract.ACTION_RUNTIME_STATE)
            addAction(VpnServiceContract.ACTION_TRAFFIC_STATS)
        }
        ContextCompat.registerReceiver(
            context,
            runtimeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        runtimeReceiverRegistered = true
    }

    fun refreshRuntime() {
        context.sendBroadcast(
            Intent(VpnServiceContract.ACTION_QUERY_RUNTIME).setPackage(context.packageName)
        )
    }

    private fun watchRuntimeLifetime(lifetime: IBinder, pid: Int): Boolean {
        if (runtimeLifetime == lifetime) return true
        clearRuntimeLifetime()
        val observedAt = System.currentTimeMillis()
        val recipient = IBinder.DeathRecipient {
            viewModelScope.launch {
                if (runtimeLifetime == lifetime) resetAfterRuntimeDeath(pid, observedAt)
            }
        }
        return try {
            lifetime.linkToDeath(recipient, 0)
            runtimeLifetime = lifetime
            runtimeDeathRecipient = recipient
            true
        } catch (_: RemoteException) {
            resetAfterRuntimeDeath(pid, observedAt)
            false
        }
    }

    private fun clearRuntimeLifetime() {
        val recipient = runtimeDeathRecipient
        if (recipient != null) runCatching { runtimeLifetime?.unlinkToDeath(recipient, 0) }
        runtimeLifetime = null
        runtimeDeathRecipient = null
    }

    private fun resetAfterRuntimeDeath(pid: Int, observedAt: Long) {
        clearRuntimeLifetime()
        _uiState.update { it.copy(traffic = VpnTrafficStats()) }
        val state = _uiState.value.connectionState
        if (state is VpnConnectionState.Disconnected || state is VpnConnectionState.Disconnecting) {
            handleServiceState(VpnConnectionState.Disconnected)
        } else if (state !is VpnConnectionState.Error) {
            val message = "El motor VPN se cerró inesperadamente. Vuelve a intentar. [VPN-ENGINE-EXIT]"
            val profile = _uiState.value.activeProfile
            val error = VpnConnectionState.Error(message, profile?.name.orEmpty())
            handleServiceState(error)
            viewModelScope.launch {
                repository.log(LogLevel.ERROR, message, profile?.id, "VPN")
                val reason = readRuntimeExitReason(pid, observedAt) ?: return@launch
                val diagnosis = "$message · $reason"
                repository.log(LogLevel.ERROR, diagnosis, profile?.id, "VPN")
                if (_uiState.value.connectionState === error) {
                    handleServiceState(error.copy(message = diagnosis))
                }
            }
        }
    }

    private suspend fun readRuntimeExitReason(pid: Int, observedAt: Long): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || pid <= 0) return null
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        repeat(6) { attempt ->
            val exit = withContext(Dispatchers.IO) {
                runCatching {
                    manager.getHistoricalProcessExitReasons(context.packageName, pid, 4).firstOrNull {
                        it.processName == "${context.packageName}:vpn" &&
                            it.timestamp >= observedAt - 2_000L
                    }
                }.getOrNull()
            }
            if (exit != null) {
                val reason = when (exit.reason) {
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "fallo nativo"
                    ApplicationExitInfo.REASON_CRASH -> "excepción de la aplicación"
                    ApplicationExitInfo.REASON_SIGNALED -> "terminación por señal"
                    ApplicationExitInfo.REASON_ANR -> "el proceso no respondió (ANR)"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "memoria insuficiente"
                    ApplicationExitInfo.REASON_USER_REQUESTED -> "cierre solicitado"
                    else -> "motivo del sistema ${exit.reason}"
                }
                return "Android: $reason · estado ${exit.status}"
            }
            // ActivityManager can publish the exit record after Binder reports the death.
            if (attempt < 5) delay(400)
        }
        return null
    }

    private fun handleServiceState(state: VpnConnectionState) {
        _uiState.update { current ->
            current.copy(
                connectionState = state,
                sessionElapsed = when (state) {
                    is VpnConnectionState.Connected ->
                        (System.currentTimeMillis() - state.connectedSince).coerceAtLeast(0L)
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
            is VpnConnectionState.Reconnecting -> if (sessionStartTime > 0L) startSessionTimer()
            VpnConnectionState.Disconnected,
            VpnConnectionState.Disconnecting,
            is VpnConnectionState.Error -> stopSessionTimer()
            is VpnConnectionState.Connecting -> Unit
        }
    }

    private companion object {
        const val CONNECT_REQUEST_COOLDOWN_MS = 2_500L
    }
}

data class DashboardUiState(
    val connectionState: VpnConnectionState = VpnConnectionState.Disconnected,
    val activeProfile: VpnProfile? = null,
    val hasProfiles: Boolean = false,
    val sessionElapsed: Long = 0L,
    val traffic: VpnTrafficStats = VpnTrafficStats(),
    val defaultNetworkLabel: String = "",
    val snackbarMessage: String? = null,
    val recentLogs: List<LogEntry> = emptyList()
) {
    val isConnected: Boolean get() = connectionState is VpnConnectionState.Connected
    val isReconnecting: Boolean get() = connectionState is VpnConnectionState.Reconnecting
    val isConnecting: Boolean get() = connectionState is VpnConnectionState.Connecting || isReconnecting
    val isDisconnected: Boolean get() = connectionState is VpnConnectionState.Disconnected
    val hasError: Boolean get() = connectionState is VpnConnectionState.Error
    val serverIp: String
        get() = (connectionState as? VpnConnectionState.Connected)?.serverIp
            ?: activeProfile?.let { if (it.isLocked) "[OCULTO]" else it.host }
            ?: "--"
}
