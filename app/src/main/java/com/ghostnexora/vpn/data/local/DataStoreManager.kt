package com.ghostnexora.vpn.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ghostnexora.vpn.data.model.AppRoutingMode
import com.ghostnexora.vpn.data.model.AppRoutingPreferences
import com.ghostnexora.vpn.data.model.DnsMode
import com.ghostnexora.vpn.data.model.IpMode
import com.ghostnexora.vpn.data.model.NetworkPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ghost_nexora_prefs")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object Keys {
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val FLOATING_WINDOW = booleanPreferencesKey("floating_window_enabled")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val RECONNECT_ON_BOOT = booleanPreferencesKey("reconnect_on_boot")
        val LAST_CONNECTED_TIME = longPreferencesKey("last_connected_time")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val SHOW_FLOATING_HINT = booleanPreferencesKey("show_floating_hint")
        val LOGS_MAX_ENTRIES = intPreferencesKey("logs_max_entries")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        val DISMISSED_UPDATE_IDENTITY = stringPreferencesKey("dismissed_update_identity")
        val IP_MODE = stringPreferencesKey("network_ip_mode")
        val TUN_MTU = intPreferencesKey("network_tun_mtu")
        val DNS_MODE = stringPreferencesKey("network_dns_mode")
        val DNS_PRIMARY = stringPreferencesKey("network_dns_primary")
        val DNS_SECONDARY = stringPreferencesKey("network_dns_secondary")
        val RECONNECT_MAX_ATTEMPTS = intPreferencesKey("reconnect_max_attempts")
        val APP_ROUTING_MODE = stringPreferencesKey("app_routing_mode")
        val APP_ROUTING_PACKAGES = stringSetPreferencesKey("app_routing_packages")
    }

    val activeProfileId: Flow<String> = dataStore.data.safeCatch().map { it[ACTIVE_PROFILE_ID] ?: "" }
    val autoReconnect: Flow<Boolean> = dataStore.data.safeCatch().map { it[AUTO_RECONNECT] ?: true }
    val killSwitch: Flow<Boolean> = dataStore.data.safeCatch().map { it[KILL_SWITCH] ?: true }
    val floatingWindowEnabled: Flow<Boolean> = dataStore.data.safeCatch().map { it[FLOATING_WINDOW] ?: false }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.safeCatch().map { it[NOTIFICATIONS] ?: true }
    val darkTheme: Flow<Boolean> = dataStore.data.safeCatch().map { it[DARK_THEME] ?: true }
    val reconnectOnBoot: Flow<Boolean> = dataStore.data.safeCatch().map { it[RECONNECT_ON_BOOT] ?: false }
    val showFloatingHint: Flow<Boolean> = dataStore.data.safeCatch().map { it[SHOW_FLOATING_HINT] ?: true }
    val logsMaxEntries: Flow<Int> = dataStore.data.safeCatch().map { it[LOGS_MAX_ENTRIES] ?: 500 }
    val isFirstLaunch: Flow<Boolean> = dataStore.data.safeCatch().map { it[FIRST_LAUNCH] ?: true }
    val lastUpdateCheckAt: Flow<Long> = dataStore.data.safeCatch().map { it[LAST_UPDATE_CHECK_AT] ?: 0L }
    val dismissedUpdateIdentity: Flow<String> = dataStore.data.safeCatch().map { it[DISMISSED_UPDATE_IDENTITY].orEmpty() }
    val networkPreferences: Flow<NetworkPreferences> = dataStore.data.safeCatch().map { values ->
        NetworkPreferences(
            ipMode = IpMode.fromId(values[IP_MODE]),
            mtu = (values[TUN_MTU] ?: NetworkPreferences.DEFAULT_MTU)
                .coerceIn(NetworkPreferences.MIN_MTU, NetworkPreferences.MAX_MTU),
            dnsMode = DnsMode.fromId(values[DNS_MODE]),
            customDnsPrimary = values[DNS_PRIMARY] ?: "1.1.1.1",
            customDnsSecondary = values[DNS_SECONDARY] ?: "8.8.8.8",
            reconnectMaxAttempts = (values[RECONNECT_MAX_ATTEMPTS] ?: 8).coerceIn(1, 12)
        )
    }
    val appRoutingPreferences: Flow<AppRoutingPreferences> = dataStore.data.safeCatch().map { values ->
        AppRoutingPreferences(
            mode = AppRoutingMode.fromId(values[APP_ROUTING_MODE]),
            packages = values[APP_ROUTING_PACKAGES]?.toSet().orEmpty()
        )
    }

    suspend fun setActiveProfileId(id: String) = edit { it[ACTIVE_PROFILE_ID] = id }
    suspend fun setAutoReconnect(enabled: Boolean) = edit { it[AUTO_RECONNECT] = enabled }
    suspend fun setKillSwitch(enabled: Boolean) = edit { it[KILL_SWITCH] = enabled }
    suspend fun setFloatingWindowEnabled(enabled: Boolean) = edit { it[FLOATING_WINDOW] = enabled }
    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[NOTIFICATIONS] = enabled }
    suspend fun setDarkTheme(enabled: Boolean) = edit { it[DARK_THEME] = enabled }
    suspend fun setReconnectOnBoot(enabled: Boolean) = edit { it[RECONNECT_ON_BOOT] = enabled }
    suspend fun setLastConnectedTime(time: Long) = edit { it[LAST_CONNECTED_TIME] = time }
    suspend fun setShowFloatingHint(show: Boolean) = edit { it[SHOW_FLOATING_HINT] = show }
    suspend fun setLogsMaxEntries(max: Int) = edit { it[LOGS_MAX_ENTRIES] = max }
    suspend fun setFirstLaunchDone() = edit { it[FIRST_LAUNCH] = false }
    suspend fun setLastUpdateCheckAt(value: Long) = edit { it[LAST_UPDATE_CHECK_AT] = value }
    suspend fun setDismissedUpdateIdentity(value: String) = edit {
        if (value.isBlank()) it.remove(DISMISSED_UPDATE_IDENTITY) else it[DISMISSED_UPDATE_IDENTITY] = value
    }
    suspend fun setIpMode(value: IpMode) = edit { it[IP_MODE] = value.id }
    suspend fun setTunMtu(value: Int) = edit {
        it[TUN_MTU] = value.coerceIn(NetworkPreferences.MIN_MTU, NetworkPreferences.MAX_MTU)
    }
    suspend fun setDnsMode(value: DnsMode) = edit { it[DNS_MODE] = value.id }
    suspend fun setCustomDns(primary: String, secondary: String) = edit {
        it[DNS_PRIMARY] = primary.trim()
        it[DNS_SECONDARY] = secondary.trim()
    }
    suspend fun setReconnectMaxAttempts(value: Int) = edit { it[RECONNECT_MAX_ATTEMPTS] = value.coerceIn(1, 12) }
    suspend fun setAppRoutingMode(value: AppRoutingMode) = edit { it[APP_ROUTING_MODE] = value.id }
    suspend fun setAppRoutingPackages(value: Set<String>) = edit {
        it[APP_ROUTING_PACKAGES] = AppRoutingPreferences(packages = value).normalizedPackages
    }
    suspend fun clearActiveProfile() = edit { it.remove(ACTIVE_PROFILE_ID) }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private suspend fun edit(transform: suspend (MutablePreferences) -> Unit) {
        dataStore.edit(transform)
    }

    private fun Flow<Preferences>.safeCatch() = catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }
}