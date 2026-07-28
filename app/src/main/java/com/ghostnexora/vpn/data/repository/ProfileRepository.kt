package com.ghostnexora.vpn.data.repository

import com.ghostnexora.vpn.data.local.DataStoreManager
import com.ghostnexora.vpn.data.local.LogDao
import com.ghostnexora.vpn.data.local.ProfileDao
import com.ghostnexora.vpn.data.model.AppRoutingMode
import com.ghostnexora.vpn.data.model.AppRoutingPreferences
import com.ghostnexora.vpn.data.model.DnsMode
import com.ghostnexora.vpn.data.model.IpMode
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.data.model.NetworkPreferences
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.security.LocalSecretCipher
import com.ghostnexora.vpn.security.LogSanitizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val logDao: LogDao,
    private val dataStore: DataStoreManager,
    private val secretCipher: LocalSecretCipher
) {
    val allProfiles: Flow<List<VpnProfile>> = profileDao.getAllProfiles().map(::revealAll)
    val enabledProfiles: Flow<List<VpnProfile>> = profileDao.getEnabledProfiles().map(::revealAll)
    val favoriteProfiles: Flow<List<VpnProfile>> = profileDao.getFavoriteProfiles().map(::revealAll)
    val profileCount: Flow<Int> = profileDao.getProfileCount()

    fun searchProfiles(query: String): Flow<List<VpnProfile>> =
        profileDao.searchProfiles(query).map(::revealAll)

    fun observeProfile(id: String): Flow<VpnProfile?> =
        profileDao.observeProfileById(id).map { it?.let(secretCipher::reveal) }

    suspend fun getProfileById(id: String): VpnProfile? =
        profileDao.getProfileById(id)?.let(secretCipher::reveal)

    suspend fun getLastUsedProfile(): VpnProfile? =
        profileDao.getLastUsedProfile()?.let(secretCipher::reveal)

    suspend fun saveProfile(profile: VpnProfile) {
        profileDao.insertProfile(secretCipher.protect(profile))
        log(LogLevel.INFO, "Perfil guardado: ${profile.name}", profile.id, tag = "PROFILE")
    }

    suspend fun saveProfiles(profiles: List<VpnProfile>) {
        profileDao.insertProfiles(profiles.map(secretCipher::protect))
        log(LogLevel.INFO, "${profiles.size} perfiles importados", tag = "PROFILE")
    }

    suspend fun updateProfile(profile: VpnProfile) {
        profileDao.updateProfile(secretCipher.protect(profile))
        log(LogLevel.INFO, "Perfil actualizado: ${profile.name}", profile.id, tag = "PROFILE")
    }

    suspend fun deleteProfile(profile: VpnProfile) {
        profileDao.deleteProfileById(profile.id)
        log(LogLevel.WARNING, "Perfil eliminado: ${profile.name}", tag = "PROFILE")
    }

    suspend fun deleteAllProfiles() {
        profileDao.deleteAllProfiles()
        log(LogLevel.WARNING, "Todos los perfiles eliminados", tag = "PROFILE")
    }

    suspend fun setFavorite(id: String, isFavorite: Boolean) = profileDao.setFavorite(id, isFavorite)
    suspend fun setEnabled(id: String, enabled: Boolean) = profileDao.setEnabled(id, enabled)

    suspend fun markLastUsed(id: String) {
        profileDao.updateLastUsed(id, Instant.now().toString())
    }

    /** Migra perfiles creados por versiones antiguas que guardaban secretos en texto claro. */
    suspend fun migrateLegacySecrets(): Int {
        val legacy = profileDao.getAllProfilesOnce().filterNot(secretCipher::isProtected)
        legacy.forEach { profileDao.insertProfile(secretCipher.protect(secretCipher.reveal(it))) }
        if (legacy.isNotEmpty()) {
            log(LogLevel.SUCCESS, "${legacy.size} perfiles migrados a almacenamiento cifrado", tag = "SECURITY")
        }
        return legacy.size
    }

    val allLogs: Flow<List<LogEntry>> = logDao.getAllLogs()
    fun getRecentLogs(limit: Int = 50): Flow<List<LogEntry>> = logDao.getRecentLogs(limit)
    fun getLogsForProfile(profileId: String): Flow<List<LogEntry>> = logDao.getLogsForProfile(profileId)

    suspend fun clearLogs() = logDao.clearAllLogs()
    suspend fun trimLogs(maxEntries: Int = 500) = logDao.keepOnly(maxEntries)

    suspend fun insertLog(entry: LogEntry) {
        logDao.insertLog(entry.copy(message = LogSanitizer.sanitize(entry.message)))
        trimLogsIfNeeded()
    }

    suspend fun log(
        level: LogLevel,
        message: String,
        profileId: String? = null,
        tag: String = "GhostVPN"
    ) {
        insertLog(
            LogEntry(
                level = level,
                tag = tag.take(32),
                message = LogSanitizer.sanitize(message),
                profileId = profileId,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    val activeProfileId: Flow<String> = dataStore.activeProfileId
    val autoReconnect: Flow<Boolean> = dataStore.autoReconnect
    val killSwitch: Flow<Boolean> = dataStore.killSwitch
    val floatingWindow: Flow<Boolean> = dataStore.floatingWindowEnabled
    val notifications: Flow<Boolean> = dataStore.notificationsEnabled
    val darkTheme: Flow<Boolean> = dataStore.darkTheme
    val reconnectOnBoot: Flow<Boolean> = dataStore.reconnectOnBoot
    val showFloatingHint: Flow<Boolean> = dataStore.showFloatingHint
    val logsMaxEntries: Flow<Int> = dataStore.logsMaxEntries
    val isFirstLaunch: Flow<Boolean> = dataStore.isFirstLaunch
    val networkPreferences: Flow<NetworkPreferences> = dataStore.networkPreferences
    val appRoutingPreferences: Flow<AppRoutingPreferences> = dataStore.appRoutingPreferences

    suspend fun setActiveProfileId(id: String) = dataStore.setActiveProfileId(id)
    suspend fun clearActiveProfile() = dataStore.clearActiveProfile()
    suspend fun setAutoReconnect(value: Boolean) = dataStore.setAutoReconnect(value)
    suspend fun setKillSwitch(value: Boolean) = dataStore.setKillSwitch(value)
    suspend fun setFloatingWindow(value: Boolean) = dataStore.setFloatingWindowEnabled(value)
    suspend fun setNotifications(value: Boolean) = dataStore.setNotificationsEnabled(value)
    suspend fun setReconnectOnBoot(value: Boolean) = dataStore.setReconnectOnBoot(value)
    suspend fun setShowFloatingHint(value: Boolean) = dataStore.setShowFloatingHint(value)
    suspend fun setLogsMaxEntries(value: Int) = dataStore.setLogsMaxEntries(value)
    suspend fun setFirstLaunchDone() = dataStore.setFirstLaunchDone()
    suspend fun setIpMode(value: IpMode) = dataStore.setIpMode(value)
    suspend fun setTunMtu(value: Int) = dataStore.setTunMtu(value)
    suspend fun setDnsMode(value: DnsMode) = dataStore.setDnsMode(value)
    suspend fun setCustomDns(primary: String, secondary: String) = dataStore.setCustomDns(primary, secondary)
    suspend fun setReconnectMaxAttempts(value: Int) = dataStore.setReconnectMaxAttempts(value)
    suspend fun setAppRoutingMode(value: AppRoutingMode) = dataStore.setAppRoutingMode(value)
    suspend fun setAppRoutingPackages(value: Set<String>) = dataStore.setAppRoutingPackages(value)

    suspend fun clearAllData() {
        profileDao.deleteAllProfiles()
        logDao.clearAllLogs()
        dataStore.clearAll()
    }

    private fun revealAll(profiles: List<VpnProfile>): List<VpnProfile> = profiles.map(secretCipher::reveal)

    private suspend fun trimLogsIfNeeded() {
        val maxEntries = logsMaxEntries.first().coerceIn(100, 5_000)
        logDao.keepOnly(maxEntries)
    }
}