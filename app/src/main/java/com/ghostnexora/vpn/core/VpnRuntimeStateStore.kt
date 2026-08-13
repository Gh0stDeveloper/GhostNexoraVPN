package com.ghostnexora.vpn.core

import com.ghostnexora.vpn.data.model.VpnConnectionState
import com.ghostnexora.vpn.data.model.VpnTrafficStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI-facing state holder only. It performs no VPN, TUN, socket, TLS or protocol
 * work; the Java GhostVpnService is the sole writer for the runtime state.
 */
object VpnRuntimeStateStore {
    private val mutableConnectionState = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
    private val mutableTrafficStats = MutableStateFlow(VpnTrafficStats())

    @JvmStatic
    val connectionState: StateFlow<VpnConnectionState> = mutableConnectionState.asStateFlow()

    @JvmStatic
    val trafficStats: StateFlow<VpnTrafficStats> = mutableTrafficStats.asStateFlow()

    @JvmStatic
    fun currentConnectionState(): VpnConnectionState = mutableConnectionState.value

    @JvmStatic
    fun currentTrafficStats(): VpnTrafficStats = mutableTrafficStats.value

    @JvmStatic
    fun publishConnectionState(state: VpnConnectionState) {
        mutableConnectionState.value = state
    }

    @JvmStatic
    fun publishTrafficStats(stats: VpnTrafficStats) {
        mutableTrafficStats.value = stats
    }
}
