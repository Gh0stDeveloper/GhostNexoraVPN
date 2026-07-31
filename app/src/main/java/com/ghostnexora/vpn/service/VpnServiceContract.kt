package com.ghostnexora.vpn.service

import android.content.Context
import android.content.Intent
import com.ghostnexora.vpn.data.model.VpnConnectionState
import com.ghostnexora.vpn.data.model.VpnTrafficStats

/**
 * Explicit same-application IPC used between the UI process and the private
 * :vpn process. Only sanitized operational state is transmitted.
 */
object VpnServiceContract {
    const val ACTION_RUNTIME_STATE = "com.ghostnexora.vpn.RUNTIME_STATE"
    const val ACTION_TRAFFIC_STATS = "com.ghostnexora.vpn.TRAFFIC_STATS"
    const val ACTION_QUERY_RUNTIME = "com.ghostnexora.vpn.QUERY_RUNTIME"

    private const val EXTRA_KIND = "runtime_kind"
    private const val EXTRA_PROFILE_NAME = "runtime_profile_name"
    private const val EXTRA_MESSAGE = "runtime_message"
    private const val EXTRA_SERVER_IP = "runtime_server_ip"
    private const val EXTRA_CONNECTED_SINCE = "runtime_connected_since"
    private const val EXTRA_ATTEMPT = "runtime_attempt"
    private const val EXTRA_NEXT_RETRY_MS = "runtime_next_retry_ms"

    private const val EXTRA_RECEIVED = "traffic_received"
    private const val EXTRA_SENT = "traffic_sent"
    private const val EXTRA_DOWNLOAD_RATE = "traffic_download_rate"
    private const val EXTRA_UPLOAD_RATE = "traffic_upload_rate"
    private const val EXTRA_RECONNECTS = "traffic_reconnects"
    private const val EXTRA_LATENCY = "traffic_latency"
    private const val EXTRA_NETWORK_TYPE = "traffic_network_type"
    private const val EXTRA_PROTOCOL = "traffic_protocol"

    fun stateIntent(context: Context, state: VpnConnectionState): Intent {
        val snapshot = VpnStateSnapshot.from(state)
        return Intent(ACTION_RUNTIME_STATE)
            .setPackage(context.packageName)
            .putExtra(EXTRA_KIND, snapshot.kind.id)
            .putExtra(EXTRA_PROFILE_NAME, snapshot.profileName.take(MAX_PROFILE_NAME_LENGTH))
            .putExtra(EXTRA_MESSAGE, snapshot.message.take(MAX_MESSAGE_LENGTH))
            .putExtra(EXTRA_SERVER_IP, snapshot.serverIp.take(MAX_ENDPOINT_LENGTH))
            .putExtra(EXTRA_CONNECTED_SINCE, snapshot.connectedSince)
            .putExtra(EXTRA_ATTEMPT, snapshot.attempt)
            .putExtra(EXTRA_NEXT_RETRY_MS, snapshot.nextRetryMs)
    }

    fun stateFrom(intent: Intent): VpnConnectionState? {
        if (intent.action != ACTION_RUNTIME_STATE) return null
        val kind = VpnStateKind.fromId(intent.getStringExtra(EXTRA_KIND)) ?: return null
        return VpnStateSnapshot(
            kind = kind,
            profileName = intent.getStringExtra(EXTRA_PROFILE_NAME)
                .orEmpty()
                .take(MAX_PROFILE_NAME_LENGTH),
            message = intent.getStringExtra(EXTRA_MESSAGE)
                .orEmpty()
                .take(MAX_MESSAGE_LENGTH),
            serverIp = intent.getStringExtra(EXTRA_SERVER_IP)
                .orEmpty()
                .take(MAX_ENDPOINT_LENGTH),
            connectedSince = intent.getLongExtra(EXTRA_CONNECTED_SINCE, 0L),
            attempt = intent.getIntExtra(EXTRA_ATTEMPT, 1),
            nextRetryMs = intent.getLongExtra(EXTRA_NEXT_RETRY_MS, 0L)
        ).toState()
    }

    fun trafficIntent(context: Context, stats: VpnTrafficStats): Intent =
        Intent(ACTION_TRAFFIC_STATS)
            .setPackage(context.packageName)
            .putExtra(EXTRA_RECEIVED, stats.receivedBytes)
            .putExtra(EXTRA_SENT, stats.sentBytes)
            .putExtra(EXTRA_DOWNLOAD_RATE, stats.downloadBytesPerSecond)
            .putExtra(EXTRA_UPLOAD_RATE, stats.uploadBytesPerSecond)
            .putExtra(EXTRA_RECONNECTS, stats.reconnectCount)
            .putExtra(EXTRA_LATENCY, stats.latencyMs)
            .putExtra(EXTRA_NETWORK_TYPE, stats.networkType.take(MAX_LABEL_LENGTH))
            .putExtra(EXTRA_PROTOCOL, stats.protocol.take(MAX_LABEL_LENGTH))

    fun trafficFrom(intent: Intent): VpnTrafficStats? {
        if (intent.action != ACTION_TRAFFIC_STATS) return null
        return VpnTrafficStats(
            receivedBytes = intent.getLongExtra(EXTRA_RECEIVED, 0L).coerceAtLeast(0L),
            sentBytes = intent.getLongExtra(EXTRA_SENT, 0L).coerceAtLeast(0L),
            downloadBytesPerSecond = intent.getLongExtra(EXTRA_DOWNLOAD_RATE, 0L).coerceAtLeast(0L),
            uploadBytesPerSecond = intent.getLongExtra(EXTRA_UPLOAD_RATE, 0L).coerceAtLeast(0L),
            reconnectCount = intent.getIntExtra(EXTRA_RECONNECTS, 0).coerceAtLeast(0),
            latencyMs = intent.getLongExtra(EXTRA_LATENCY, 0L).coerceAtLeast(0L),
            networkType = intent.getStringExtra(EXTRA_NETWORK_TYPE)
                .orEmpty()
                .take(MAX_LABEL_LENGTH)
                .ifBlank { "--" },
            protocol = intent.getStringExtra(EXTRA_PROTOCOL)
                .orEmpty()
                .take(MAX_LABEL_LENGTH)
                .ifBlank { "--" }
        )
    }

    private const val MAX_PROFILE_NAME_LENGTH = 120
    private const val MAX_MESSAGE_LENGTH = 500
    private const val MAX_ENDPOINT_LENGTH = 256
    private const val MAX_LABEL_LENGTH = 80
}

enum class VpnStateKind(val id: String) {
    DISCONNECTED("disconnected"),
    CONNECTING("connecting"),
    RECONNECTING("reconnecting"),
    CONNECTED("connected"),
    DISCONNECTING("disconnecting"),
    ERROR("error");

    companion object {
        fun fromId(value: String?): VpnStateKind? = entries.firstOrNull { it.id == value }
    }
}

data class VpnStateSnapshot(
    val kind: VpnStateKind,
    val profileName: String = "",
    val message: String = "",
    val serverIp: String = "",
    val connectedSince: Long = 0L,
    val attempt: Int = 1,
    val nextRetryMs: Long = 0L
) {
    fun toState(): VpnConnectionState = when (kind) {
        VpnStateKind.DISCONNECTED -> VpnConnectionState.Disconnected
        VpnStateKind.CONNECTING -> VpnConnectionState.Connecting(profileName)
        VpnStateKind.RECONNECTING -> VpnConnectionState.Reconnecting(
            profileName = profileName,
            attempt = attempt.coerceAtLeast(1),
            nextRetryMs = nextRetryMs.coerceAtLeast(0L)
        )
        VpnStateKind.CONNECTED -> VpnConnectionState.Connected(
            profileName = profileName,
            serverIp = serverIp,
            connectedSince = connectedSince.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        VpnStateKind.DISCONNECTING -> VpnConnectionState.Disconnecting
        VpnStateKind.ERROR -> VpnConnectionState.Error(message, profileName)
    }

    companion object {
        fun from(state: VpnConnectionState): VpnStateSnapshot = when (state) {
            VpnConnectionState.Disconnected -> VpnStateSnapshot(VpnStateKind.DISCONNECTED)
            is VpnConnectionState.Connecting -> VpnStateSnapshot(
                kind = VpnStateKind.CONNECTING,
                profileName = state.profileName
            )
            is VpnConnectionState.Reconnecting -> VpnStateSnapshot(
                kind = VpnStateKind.RECONNECTING,
                profileName = state.profileName,
                attempt = state.attempt,
                nextRetryMs = state.nextRetryMs
            )
            is VpnConnectionState.Connected -> VpnStateSnapshot(
                kind = VpnStateKind.CONNECTED,
                profileName = state.profileName,
                serverIp = state.serverIp,
                connectedSince = state.connectedSince
            )
            VpnConnectionState.Disconnecting -> VpnStateSnapshot(VpnStateKind.DISCONNECTING)
            is VpnConnectionState.Error -> VpnStateSnapshot(
                kind = VpnStateKind.ERROR,
                profileName = state.profileName,
                message = state.message
            )
        }
    }
}
