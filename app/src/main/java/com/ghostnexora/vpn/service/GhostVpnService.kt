package com.ghostnexora.vpn.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.ghostnexora.vpn.BuildConfig
import com.ghostnexora.vpn.GhostNexoraApp
import com.ghostnexora.vpn.R
import com.ghostnexora.vpn.data.model.AppRoutingMode
import com.ghostnexora.vpn.data.model.AppRoutingPreferences
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.data.model.NetworkPreferences
import com.ghostnexora.vpn.data.model.VpnConnectionState
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.model.VpnTrafficStats
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.tunnel.ConnectionErrorCatalog
import com.ghostnexora.vpn.tunnel.OutboundSocketProtection
import com.ghostnexora.vpn.tunnel.StableXrayConfigFactory
import com.ghostnexora.vpn.tunnel.TunnelLogEvent
import com.ghostnexora.vpn.tunnel.TunnelLogEventParser
import com.ghostnexora.vpn.tunnel.TunnelManager
import com.ghostnexora.vpn.tunnel.TunnelRuntime
import com.ghostnexora.vpn.ui.MainActivity
import com.ghostnexora.vpn.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

@AndroidEntryPoint
class GhostVpnService : VpnService() {
    @Inject
    lateinit var repository: ProfileRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionMutex = Mutex()
    private var tunInterface: ParcelFileDescriptor? = null
    private var tunnelRuntime: TunnelRuntime? = null
    private var activeProfile: VpnProfile? = null
    private var logRedactionProfile: VpnProfile? = null
    private var reconnectJob: Job? = null
    private var startupVerificationJob: Job? = null
    private var healthJob: Job? = null
    private var statsJob: Job? = null
    private var tunnelLogWriterJob: Job? = null
    private var intentionalDisconnect = false
    private var sessionConnectedSince = 0L
    private var reconnectCount = 0
    private var sessionReceivedBytes = 0L
    private var sessionSentBytes = 0L
    private var activeNetworkPreferences = NetworkPreferences()

    @Volatile
    private var physicalNetworkAvailable = false

    @Volatile
    private var physicalNetworkType = "Sin red"

    @Volatile
    private var underlyingNetwork: Network? = null

    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val tunnelManager by lazy {
        TunnelManager(applicationContext) { status ->
            TunnelLogEventParser.parse(status)?.let { event ->
                tunnelLogChannel.trySend(PendingTunnelLog(activeProfile?.id, event))
            }
        }
    }

    private val tunnelLogChannel = Channel<PendingTunnelLog>(Channel.UNLIMITED)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            registerUnderlyingNetwork(network)
            serviceScope.launch {
                logSafe(LogLevel.INFO, "Red física disponible: $physicalNetworkType", activeProfile?.id, "NETWORK")
                val state = connectionState.value
                if (state is VpnConnectionState.Reconnecting && reconnectJob?.isActive != true) {
                    triggerReconnect("La red física volvió a estar disponible")
                }
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return
            physicalNetworkAvailable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            physicalNetworkType = networkType(capabilities)
            underlyingNetwork = network
            runCatching { setUnderlyingNetworks(arrayOf(network)) }
        }

        override fun onLost(network: Network) {
            serviceScope.launch {
                delay(300L)
                val replacement = findUsablePhysicalNetwork(excluding = network)
                if (replacement != null) {
                    registerUnderlyingNetwork(replacement)
                    logSafe(LogLevel.INFO, "Cambio de red física: $physicalNetworkType", activeProfile?.id, "NETWORK")
                    return@launch
                }

                underlyingNetwork = null
                physicalNetworkAvailable = false
                physicalNetworkType = "Sin red"
                runCatching { setUnderlyingNetworks(null) }
                if (activeProfile != null && tunnelRuntime != null) {
                    logSafe(
                        LogLevel.WARNING,
                        "Red física perdida; activando protección de reconexión",
                        activeProfile?.id,
                        "NETWORK"
                    )
                    triggerReconnect("Red física perdida")
                }
            }
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.ghostnexora.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.ghostnexora.vpn.DISCONNECT"
        const val EXTRA_PROFILE_ID = "extra_profile_id"

        private const val INITIAL_VERIFICATION_TIMEOUT_MS = 30_000L
        private val RECONNECT_DELAYS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
        private val SENSITIVE_LOG_TAGS = setOf("SSH", "TLS", "PROXY", "PAYLOAD", "SOCKS")

        private val _connectionState = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val connectionState: StateFlow<VpnConnectionState> = _connectionState.asStateFlow()

        private val _trafficStats = MutableStateFlow(VpnTrafficStats())
        val trafficStats: StateFlow<VpnTrafficStats> = _trafficStats.asStateFlow()

        fun updateState(state: VpnConnectionState) {
            _connectionState.value = state
        }
    }

    override fun onCreate() {
        super.onCreate()
        OutboundSocketProtection.install { socket -> protect(socket) }
        tunnelLogWriterJob = serviceScope.launch {
            for (pending in tunnelLogChannel) {
                logSafe(
                    pending.event.level,
                    pending.event.message,
                    pending.profileId,
                    pending.event.tag
                )
            }
        }
        val initialNetwork = findUsablePhysicalNetwork()
        if (initialNetwork != null) registerUnderlyingNetwork(initialNetwork)
        registerPhysicalNetworkCallback()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                startPreparingForeground("Preparando conexión")
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId.isNullOrBlank()) {
                    serviceScope.launch {
                        repository.setVpnDesiredConnected(false)
                        logSafe(LogLevel.ERROR, "No se especificó un perfil", tag = "VPN")
                        publishState(VpnConnectionState.Error("Sin perfil especificado"))
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                } else {
                    serviceScope.launch {
                        repository.setVpnDesiredConnected(true)
                        repository.resetVpnRecovery()
                        handleConnect(profileId)
                    }
                }
            }

            ACTION_DISCONNECT -> serviceScope.launch {
                logSafe(
                    LogLevel.INFO,
                    "Solicitud de desconexión recibida desde el control de la aplicación",
                    activeProfile?.id,
                    "VPN"
                )
                repository.setVpnDesiredConnected(false)
                handleDisconnect()
            }
            VpnServiceContract.ACTION_QUERY_RUNTIME -> serviceScope.launch {
                handleRuntimeQuery(startId)
            }
            else -> {
                startPreparingForeground("Restaurando sesión")
                serviceScope.launch { handleSystemRestart() }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        serviceScope.launch {
            logSafe(LogLevel.WARNING, "Permiso VPN revocado por Android", activeProfile?.id, "VPN")
            handleDisconnect()
        }
    }

    override fun onDestroy() {
        intentionalDisconnect = true
        reconnectJob?.cancel()
        startupVerificationJob?.cancel()
        healthJob?.cancel()
        statsJob?.cancel()
        cleanupTunnel(closeTun = true)
        OutboundSocketProtection.clear()
        logRedactionProfile = null
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        runCatching { setUnderlyingNetworks(null) }
        tunnelLogChannel.close()
        tunnelLogWriterJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handleConnect(profileId: String) = connectionMutex.withLock {
        val profile = runCatching { repository.getProfileForConnection(profileId) }
            .getOrElse { error ->
                repository.setVpnDesiredConnected(false)
                val message = error.message
                    ?.take(180)
                    ?.takeIf(String::isNotBlank)
                    ?: "No se pudo abrir el perfil protegido"
                publishState(VpnConnectionState.Error(message))
                logSafe(LogLevel.ERROR, message, profileId, "SECURITY")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@withLock
            }
        if (profile == null) {
            repository.setVpnDesiredConnected(false)
            publishState(VpnConnectionState.Error("Perfil no encontrado"))
            logSafe(LogLevel.ERROR, "Perfil no encontrado", tag = "VPN")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return@withLock
        }

        intentionalDisconnect = false
        reconnectJob?.cancel()
        startupVerificationJob?.cancel()
        healthJob?.cancel()
        statsJob?.cancel()
        cleanupTunnel(closeTun = true)
        activeProfile = profile
        logRedactionProfile = profile.takeIf(VpnProfile::isLocked)
        reconnectCount = 0
        sessionConnectedSince = 0L
        publishState(VpnConnectionState.Connecting(profile.name))
        startForeground(
            GhostNexoraApp.NOTIF_ID_VPN,
            buildNotification(VpnConnectionState.Connecting(profile.name))
        )

        try {
            validateProfile(profile)
            ensurePhysicalNetwork()
            val preferences = repository.networkPreferences.first()
            val appRouting = repository.appRoutingPreferences.first()
            require(appRouting.isValid) {
                "App-routing invalid: select at least one selected application [APP-ROUTE-001]"
            }
            activeNetworkPreferences = preferences
            logSafe(
                LogLevel.INFO,
                "Red VPN: ${preferences.ipMode.label} · MTU ${preferences.validatedMtu} · ${preferences.dnsMode.label}",
                profile.id,
                "SETTINGS"
            )
            logSafe(
                LogLevel.INFO,
                "Aplicaciones: ${appRouting.mode.label} · ${appRouting.normalizedPackages.size} regla(s)",
                profile.id,
                "APP_ROUTING"
            )
            logSafe(LogLevel.INFO, "Iniciando ${profile.connectionModeLabel}", profile.id, "VPN")
            logConnectionSnapshot(profile)
            logSafe(
                LogLevel.INFO,
                "Activando TUN fail-closed; el core publicará Conectado al iniciar y validará la salida en segundo plano",
                profile.id,
                "NETWORK"
            )

            val tun = buildTunInterface(profile, preferences, appRouting) ?: error("Android no pudo establecer la interfaz VPN")
            tunInterface = tun
            underlyingNetwork?.let { network -> runCatching { setUnderlyingNetworks(arrayOf(network)) } }
            logSafe(
                LogLevel.INFO,
                "TUN activo · MTU ${preferences.validatedMtu} · ${preferences.ipMode.label} · rutas completas · bypass propio aplicado",
                profile.id,
                "NETWORK"
            )

            tunnelRuntime = tunnelManager.start(profile, tun.fd, preferences)
            logTransportReady(profile)
            repository.markLastUsed(profile.id)

            sessionConnectedSince = System.currentTimeMillis()
            val connected = connectedState(profile)
            publishState(connected)
            updateNotification(connected)
            logSafe(
                LogLevel.SUCCESS,
                "Core y TUN activos · estado Conectado publicado · verificación de Internet en segundo plano",
                profile.id,
                "VPN"
            )
            repository.resetVpnRecovery()
            resetTrafficBaseline(profile)
            startStatsTicker(profile)
            startInitialOutboundVerification(profile)
            startHealthMonitor(profile)
            maybeStartFloatingWindow()
        } catch (error: Throwable) {
            val message = friendlyConnectionError(error, profile)
            cleanupTunnel(closeTun = true)
            activeProfile = null
            repository.setVpnDesiredConnected(false)
            publishState(VpnConnectionState.Error(message, profile.name))
            updateNotification(VpnConnectionState.Error(message, profile.name))
            logSafe(LogLevel.ERROR, message, profile.id, "VPN")
            logRedactionProfile = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun handleDisconnect() = connectionMutex.withLock {
        intentionalDisconnect = true
        repository.setVpnDesiredConnected(false)
        repository.resetVpnRecovery()
        reconnectJob?.cancel()
        startupVerificationJob?.cancel()
        healthJob?.cancel()
        statsJob?.cancel()
        val profileId = activeProfile?.id

        publishState(VpnConnectionState.Disconnecting)
        updateNotification(VpnConnectionState.Disconnecting)
        logSafe(LogLevel.INFO, "Cerrando túnel y sesión de transporte", profileId, "VPN")

        cleanupTunnel(closeTun = true)
        stopService(Intent(this, FloatingWindowService::class.java))
        activeProfile = null
        sessionConnectedSince = 0L
        publishTraffic(VpnTrafficStats())
        publishState(VpnConnectionState.Disconnected)
        logSafe(LogLevel.SUCCESS, "VPN desconectada", profileId, "VPN")
        logRedactionProfile = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun handleSystemRestart() {
        val profileId = repository.activeProfileId.first()
        val shouldReconnect =
            repository.autoReconnect.first() && repository.vpnDesiredConnected.first()
        if (shouldReconnect && profileId.isNotBlank()) {
            val recoveryAttempt = repository.claimVpnRecoveryAttempt()
            if (recoveryAttempt == null) {
                val message = "El motor VPN se reinició demasiadas veces; reconexión automática detenida"
                repository.setVpnDesiredConnected(false)
                publishState(VpnConnectionState.Error(message))
                logSafe(LogLevel.ERROR, "$message [CORE-RECOVERY-003]", tag = "CORE")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            logSafe(
                LogLevel.WARNING,
                "Restaurando VPN después del reinicio del proceso nativo · intento $recoveryAttempt/3",
                tag = "CORE"
            )
            handleConnect(profileId)
        } else {
            publishState(VpnConnectionState.Disconnected)
            publishTraffic(VpnTrafficStats())
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun handleRuntimeQuery(startId: Int) {
        broadcastCurrentRuntime()
        if (activeProfile != null || connectionState.value !is VpnConnectionState.Disconnected) return

        if (repository.vpnDesiredConnected.first()) {
            startPreparingForeground("Recuperando motor VPN")
            handleSystemRestart()
        } else {
            stopSelfResult(startId)
        }
    }

    private fun triggerReconnect(reason: String) {
        if (intentionalDisconnect || activeProfile == null || tunInterface == null) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = serviceScope.launch {
            try {
                reconnectLoop(reason)
            } finally {
                reconnectJob = null
            }
        }
    }

    private suspend fun reconnectLoop(reason: String) {
        val profile = activeProfile ?: return
        startupVerificationJob?.cancel()
        healthJob?.cancel()

        connectionMutex.withLock {
            tunnelManager.stop(tunnelRuntime)
            tunnelRuntime = null
        }

        val autoReconnect = repository.autoReconnect.first()
        val killSwitch = repository.killSwitch.first()

        if (!autoReconnect) {
            if (killSwitch) {
                val message = "Conexión perdida. Kill Switch mantiene el tráfico bloqueado."
                publishState(VpnConnectionState.Error(message, profile.name))
                updateNotification(VpnConnectionState.Error(message, profile.name))
                logSafe(LogLevel.WARNING, "$reason · $message", profile.id, "NETWORK")
            } else {
                cleanupTunnel(closeTun = true)
                publishState(VpnConnectionState.Error("Conexión perdida", profile.name))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return
        }

        val preferences = repository.networkPreferences.first()
        activeNetworkPreferences = preferences
        val maxAttempts = preferences.validatedReconnectAttempts
        logSafe(LogLevel.WARNING, "$reason · iniciando reconexión protegida · máximo $maxAttempts", profile.id, "NETWORK")
        var attempt = 0
        while (serviceScope.isActive && !intentionalDisconnect && tunInterface != null && attempt < maxAttempts) {
            val baseDelay = RECONNECT_DELAYS[attempt.coerceAtMost(RECONNECT_DELAYS.lastIndex)]
            val waitMs = baseDelay + ((attempt * 173L) % 650L)
            val state = VpnConnectionState.Reconnecting(profile.name, attempt + 1, waitMs)
            publishState(state)
            updateNotification(state)

            if (!physicalNetworkAvailable) {
                delay(1_000L)
                findUsablePhysicalNetwork()?.let(::registerUnderlyingNetwork)
                continue
            }

            delay(waitMs)
            if (intentionalDisconnect || tunInterface == null) return

            val result = runCatching {
                connectionMutex.withLock {
                    tunnelManager.stop(tunnelRuntime)
                    tunnelRuntime = null
                    val tun = tunInterface ?: error("TUN no disponible durante reconexión")
                    tunnelRuntime = tunnelManager.start(profile, tun.fd, preferences)
                }
            }

            if (result.isSuccess && tunnelManager.isAlive(tunnelRuntime)) {
                reconnectCount += 1
                sessionConnectedSince = System.currentTimeMillis()
                val connected = connectedState(profile)
                publishState(connected)
                updateNotification(connected)
                logSafe(
                    LogLevel.SUCCESS,
                    "Core y TUN restablecidos en intento ${attempt + 1} · validación en segundo plano",
                    profile.id,
                    "NETWORK"
                )
                publishTraffic(_trafficStats.value.copy(reconnectCount = reconnectCount))
                startInitialOutboundVerification(profile)
                startHealthMonitor(profile)
                return
            }

            val error = result.exceptionOrNull() ?: IllegalStateException("Transport not alive")
            val failure = ConnectionErrorCatalog.classify(error, profile)
            logSafe(LogLevel.WARNING, "Intento ${attempt + 1}/$maxAttempts · ${failure.logMessage()}", profile.id, failure.stage)
            attempt += 1
        }

        val exhausted = "Reconnect attempts exhausted ($maxAttempts) [RECONNECT-408]"
        if (killSwitch) {
            publishState(VpnConnectionState.Error(exhausted, profile.name))
            updateNotification(VpnConnectionState.Error(exhausted, profile.name))
            logSafe(LogLevel.ERROR, "$exhausted · Kill Switch keeps traffic blocked", profile.id, "NETWORK")
        } else {
            cleanupTunnel(closeTun = true)
            publishState(VpnConnectionState.Error(exhausted, profile.name))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startInitialOutboundVerification(profile: VpnProfile) {
        startupVerificationJob?.cancel()
        val runtime = tunnelRuntime ?: return
        startupVerificationJob = serviceScope.launch {
            logSafe(
                LogLevel.INFO,
                "Comprobación inicial de Internet iniciada en segundo plano",
                profile.id,
                "NETWORK"
            )
            val result = withTimeoutOrNull(INITIAL_VERIFICATION_TIMEOUT_MS) {
                runCatching {
                    runInterruptible { tunnelManager.verifyActive() }
                }
            }

            if (intentionalDisconnect || activeProfile?.id != profile.id || tunnelRuntime !== runtime) {
                return@launch
            }

            when {
                result == null -> logSafe(
                    LogLevel.WARNING,
                    "La comprobación inicial excedió ${INITIAL_VERIFICATION_TIMEOUT_MS / 1_000}s; el monitor continuará reintentando",
                    profile.id,
                    "NETWORK"
                )
                result.isSuccess -> {
                    val check = result.getOrThrow()
                    tunnelRuntime = runtime.copy(verifiedLatencyMs = check.latencyMs)
                    publishTraffic(_trafficStats.value.copy(latencyMs = check.latencyMs))
                    logSafe(
                        LogLevel.SUCCESS,
                        "Salida a Internet verificada en segundo plano · ${check.latencyMs} ms",
                        profile.id,
                        "NETWORK"
                    )
                }
                else -> {
                    val detail = result.exceptionOrNull()?.message
                        .orEmpty()
                        .replace('\n', ' ')
                        .take(180)
                        .ifBlank { "sin detalle" }
                    logSafe(
                        LogLevel.WARNING,
                        "Túnel activo, pero la comprobación inicial falló · $detail · el monitor reintentará",
                        profile.id,
                        "NETWORK"
                    )
                }
            }
        }
    }

    private fun startHealthMonitor(profile: VpnProfile) {
        healthJob?.cancel()
        healthJob = serviceScope.launch {
            var ticks = 0
            var consecutiveOutboundFailures = 0
            while (isActive && !intentionalDisconnect) {
                delay(5_000L)
                if (connectionState.value !is VpnConnectionState.Connected) continue

                if (!tunnelManager.isAlive(tunnelRuntime)) {
                    logSafe(LogLevel.WARNING, "El transporte dejó de responder [HEALTH-TRANSPORT]", profile.id, "CORE")
                    triggerReconnect("Fallo detectado en el transporte")
                    return@launch
                }

                ticks += 1
                if (ticks % 3 == 0) {
                    if (startupVerificationJob?.isActive == true) continue
                    val internetCheck = runCatching {
                        runInterruptible { tunnelManager.verifyActive() }
                    }
                    if (internetCheck.isSuccess) {
                        consecutiveOutboundFailures = 0
                    } else {
                        consecutiveOutboundFailures += 1
                        logSafe(
                            LogLevel.WARNING,
                            "Comprobación de Internet fallida $consecutiveOutboundFailures/2 [HEALTH-OUTBOUND]",
                            profile.id,
                            "CORE"
                        )
                        if (consecutiveOutboundFailures >= 2) {
                            triggerReconnect("Salida de Internet perdida en dos comprobaciones consecutivas")
                            return@launch
                        }
                    }
                }
            }
        }
    }

    private fun resetTrafficBaseline(profile: VpnProfile) {
        tunnelManager.drainTraffic()
        sessionReceivedBytes = 0L
        sessionSentBytes = 0L
        publishTraffic(VpnTrafficStats(
            receivedBytes = 0,
            sentBytes = 0,
            reconnectCount = reconnectCount,
            latencyMs = tunnelRuntime?.verifiedLatencyMs ?: 0L,
            networkType = physicalNetworkType,
            protocol = profile.connectionModeLabel
        ))
    }

    private fun startStatsTicker(profile: VpnProfile) {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            var tick = 0
            while (isActive && !intentionalDisconnect) {
                delay(1_000L)
                val traffic = tunnelManager.drainTraffic()
                sessionReceivedBytes += traffic.receivedBytes
                sessionSentBytes += traffic.sentBytes
                tick += 1
                val latency = if (tick % 10 == 0 && physicalNetworkAvailable) {
                    measureTcpLatency(profile.host, profile.port)
                } else {
                    _trafficStats.value.latencyMs
                }
                publishTraffic(VpnTrafficStats(
                    receivedBytes = sessionReceivedBytes,
                    sentBytes = sessionSentBytes,
                    downloadBytesPerSecond = if (tunnelManager.isAlive(tunnelRuntime)) {
                        traffic.receivedBytes
                    } else {
                        0
                    },
                    uploadBytesPerSecond = if (tunnelManager.isAlive(tunnelRuntime)) {
                        traffic.sentBytes
                    } else {
                        0
                    },
                    reconnectCount = reconnectCount,
                    latencyMs = latency,
                    networkType = physicalNetworkType,
                    protocol = profile.connectionModeLabel
                ))
            }
        }
    }

    private fun cleanupTunnel(closeTun: Boolean) {
        startupVerificationJob?.cancel()
        startupVerificationJob = null
        runCatching { tunnelManager.stop(tunnelRuntime) }
        tunnelRuntime = null
        if (closeTun) {
            runCatching { tunInterface?.close() }
            tunInterface = null
        }
    }

    private fun validateProfile(profile: VpnProfile) {
        require(profile.host.isNotBlank()) { "El host del servidor es obligatorio" }
        require(profile.port in 1..65535) { "El puerto del servidor es inválido" }
        require(profile.selectedMode.supported) { "${profile.connectionModeLabel} no está disponible" }
        if (profile.selectedMode.isSsh) {
            require(profile.username.isNotBlank()) { "El usuario SSH es obligatorio" }
            require(profile.password.isNotBlank()) { "La contraseña SSH es obligatoria" }
        }
        if (profile.selectedMode == ConnectionMode.V2RAY) {
            require(profile.username.isNotBlank()) { "V2Ray requiere UUID / User ID" }
        }
        if (profile.selectedMode == ConnectionMode.TROJAN || profile.selectedMode == ConnectionMode.UDP) {
            require(profile.password.isNotBlank()) { "El método seleccionado requiere contraseña/auth" }
        }
        if (profile.selectedMode.requiresSni) {
            require(profile.sni.isNotBlank()) { "El método seleccionado requiere SNI" }
        }
        if (profile.selectedMode.requiresPayload) {
            require(profile.payload.isNotBlank()) { "El método seleccionado requiere payload" }
        }
        if (profile.selectedMode.requiresProxy) {
            require(profile.proxy.host.isNotBlank() && profile.proxy.port in 1..65535) {
                "El método seleccionado requiere un proxy válido"
            }
        }
    }

    private fun ensurePhysicalNetwork() {
        val network = underlyingNetwork ?: findUsablePhysicalNetwork()
            ?: error("No hay una red móvil o Wi-Fi con acceso a Internet")
        registerUnderlyingNetwork(network)
    }

    private fun buildTunInterface(
        profile: VpnProfile,
        preferences: NetworkPreferences,
        appRouting: AppRoutingPreferences
    ): ParcelFileDescriptor? = try {
        val builder = Builder()
            .setSession(profile.name)
            .setMtu(preferences.validatedMtu)
            .addAddress("10.20.0.2", 30)
            .addRoute("0.0.0.0", 0)
            .setBlocking(true)
        if (preferences.ipMode.capturesIpv6) {
            builder.addAddress("fd00:20::2", 126)
            builder.addRoute("::", 0)
        }
        preferences.dnsServers().forEach { address ->
            if (preferences.ipMode.capturesIpv6 || !address.contains(':')) {
                runCatching { builder.addDnsServer(address) }
            }
        }
        applyAppRouting(builder, appRouting)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        builder.establish()
    } catch (error: Throwable) {
        serviceScope.launch {
            logSafe(LogLevel.ERROR, "Error creando TUN: ${error.message}", profile.id, "NETWORK")
        }
        null
    }

    private fun applyAppRouting(builder: Builder, preferences: AppRoutingPreferences) {
        val packages = preferences.normalizedPackages.filterNot { it == packageName }
        when (preferences.mode) {
            AppRoutingMode.ALL -> {
                // Mandatory self-bypass: all processes in this application UID,
                // including JSch and the native core, stay on the physical network.
                builder.addDisallowedApplication(packageName)
            }
            AppRoutingMode.ONLY_SELECTED -> {
                require(packages.isNotEmpty()) {
                    "App-routing invalid: no selected application [APP-ROUTE-001]"
                }
                // Android forbids mixing allowed and disallowed lists. The VPN
                // package is therefore excluded by never adding it to the
                // allowlist. Every selected package must resolve or startup fails.
                packages.forEach(builder::addAllowedApplication)
            }
            AppRoutingMode.EXCLUDE_SELECTED -> {
                builder.addDisallowedApplication(packageName)
                packages.forEach(builder::addDisallowedApplication)
            }
        }
    }

    private suspend fun logTransportReady(profile: VpnProfile) {
        if (profile.isLocked) {
            logSafe(
                LogLevel.SUCCESS,
                "Transporte protegido autenticado · parámetros ocultos por el creador",
                profile.id,
                "PROTECTED"
            )
            logSafe(LogLevel.DEBUG, "Core protegido activo", profile.id, "CORE")
            return
        }
        when (profile.selectedMode) {
            ConnectionMode.SSH_DIRECT -> logSafe(LogLevel.SUCCESS, "SSH autenticado · bridge SOCKS/TUN activo", profile.id, "SSH")
            ConnectionMode.SSL_SNI -> logSafe(
                LogLevel.SUCCESS,
                "TLS activo · ${profile.selectedTlsVerificationMode.label} · SSH activo",
                profile.id,
                "TLS"
            )
            ConnectionMode.SSH_PAYLOAD -> logSafe(LogLevel.SUCCESS, "Payload aceptado · SSH/TUN activo", profile.id, "SSH")
            ConnectionMode.SSH_PAYLOAD_SSL -> logSafe(
                LogLevel.SUCCESS,
                "TLS + payload + SSH activos · ${profile.selectedTlsVerificationMode.label}",
                profile.id,
                "TLS"
            )
            ConnectionMode.SSH_PROXY -> logSafe(LogLevel.SUCCESS, "Proxy + SSH activos", profile.id, "SSH")
            ConnectionMode.SSH_PAYLOAD_PROXY -> logSafe(LogLevel.SUCCESS, "Proxy + payload + SSH activos", profile.id, "SSH")
            ConnectionMode.SSH_PAYLOAD_PROXY_SSL -> logSafe(
                LogLevel.SUCCESS,
                "Proxy + payload + TLS + SSH activos · ${profile.selectedTlsVerificationMode.label}",
                profile.id,
                "TLS"
            )
            ConnectionMode.V2RAY -> logSafe(LogLevel.SUCCESS, "V2Ray/Xray Core y TUN activos", profile.id, "CORE")
            ConnectionMode.TROJAN -> logSafe(LogLevel.SUCCESS, "Trojan TLS y TUN activos", profile.id, "TLS")
            ConnectionMode.UDP -> logSafe(LogLevel.SUCCESS, "Hysteria2/QUIC/TLS y TUN activos", profile.id, "CORE")
        }
        logSafe(LogLevel.DEBUG, "Xray Core ${tunnelManager.coreVersion()}", profile.id, "CORE")
    }

    private suspend fun maybeStartFloatingWindow() {
        if (!repository.floatingWindow.first()) return
        if (!PermissionHelper.hasOverlayPermission(this)) return
        startService(Intent(this, FloatingWindowService::class.java))
    }

    private fun connectedState(profile: VpnProfile): VpnConnectionState.Connected =
        VpnConnectionState.Connected(
            profile.name,
            if (profile.isLocked) "[OCULTO]" else profile.host,
            sessionConnectedSince
        )

    private fun buildNotification(state: VpnConnectionState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, GhostVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val (title, content) = when (state) {
            is VpnConnectionState.Connected -> "VPN protegida" to "${state.profileName} · túnel activo"
            is VpnConnectionState.Connecting -> "Iniciando conexión…" to state.profileName
            is VpnConnectionState.Reconnecting -> "Reconectando de forma segura" to "Intento ${state.attempt} · tráfico protegido"
            is VpnConnectionState.Disconnecting -> "Desconectando…" to "Cerrando el túnel de forma segura"
            is VpnConnectionState.Error -> "Conexión VPN rechazada" to state.message
            VpnConnectionState.Disconnected -> "Ghost Nexora VPN" to "Desconectado"
        }
        return NotificationCompat.Builder(this, GhostNexoraApp.CHANNEL_VPN_STATUS)
            .setSmallIcon(R.drawable.ic_vpn_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppIntent)
            .setOngoing(state !is VpnConnectionState.Disconnected)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (state is VpnConnectionState.Connected ||
                    state is VpnConnectionState.Reconnecting ||
                    state is VpnConnectionState.Error
                ) {
                    addAction(R.drawable.ic_vpn_notification, "Desconectar", disconnectIntent)
                }
            }
            .build()
    }

    private fun updateNotification(state: VpnConnectionState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(GhostNexoraApp.NOTIF_ID_VPN, buildNotification(state))
    }

    private fun friendlyConnectionError(error: Throwable, profile: VpnProfile): String {
        val failure = ConnectionErrorCatalog.classify(error, profile)
        serviceScope.launch { logSafe(LogLevel.ERROR, failure.logMessage(), profile.id, failure.stage) }
        return "${failure.userMessage()} [${profile.connectionModeLabel}]"
    }

    private suspend fun logConnectionSnapshot(profile: VpnProfile) {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "unknown" }
        logSafe(
            LogLevel.INFO,
            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.SDK_INT} · $abi",
            profile.id,
            "SYSTEM"
        )
        logSafe(
            LogLevel.INFO,
            "Ghost Nexora VPN ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            profile.id,
            "SYSTEM"
        )
        logSafe(LogLevel.INFO, "Red de salida: $physicalNetworkType", profile.id, "NETWORK")
        if (profile.isLocked) {
            logSafe(
                LogLevel.INFO,
                "Configuración bloqueada · servidor, método y parámetros [OCULTOS]",
                profile.id,
                "PROTECTED"
            )
            return
        }
        logSafe(LogLevel.INFO, "Servidor ${profile.host}:${profile.port}", profile.id, "NETWORK")
        if (profile.selectedMode.usesTls || profile.sslEnabled) {
            val verification = if (profile.selectedMode.isSsh) {
                profile.selectedTlsVerificationMode.label
            } else {
                "TLS estricto"
            }
            logSafe(
                LogLevel.INFO,
                "TLS/SNI ${profile.sni.ifBlank { profile.host }} · $verification",
                profile.id,
                "TLS"
            )
        }
        if (profile.selectedMode.requiresProxy) {
            logSafe(
                LogLevel.INFO,
                "Proxy ${profile.proxy.type.uppercase()} ${profile.proxy.host}:${profile.proxy.port}",
                profile.id,
                "NETWORK"
            )
        }
    }

    private fun registerPhysicalNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching { connectivityManager.registerNetworkCallback(request, networkCallback) }
    }

    private fun registerUnderlyingNetwork(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return
        underlyingNetwork = network
        physicalNetworkAvailable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        physicalNetworkType = networkType(capabilities)
        runCatching { setUnderlyingNetworks(arrayOf(network)) }
    }

    private fun findUsablePhysicalNetwork(excluding: Network? = null): Network? {
        val active = connectivityManager.activeNetwork
        if (active != null && active != excluding && isUsablePhysicalNetwork(active)) {
            return active
        }
        return underlyingNetwork?.takeIf { network ->
            network != excluding && isUsablePhysicalNetwork(network)
        }
    }

    private fun isUsablePhysicalNetwork(network: Network): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }

    private fun networkType(capabilities: NetworkCapabilities): String = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Red física"
    }

    private fun measureTcpLatency(host: String, port: Int): Long {
        val start = System.nanoTime()
        return runCatching {
            Socket().use { socket ->
                check(protect(socket)) { "Android rechazó protect(Socket) para la medición TCP" }
                underlyingNetwork?.bindSocket(socket)
                socket.connect(InetSocketAddress(host, port), 2_000)
            }
            ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
        }.getOrDefault(0L)
    }

    private fun startPreparingForeground(label: String) {
        val state = VpnConnectionState.Connecting(label)
        publishState(state)
        startForeground(GhostNexoraApp.NOTIF_ID_VPN, buildNotification(state))
    }

    private fun publishState(state: VpnConnectionState) {
        updateState(state)
        runCatching { sendBroadcast(VpnServiceContract.stateIntent(this, state)) }
    }

    private fun publishTraffic(stats: VpnTrafficStats) {
        _trafficStats.value = stats
        runCatching { sendBroadcast(VpnServiceContract.trafficIntent(this, stats)) }
    }

    private fun broadcastCurrentRuntime() {
        runCatching {
            sendBroadcast(VpnServiceContract.stateIntent(this, connectionState.value))
            sendBroadcast(VpnServiceContract.trafficIntent(this, trafficStats.value))
        }
    }

    private suspend fun logSafe(
        level: LogLevel,
        message: String,
        profileId: String? = null,
        tag: String = "VPN"
    ) {
        val protectedProfile = logRedactionProfile
            ?.takeIf { profileId == null || it.id == profileId }
        val safeMessage = protectedProfile
            ?.let { redactLockedLog(message, it) }
            ?: message
        repository.log(
            level,
            safeMessage,
            profileId,
            if (protectedProfile != null && tag in SENSITIVE_LOG_TAGS) {
                "PROTECTED"
            } else {
                tag
            }
        )
    }

    private fun redactLockedLog(message: String, profile: VpnProfile): String {
        var redacted = message
        val exactValues = listOf(
            "${profile.host}:${profile.port}",
            "${profile.proxy.host}:${profile.proxy.port}",
            profile.payload,
            profile.password,
            profile.username,
            profile.sni,
            profile.proxy.host,
            profile.host,
            profile.selectedMode.label,
            profile.connectionMode,
            profile.method
        )
            .filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending(String::length)
        exactValues.forEach { value ->
            redacted = redacted.replace(value, "[OCULTO]", ignoreCase = true)
        }
        return redacted
            .replace(Regex(""":${profile.port}\b"""), ":[OCULTO]")
            .replace(Regex("""(?i)\b(SNI|Host|Proxy|Payload)\s+[^\s·|,;]+""")) {
                "${it.groupValues[1]} [OCULTO]"
            }
    }

    private data class PendingTunnelLog(
        val profileId: String?,
        val event: TunnelLogEvent
    )
}
