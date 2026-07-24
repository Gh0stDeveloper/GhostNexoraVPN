package com.ghostnexora.vpn.data.model

sealed class VpnConnectionState {
    data object Disconnected : VpnConnectionState()
    data class Connecting(val profileName: String = "") : VpnConnectionState()
    data class Reconnecting(
        val profileName: String = "",
        val attempt: Int = 1,
        val nextRetryMs: Long = 0L
    ) : VpnConnectionState()
    data class Connected(
        val profileName: String = "",
        val serverIp: String = "",
        val connectedSince: Long = System.currentTimeMillis()
    ) : VpnConnectionState()
    data object Disconnecting : VpnConnectionState()
    data class Error(
        val message: String = "",
        val profileName: String = ""
    ) : VpnConnectionState()

    val isConnected: Boolean get() = this is Connected
    val isConnecting: Boolean get() = this is Connecting || this is Reconnecting
    val isDisconnected: Boolean get() = this is Disconnected
    val hasError: Boolean get() = this is Error

    fun label(): String = when (this) {
        is Disconnected -> "Desconectado"
        is Connecting -> "Conectando…"
        is Reconnecting -> "Reconectando…"
        is Connected -> "Protegido"
        is Disconnecting -> "Desconectando…"
        is Error -> "Error"
    }

    fun actionLabel(): String = when (this) {
        is Disconnected -> "Conectar"
        is Connecting -> "Cancelar"
        is Reconnecting -> "Desconectar"
        is Connected -> "Desconectar"
        is Disconnecting -> "Cerrando…"
        is Error -> "Reintentar"
    }
}
