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
import android.net.TrafficStats
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.ghostnexora.vpn.BuildConfig
import com.ghostnexora.vpn.GhostNexoraApp
import com.ghostnexora.vpn.R
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.data.model.VpnConnectionState
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.model.VpnTrafficStats
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.tunnel.StableXrayConfigFactory
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var reconnectJob: Job? = null
    private var healthJob: Job? = null
    private var statsJob: Job? = null
    private var intentionalDisconnect = false
    private var sessionConnectedSince = 0L
    private var reconnectCount = 0
    private var baselineRx = 0L
    private var baselineTx = 0L
    private var lastRx = 0L
    private var lastTx = 0L

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
            serviceScope.launch { logSafe(LogLevel.DEBUG, status, activeProfile?.id, tag = "CORE") }
        }
    }

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

    private val binder = GhostVpnBinder()

    companion object {
        const val ACTION_CONNECT = "com.ghostnexora.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.ghostnexora.vpn.DISCONNECT"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_PREFLIGHT_AT = "extra_preflight_at"

        private const val PREFLIGHT_VALIDITY_MS = 90_000L
        private val RECONNECT_DELAYS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)

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
        val initialNetwork = findUsablePhysicalNetwork()
        if (initialNetwork != null) registerUnderlyingNetwork(initialNetwork)
        registerPhysicalNetworkCallback()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                val preflightAt = intent.getLongExtra(EXTRA_PREFLIGHT_AT, 0L)
                if (profileId.isNullOrBlank()) {
                    serviceScope.launch {
                        logSafe(LogLevel.ERROR, "No se especificó un perfil", tag = "VPN")
                        updateState(VpnConnectionState.Error("Sin perfil especificado"))
                        stopSelf()
                    }
                } else {
                    serviceScope.launch { handleConnect(profileId, preflightAt) }
                }
            }

            ACTION_DISCONNECT -> serviceScope.launch { handleDisconnect() }
            else -> serviceScope.launch { handleSystemRestart() }
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
        healthJob?.cancel()
        statsJob?.cancel()
        cleanupTunnel(closeTun = true)
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        runCatching { setUnderlyingNetworks(null) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handleConnect(profileId: String, preflightAt: Long = 0L) = connectionMutex.withLock {
        val profile = repository.getProfileById(profileId)
        if (profile == null) {
            updateState(VpnConnectionState.Error("Perfil no encontrado"))
            logSafe(LogLevel.ERROR, "Perfil no encontrado", tag = "VPN")
            return@withLock
        }

        intentionalDisconnect = false
        reconnectJob?.cancel()
        healthJob?.cancel()
        statsJob?.cancel()
        cleanupTunnel(closeTun = true)
        activeProfile = profile
        reconnectCount = 0
        sessionConnectedSince = System.currentTimeMillis()
        updateState(VpnConnectionState.Connecting(profile.name))
        startForeground(
            GhostNexoraApp.NOTIF_ID_VPN,
            buildNotification(VpnConnectionState.Connecting(profile.name))
        )

        try {
            validateProfile(profile)
            ensurePhysicalNetwork()
            logSafe(LogLevel.INFO, "Iniciando ${profile.connectionModeLabel}", profile.id, "VPN")
            logConnectionSnapshot(profile)

            val preflightAge = SystemClock.elapsedRealtime() - preflightAt
            val hasFreshPreflight = preflightAt > 0L && preflightAge in 0L..PREFLIGHT_VALIDITY_MS
            if (hasFreshPreflight) {
                logSafe(LogLevel.INFO, "Validación previa vigente; preparando TUN", profile.id, "NETWORK")
            } else {
                logSafe(
                    LogLevel.INFO,
                    "Comprobando acceso a Internet del servidor antes de crear el TUN",
                    profile.id,
                    "NETWORK"
                )
                val preflight = tunnelManager.verify(profile)
                logSafe(
                    LogLevel.SUCCESS,
                    "Servidor validado antes del TUN · ${preflight.latencyMs} ms",
                    profile.id,
                    "NETWORK"
                )
            }

            val tun = buildTunInterface(profile) ?: error("Android no pudo establecer la interfaz VPN")
            tunInterface = tun
            underlyingNetwork?.let { network -> runCatching { setUnderlyingNetworks(arrayOf(network)) } }
            logSafe(LogLevel.INFO, "TUN activo · MTU ${StableXrayConfigFactory.TUN_MTU} · IPv4/IPv6 · rutas completas", profile.id, "NETWORK")

            tunnelRuntime = tunnelManager.start(profile, tun.fd)
            logTransportReady(profile)
            repository.markLastUsed(profile.id)

            val connected = connectedState(profile)
            updateState(connected)
            updateNotification(connected)
            logSafe(
                LogLevel.SUCCESS,
                "Conexión VPN verificada · Internet disponible y tráfico enrutado",
                profile.id,
                "VPN"
            )
            resetTrafficBaseline(profile)
            startStatsTicker(profile)
            startHealthMonitor(profile)
            maybeStartFloatingWindow()
        } catch (error: Throwable) {
            val message = friendlyConnectionError(error, profile)
            cleanupTunnel(closeTun = true)
            activeProfile = null
            updateState(VpnConnectionState.Error(message, profile.name))
            updateNotification(VpnConnectionState.Error(message, profile.name))
            logSafe(LogLevel.ERROR, message, profile.id, "VPN")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun handleDisconnect() = connectionMutex.withLock {
        intentionalDisconnect = true
        reconnectJob?.cancel()
        healthJob?.cancel()
        statsJob?.cancel()
        val profileId = activeProfile?.id

        updateState(VpnConnectionState.Disconnecting)
        updateNotification(VpnConnectionState.Disconnecting)
        logSafe(LogLevel.INFO, "Cerrando túnel y sesión de transporte", profileId, "VPN")

        cleanupTunnel(closeTun = true)
        stopService(Intent(this, FloatingWindowService::class.java))
        activeProfile = null
        sessionConnectedSince = 0L
        _trafficStats.value = VpnTrafficStats()
        updateState(VpnConnectionState.Disconnected)
        logSafe(LogLevel.SUCCESS, "VPN desconectada", profileId, "VPN")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun handleSystemRestart() {
        val profileId = repository.activeProfileId.first()
        val shouldReconnect = repository.autoReconnect.first()
        if (shouldReconnect && profileId.isNotBlank()) {
            logSafe(LogLevel.INFO, "Restaurando VPN después de reinicio del servicio", tag = "VPN")
            handleConnect(profileId, preflightAt = 0L)
        } else {
            stopSelf()
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
                updateState(VpnConnectionState.Error(message, profile.name))
                updateNotification(VpnConnectionState.Error(message, profile.name))
                logSafe(LogLevel.WARNING, "$reason · $message", profile.id, "NETWORK")
            } else {
                cleanupTunnel(closeTun = true)
                updateState(VpnConnectionState.Error("Conexión perdida", profile.name))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return
        }

        logSafe(LogLevel.WARNING, "$reason · iniciando reconexión protegida", profile.id, "NETWORK")
        var attempt = 0
        while (serviceScope.isActive && !intentionalDisconnect && tunInterface != null) {
            val waitMs = RECONNECT_DELAYS[attempt.coerceAtMost(RECONNECT_DELAYS.lastIndex)]
            val state = VpnConnectionState.Reconnecting(profile.name, attempt + 1, waitMs)
            updateState(state)
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
                    tunnelRuntime = tunnelManager.start(profile, tun.fd)
                }
            }

            if (result.isSuccess && tunnelManager.isAlive(tunnelRuntime)) {
                reconnectCount += 1
                val connected = connectedState(profile)
                updateState(connected)
                updateNotification(connected)
                logSafe(
                    LogLevel.SUCCESS,
                    "Reconexión verificada en intento ${attempt + 1}",
                    profile.id,
                    "NETWORK"
                )
                _trafficStats.value = _trafficStats.value.copy(reconnectCount = reconnectCount)
                startHealthMonitor(profile)
                return
            }

            val error = result.exceptionOrNull()
            logSafe(
                LogLevel.WARNING,
                "Intento ${attempt + 1} fallido: ${error?.message?.take(160).orEmpty()}",
                profile.id,
                "NETWORK"
            )
            attempt += 1
        }
    }

    private fun startHealthMonitor(profile: VpnProfile) {
        healthJob?.cancel()
        healthJob = serviceScope.launch {
            var ticks = 0
            while (isActive && !intentionalDisconnect) {
                delay(3_000L)
                if (connectionState.value !is VpnConnectionState.Connected) continue

                if (!tunnelManager.isAlive(tunnelRuntime)) {
                    logSafe(LogLevel.WARNING, "El transporte dejó de responder", profile.id, "CORE")
                    triggerReconnect("Fallo detectado en el transporte")
                    return@launch
                }

                ticks += 1
                if (ticks % 5 == 0) {
                    val internetCheck = runCatching { tunnelManager.verifyActive() }
                    if (internetCheck.isFailure) {
                        logSafe(
                            LogLevel.WARNING,
                            "El core sigue activo pero el servidor ya no entrega Internet",
                            profile.id,
                            "CORE"
                        )
                        triggerReconnect("Salida de Internet perdida")
                        return@launch
                    }
                }
            }
        }
    }

    private fun resetTrafficBaseline(profile: VpnProfile) {
        val rx = safeUidRxBytes()
        val tx = safeUidTxBytes()
        _trafficStats.value = VpnTrafficStats(
            receivedBytes = 0,
            sentBytes = 0,
            reconnectCount = reconnectCount,
            latencyMs = tunnelRuntime?.verifiedLatencyMs ?: 0L,
            networkType = physicalNetworkType,
            protocol = profile.connectionModeLabel
        )
        lastRx = rx
        lastTx = tx
        baselineRx = rx
        baselineTx = tx
    }

    private fun startStatsTicker(profile: VpnProfile) {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            var tick = 0
            while (isActive && !intentionalDisconnect) {
                delay(1_000L)
                val rx = safeUidRxBytes()
                val tx = safeUidTxBytes()
                val downSpeed = (rx - lastRx).coerceAtLeast(0)
                val upSpeed = (tx - lastTx).coerceAtLeast(0)
                lastRx = rx
                lastTx = tx
                tick += 1
                val latency = if (tick % 10 == 0 && physicalNetworkAvailable) {
                    measureTcpLatency(profile.host, profile.port)
                } else {
                    _trafficStats.value.latencyMs
                }
                _trafficStats.value = VpnTrafficStats(
                    receivedBytes = (rx - baselineRx).coerceAtLeast(0),
                    sentBytes = (tx - baselineTx).coerceAtLeast(0),
                    downloadBytesPerSecond = if (tunnelManager.isAlive(tunnelRuntime)) downSpeed else 0,
                    uploadBytesPerSecond = if (tunnelManager.isAlive(tunnelRuntime)) upSpeed else 0,
                    reconnectCount = reconnectCount,
                    latencyMs = latency,
                    networkType = physicalNetworkType,
                    protocol = profile.connectionModeLabel
                )
            }
        }
    }

    private fun cleanupTunnel(closeTun: Boolean) {
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

    private fun buildTunInterface(profile: VpnProfile): ParcelFileDescriptor? = try {
        val builder = Builder()
            .setSession(profile.name)
            .setMtu(StableXrayConfigFactory.TUN_MTU)
            .addAddress("10.20.0.2", 30)
            .addAddress("fd00:20::2", 126)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("2606:4700:4700::1111")
            .setBlocking(true)
        runCatching { builder.addDisallowedApplication(packageName) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        builder.establish()
    } catch (error: Throwable) {
        serviceScope.launch {
            logSafe(LogLevel.ERROR, "Error creando TUN: ${error.message}", profile.id, "NETWORK")
        }
        null
    }

    private suspend fun logTransportReady(profile: VpnProfile) {
        when (profile.selectedMode) {
            ConnectionMode.SSH_DIRECT -> logSafe(LogLevel.SUCCESS, "SSH autenticado · bridge SOCKS/TUN activo", profile.id, "SSH")
            ConnectionMode.SSL_SNI -> logSafe(LogLevel.SUCCESS, "TLS validado · SSH activo", profile.id, "TLS")
            ConnectionMode.SSH_PAYLOAD -> logSafe(LogLevel.SUCCESS, "Payload aceptado · SSH/TUN activo", profile.id, "SSH")
            ConnectionMode.SSH_PAYLOAD_SSL -> logSafe(LogLevel.SUCCESS, "TLS + payload + SSH activos", profile.id, "TLS")
            ConnectionMode.SSH_PROXY -> logSafe(LogLevel.SUCCESS, "Proxy + SSH activos", profile.id, "SSH")
            ConnectionMode.SSH_PAYLOAD_PROXY -> logSafe(LogLevel.SUCCESS, "Proxy + payload + SSH activos", profile.id, "SSH")
            ConnectionMode.SSH_PAYLOAD_PROXY_SSL -> logSafe(LogLevel.SUCCESS, "Proxy + payload + TLS + SSH activos", profile.id, "TLS")
            ConnectionMode.V2RAY -> logSafe(LogLevel.SUCCESS, "V2Ray/Xray con salida a Internet verificada", profile.id, "CORE")
            ConnectionMode.TROJAN -> logSafe(LogLevel.SUCCESS, "Trojan TLS con salida verificada", profile.id, "TLS")
            ConnectionMode.UDP -> logSafe(LogLevel.SUCCESS, "Hysteria2/QUIC/TLS con salida verificada", profile.id, "CORE")
        }
        logSafe(LogLevel.DEBUG, "Xray Core ${tunnelManager.coreVersion()}", profile.id, "CORE")
    }

    private suspend fun maybeStartFloatingWindow() {
        if (!repository.floatingWindow.first()) return
        if (!PermissionHelper.hasOverlayPermission(this)) return
        startService(Intent(this, FloatingWindowService::class.java))
    }

    private fun connectedState(profile: VpnProfile): VpnConnectionState.Connected =
        VpnConnectionState.Connected(profile.name, profile.host, sessionConnectedSince)

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
            is VpnConnectionState.Connected -> "VPN protegida" to "${state.profileName} · Internet verificado"
            is VpnConnectionState.Connecting -> "Validando conexión…" to state.profileName
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
        val raw = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.takeIf(String::isNotBlank) }
            .joinToString(" · ")
            .take(260)
        val lower = raw.lowercase()
        val base = when {
            lower.contains("auth fail") || lower.contains("autenticación ssh") ->
                "Autenticación SSH fallida. Verifica usuario y contraseña."
            lower.contains("hostkey") || lower.contains("host key") ->
                "La identidad SSH del servidor cambió. Conexión bloqueada por seguridad."
            lower.contains("certificate") || lower.contains("certificado") || lower.contains("trust anchor") ->
                "TLS rechazó el certificado o el SNI del servidor."
            lower.contains("no entregan acceso") || lower.contains("no pudo entregar") || lower.contains("generate_204") ->
                "El perfil inició el core, pero el servidor no pudo entregar acceso a Internet. Revisa UUID/credenciales, SNI, Host, path y transporte."
            lower.contains("timeout") || lower.contains("timed out") || lower.contains("deadline exceeded") ->
                "El servidor no respondió a la prueba de Internet dentro del tiempo permitido."
            lower.contains("libv2ray") || lower.contains("xray core") || lower.contains("go_seq") ->
                "No se pudo iniciar Xray Core."
            else -> raw.ifBlank { error.javaClass.simpleName.ifBlank { "Error desconocido" } }
        }
        return "$base [${profile.connectionModeLabel}]"
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
        logSafe(LogLevel.INFO, "Servidor ${profile.host}:${profile.port}", profile.id, "NETWORK")
        if (profile.selectedMode.usesTls || profile.sslEnabled) {
            logSafe(
                LogLevel.INFO,
                "TLS/SNI ${profile.sni.ifBlank { profile.host }} · verificación estricta",
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
            Socket().use { socket -> socket.connect(InetSocketAddress(host, port), 2_000) }
            ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
        }.getOrDefault(0L)
    }

    private fun safeUidRxBytes(): Long = TrafficStats.getUidRxBytes(Process.myUid())
        .takeIf { it != TrafficStats.UNSUPPORTED.toLong() }
        ?: 0L

    private fun safeUidTxBytes(): Long = TrafficStats.getUidTxBytes(Process.myUid())
        .takeIf { it != TrafficStats.UNSUPPORTED.toLong() }
        ?: 0L

    private suspend fun logSafe(
        level: LogLevel,
        message: String,
        profileId: String? = null,
        tag: String = "VPN"
    ) = repository.log(level, message, profileId, tag)

    inner class GhostVpnBinder : Binder() {
        fun getService(): GhostVpnService = this@GhostVpnService
    }
}
