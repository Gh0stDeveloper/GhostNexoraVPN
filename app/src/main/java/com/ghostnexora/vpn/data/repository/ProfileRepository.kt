package com.ghostnexora.vpn.data.repository

import com.ghostnexora.vpn.data.local.DataStoreManager
import com.ghostnexora.vpn.data.local.LogDao
import com.ghostnexora.vpn.data.local.ProfileDao
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.data.model.VpnProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio central de Ghost Nexora VPN.
 *
 * Actúa como única fuente de verdad (Single Source of Truth) para:
 * - Perfiles VPN (Room)
 * - Logs (Room)
 * - Preferencias de usuario (DataStore)
 *
 * Los ViewModels nunca acceden a DAOs directamente; siempre pasan por aquí.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val logDao: LogDao,
    private val dataStore: DataStoreManager
) {

    // ══════════════════════════════════════════════════════════════════════
    // PERFILES
    // ══════════════════════════════════════════════════════════════════════

    val allProfiles: Flow<List<VpnProfile>> = profileDao.getAllProfiles()
    val enabledProfiles: Flow<List<VpnProfile>> = profileDao.getEnabledProfiles()
    val favoriteProfiles: Flow<List<VpnProfile>> = profileDao.getFavoriteProfiles()
    val profileCount: Flow<Int> = profileDao.getProfileCount()

    fun searchProfiles(query: String): Flow<List<VpnProfile>> =
        profileDao.searchProfiles(query)

    fun observeProfile(id: String): Flow<VpnProfile?> =
        profileDao.observeProfileById(id)

    suspend fun getProfileById(id: String): VpnProfile? =
        profileDao.getProfileById(id)

    suspend fun getLastUsedProfile(): VpnProfile? =
        profileDao.getLastUsedProfile()

    suspend fun saveProfile(profile: VpnProfile) {
        profileDao.insertProfile(profile)
        log(LogLevel.INFO, "Perfil guardado: ${profile.name}", profile.id)
    }

    suspend fun saveProfiles(profiles: List<VpnProfile>) {
        profileDao.insertProfiles(profiles)
        log(LogLevel.INFO, "${profiles.size} perfiles importados")
    }

    suspend fun updateProfile(profile: VpnProfile) {
        profileDao.updateProfile(profile)
        log(LogLevel.INFO, "Perfil actualizado: ${profile.name}", profile.id)
    }

    suspend fun deleteProfile(profile: VpnProfile) {
        profileDao.deleteProfile(profile)
        log(LogLevel.WARNING, "Perfil eliminado: ${profile.name}")
    }

    suspend fun deleteAllProfiles() {
        profileDao.deleteAllProfiles()
        log(LogLevel.WARNING, "Todos los perfiles eliminados")
    }

    suspend fun setFavorite(id: String, isFavorite: Boolean) =
        profileDao.setFavorite(id, isFavorite)

    suspend fun setEnabled(id: String, enabled: Boolean) =
        profileDao.setEnabled(id, enabled)

    suspend fun markLastUsed(id: String) {
        profileDao.updateLastUsed(id, Instant.now().toString())
    }

    // ══════════════════════════════════════════════════════════════════════
    // LOGS
    // ══════════════════════════════════════════════════════════════════════

    val allLogs: Flow<List<LogEntry>> = logDao.getAllLogs()

    fun getRecentLogs(limit: Int = 50): Flow<List<LogEntry>> =
        logDao.getRecentLogs(limit)

    fun getLogsForProfile(profileId: String): Flow<List<LogEntry>> =
        logDao.getLogsForProfile(profileId)

    suspend fun clearLogs() {
        logDao.clearAllLogs()
    }

    suspend fun trimLogs(maxEntries: Int = 500) {
        logDao.keepOnly(maxEntries)
    }

    /** Inserta un log directamente */
    suspend fun insertLog(entry: LogEntry) {
        logDao.insertLog(entry)
        trimLogsIfNeeded()
    }

    /** Shortcut para loggear desde el repositorio o servicios */
    suspend fun log(
        level: LogLevel,
        message: String,
        profileId: String? = null,
        tag: String = "GhostVPN"
    ) {
        insertLog(
            LogEntry(
                level = level,
                tag = tag,
                message = message,
                profileId = profileId,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // PREFERENCIAS (DataStore)
    // ══════════════════════════════════════════════════════════════════════

    val activeProfileId: Flow<String>      = dataStore.activeProfileId
    val autoReconnect: Flow<Boolean>       = dataStore.autoReconnect
    val floatingWindow: Flow<Boolean>      = dataStore.floatingWindowEnabled
    val notifications: Flow<Boolean>       = dataStore.notificationsEnabled
    val darkTheme: Flow<Boolean>           = dataStore.darkTheme
    val reconnectOnBoot: Flow<Boolean>     = dataStore.reconnectOnBoot
    val showFloatingHint: Flow<Boolean>    = dataStore.showFloatingHint
    val logsMaxEntries: Flow<Int>          = dataStore.logsMaxEntries
    val isFirstLaunch: Flow<Boolean>       = dataStore.isFirstLaunch

    suspend fun setActiveProfileId(id: String) = dataStore.setActiveProfileId(id)
    suspend fun clearActiveProfile()            = dataStore.clearActiveProfile()
    suspend fun setAutoReconnect(v: Boolean)    = dataStore.setAutoReconnect(v)
    suspend fun setFloatingWindow(v: Boolean)   = dataStore.setFloatingWindowEnabled(v)
    suspend fun setNotifications(v: Boolean)    = dataStore.setNotificationsEnabled(v)
    suspend fun setReconnectOnBoot(v: Boolean)  = dataStore.setReconnectOnBoot(v)
    suspend fun setShowFloatingHint(v: Boolean) = dataStore.setShowFloatingHint(v)
    suspend fun setLogsMaxEntries(v: Int)        = dataStore.setLogsMaxEntries(v)
    suspend fun setFirstLaunchDone()            = dataStore.setFirstLaunchDone()

    /** Limpia todo: perfiles, logs y preferencias */
    suspend fun clearAllData() {
        profileDao.deleteAllProfiles()
        logDao.clearAllLogs()
        dataStore.clearAll()
        log(LogLevel.WARNING, "Todos los datos eliminados")
    }

    private suspend fun trimLogsIfNeeded() {
        val maxEntries = logsMaxEntries.first().coerceIn(100, 5_000)
        logDao.keepOnly(maxEntries)
    }
}
