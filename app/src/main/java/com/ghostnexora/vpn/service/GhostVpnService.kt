package com.ghostnexora.vpn.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.ghostnexora.vpn.BuildConfig
import com.ghostnexora.vpn.GhostNexoraApp
import com.ghostnexora.vpn.R
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.data.model.VpnConnectionState
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.tunnel.TunnelManager
import com.ghostnexora.vpn.tunnel.TunnelRuntime
import com.ghostnexora.vpn.ui.MainActivity
import com.ghostnexora.vpn.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val tunnelManager by lazy {
        TunnelManager(applicationContext) { status ->
            serviceScope.launch {
                logSafe(LogLevel.DEBUG, status, activeProfile?.id)
            }
        }
    }

    private val binder = GhostVpnBinder()

    companion object {
        const val ACTION_CONNECT = "com.ghostnexora.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.ghostnexora.vpn.DISCONNECT"
        const val EXTRA_PROFILE_ID = "extra_profile_id"

        private val _connectionState = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val connectionState: StateFlow<VpnConnectionState> = _connectionState.asStateFlow()

        fun updateState(state: VpnConnectionState) {
            _connectionState.value = state
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId.isNullOrBlank()) {
                    serviceScope.launch {
                        logSafe(LogLevel.ERROR, "No se especificó un perfil")
                        updateState(VpnConnectionState.Error("Sin perfil especificado"))
                        stopSelf()
                    }
                } else {
                    serviceScope.launch { handleConnect(profileId) }
                }
            }

            ACTION_DISCONNECT -> serviceScope.launch { handleDisconnect() }
            else -> serviceScope.launch { handleSystemRestart() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        serviceScope.launch {
            logSafe(LogLevel.WARNING, "Permiso VPN revocado por Android", activeProfile?.id)
            handleDisconnect()
        }
    }

    override fun onDestroy() {
        cleanupTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handleConnect(profileId: String) = connectionMutex.withLock {
        val profile = repository.getProfileById(profileId)
        if (profile == null) {
            updateState(VpnConnectionState.Error("Perfil no encontrado"))
            logSafe(LogLevel.ERROR, "Perfil no encontrado")
            return@withLock
        }

        cleanupTunnel()
        activeProfile = profile
        updateState(VpnConnectionState.Connecting(profile.name))
        startForeground(
            GhostNexoraApp.NOTIF_ID_VPN,
            buildNotification(VpnConnectionState.Connecting(profile.name))
        )

        try {
            validateProfile(profile)
            logSafe(LogLevel.INFO, "Iniciando ${profile.connectionModeLabel}", profile.id)
            logConnectionSnapshot(profile)

            val tun = buildTunInterface(profile)
                ?: error("Android no pudo establecer la interfaz VPN")
            tunInterface = tun
            logSafe(
                LogLevel.INFO,
                "TUN activo · IPv4/IPv6 · rutas 0.0.0.0/0 y ::/0",
                profile.id
            )

            tunnelRuntime = tunnelManager.start(profile, tun.fd)
            logTransportReady(profile)

            repository.markLastUsed(profile.id)
            val connectedState = VpnConnectionState.Connected(
                profileName = profile.name,
                serverIp = profile.host,
                connectedSince = System.currentTimeMillis()
            )
            updateState(connectedState)
            updateNotification(connectedState)
            logSafe(LogLevel.SUCCESS, "Conexión VPN establecida y tráfico enrutado", profile.id)

            maybeStartFloatingWindow()
        } catch (error: Throwable) {
            val message = friendlyConnectionError(error, profile)
            cleanupTunnel()
            updateState(VpnConnectionState.Error(message))
            updateNotification(VpnConnectionState.Error(message))
            logSafe(LogLevel.ERROR, message, profile.id)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun handleDisconnect() = connectionMutex.withLock {
        val profileId = activeProfile?.id
        if (tunnelRuntime == null && tunInterface == null) {
            updateState(VpnConnectionState.Disconnected)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return@withLock
        }

        updateState(VpnConnectionState.Disconnecting)
        updateNotification(VpnConnectionState.Disconnecting)
        logSafe(LogLevel.INFO, "Cerrando túnel y sesión de transporte", profileId)

        cleanupTunnel()
        stopService(Intent(this, FloatingWindowService::class.java))
        activeProfile = null
        updateState(VpnConnectionState.Disconnected)
        logSafe(LogLevel.SUCCESS, "VPN desconectada", profileId)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun handleSystemRestart() {
        val profileId = repository.activeProfileId.first()
        val shouldReconnect = repository.autoReconnect.first()
        if (shouldReconnect && profileId.isNotBlank()) {
            logSafe(LogLevel.INFO, "Reconexión automática solicitada")
            handleConnect(profileId)
        } else {
            stopSelf()
        }
    }

    private fun cleanupTunnel() {
        runCatching { tunnelManager.stop(tunnelRuntime) }
        tunnelRuntime = null
        runCatching { tunInterface?.close() }
        tunInterface = null
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
            if (profile.sslEnabled) {
                require(profile.sni.isNotBlank()) { "V2Ray con TLS/Reality requiere SNI" }
            }
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

    private fun buildTunInterface(profile: VpnProfile): ParcelFileDescriptor? = try {
        val builder = Builder()
            .setSession(profile.name)
            .setMtu(1500)
            .addAddress("10.20.0.2", 30)
            .addAddress("fd00:20::2", 126)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("2606:4700:4700::1111")
            .setBlocking(true)

        // El proceso de la aplicación debe salir por la red física para que el
        // core y los sockets SSH no sean recapturados por su propio TUN.
        runCatching { builder.addDisallowedApplication(packageName) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        builder.establish()
    } catch (error: Throwable) {
        serviceScope.launch {
            logSafe(LogLevel.ERROR, "Error creando TUN: ${error.message}", profile.id)
        }
        null
    }

    private suspend fun logTransportReady(profile: VpnProfile) {
        when (profile.selectedMode) {
            ConnectionMode.SSH_DIRECT ->
                logSafe(LogLevel.SUCCESS, "SSH autenticado · bridge SOCKS/TUN activo", profile.id)

            ConnectionMode.SSL_SNI ->
                logSafe(LogLevel.SUCCESS, "TLS validado · SNI ${profile.sni} · SSH activo", profile.id)

            ConnectionMode.SSH_PAYLOAD ->
                logSafe(LogLevel.SUCCESS, "Payload aceptado · SSH/TUN activo", profile.id)

            ConnectionMode.SSH_PAYLOAD_SSL ->
                logSafe(LogLevel.SUCCESS, "TLS + payload + SSH activos", profile.id)

            ConnectionMode.SSH_PROXY ->
                logSafe(LogLevel.SUCCESS, "Proxy ${profile.proxy.host}:${profile.proxy.port} + SSH activos", profile.id)

            ConnectionMode.SSH_PAYLOAD_PROXY ->
                logSafe(LogLevel.SUCCESS, "Proxy + payload + SSH activos", profile.id)

            ConnectionMode.SSH_PAYLOAD_PROXY_SSL ->
                logSafe(LogLevel.SUCCESS, "Proxy + payload + TLS + SSH activos", profile.id)

            ConnectionMode.V2RAY ->
                logSafe(LogLevel.SUCCESS, "V2Ray/Xray Core activo", profile.id)

            ConnectionMode.TROJAN ->
                logSafe(LogLevel.SUCCESS, "Trojan TLS activo · certificado verificado", profile.id)

            ConnectionMode.UDP ->
                logSafe(LogLevel.SUCCESS, "UDP cifrado Hysteria2/QUIC activo", profile.id)
        }
        logSafe(LogLevel.DEBUG, "Xray Core ${tunnelManager.coreVersion()}", profile.id)
    }

    private suspend fun maybeStartFloatingWindow() {
        if (!repository.floatingWindow.first()) return
        if (!PermissionHelper.hasOverlayPermission(this)) return
        startService(Intent(this, FloatingWindowService::class.java))
    }

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
            is VpnConnectionState.Connected -> "VPN protegida" to "${state.profileName} · ${state.serverIp}"
            is VpnConnectionState.Connecting -> "Conectando…" to state.profileName
            is VpnConnectionState.Disconnecting -> "Desconectando…" to "Cerrando el túnel de forma segura"
            is VpnConnectionState.Error -> "Error de conexión" to state.message
            VpnConnectionState.Disconnected -> "Ghost Nexora VPN" to "Desconectado"
        }

        return NotificationCompat.Builder(this, GhostNexoraApp.CHANNEL_VPN_STATUS)
            .setSmallIcon(R.drawable.ic_vpn_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppIntent)
            .setOngoing(state !is VpnConnectionState.Disconnected && state !is VpnConnectionState.Error)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (state is VpnConnectionState.Connected) {
                    addAction(R.drawable.ic_vpn_notification, "Desconectar", disconnectIntent)
                }
            }
            .build()
    }

    private fun updateNotification(state: VpnConnectionState) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(GhostNexoraApp.NOTIF_ID_VPN, buildNotification(state))
    }

    private fun friendlyConnectionError(error: Throwable, profile: VpnProfile): String {
        val raw = error.message.orEmpty()
        val lower = raw.lowercase()
        val base = when {
            lower.contains("auth fail") || lower.contains("autenticación ssh") ->
                "Autenticación SSH fallida. Verifica usuario, contraseña y puerto."

            lower.contains("hostkey") || lower.contains("host key") ->
                "La identidad SSH del servidor no coincide con la guardada. Se bloqueó la conexión por seguridad."

            lower.contains("certificate") || lower.contains("certificado") || lower.contains("trust anchor") ->
                "TLS rechazó el certificado o el SNI del servidor."

            lower.contains("timeout") || lower.contains("timed out") ->
                "Tiempo de espera agotado al conectar con el servidor."

            lower.contains("libv2ray") || lower.contains("xray core") || lower.contains("go_seq") ->
                "No se pudo iniciar Xray Core. Verifica que libv2ray.aar esté incluido en la compilación."

            else -> raw.ifBlank { error.javaClass.simpleName.ifBlank { "Error desconocido" } }
        }
        return "$base [${profile.connectionModeLabel}]"
    }

    private suspend fun logConnectionSnapshot(profile: VpnProfile) {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "unknown" }
        logSafe(LogLevel.INFO, "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.SDK_INT} · $abi", profile.id)
        logSafe(LogLevel.INFO, "Ghost Nexora VPN ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", profile.id)
        logSafe(LogLevel.INFO, describeNetworkState(), profile.id)
        logSafe(LogLevel.INFO, "Servidor ${profile.host}:${profile.port}", profile.id)
        if (profile.selectedMode.usesTls || profile.sslEnabled) {
            logSafe(LogLevel.INFO, "TLS/SNI ${profile.sni.ifBlank { profile.host }} · verificación estricta", profile.id)
        }
        if (profile.selectedMode.requiresProxy) {
            logSafe(LogLevel.INFO, "Proxy ${profile.proxy.type.uppercase()} ${profile.proxy.host}:${profile.proxy.port}", profile.id)
        }
    }

    private fun describeNetworkState(): String {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val transport = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Datos móviles"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Red desconocida"
        }
        return "Red de salida: $transport"
    }

    private suspend fun logSafe(
        level: LogLevel,
        message: String,
        profileId: String? = null
    ) = repository.log(level, message, profileId, tag = "Ghost Nexora VPN")

    inner class GhostVpnBinder : Binder() {
        fun getService(): GhostVpnService = this@GhostVpnService
    }
}
