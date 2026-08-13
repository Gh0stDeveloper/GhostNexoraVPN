package com.ghostnexora.vpn.data.repository

import com.ghostnexora.vpn.data.model.AppRoutingPreferences
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.data.model.NetworkPreferences
import com.ghostnexora.vpn.data.model.VpnProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Blocking persistence facade consumed by the Java VPN service from its own
 * dedicated worker thread. This class contains no socket/TUN/protocol logic.
 */
class VpnRepositoryBridge(private val repository: ProfileRepository) {
    fun getProfileForConnection(id: String): VpnProfile? = runBlocking {
        repository.getProfileForConnection(id)
    }

    fun setVpnDesiredConnected(value: Boolean) = runBlocking {
        repository.setVpnDesiredConnected(value)
    }

    fun resetVpnRecovery() = runBlocking { repository.resetVpnRecovery() }
    fun markLastUsed(id: String) = runBlocking { repository.markLastUsed(id) }
    fun networkPreferences(): NetworkPreferences = runBlocking { repository.networkPreferences.first() }
    fun appRoutingPreferences(): AppRoutingPreferences = runBlocking { repository.appRoutingPreferences.first() }
    fun activeProfileId(): String = runBlocking { repository.activeProfileId.first() }
    fun autoReconnect(): Boolean = runBlocking { repository.autoReconnect.first() }
    fun killSwitch(): Boolean = runBlocking { repository.killSwitch.first() }
    fun floatingWindow(): Boolean = runBlocking { repository.floatingWindow.first() }
    fun vpnDesiredConnected(): Boolean = runBlocking { repository.vpnDesiredConnected.first() }
    fun claimVpnRecoveryAttempt(): Int? = runBlocking { repository.claimVpnRecoveryAttempt() }

    fun log(level: LogLevel, message: String, profileId: String?, tag: String) = runBlocking {
        repository.log(level, message, profileId, tag)
    }
}
