package com.ghostnexora.vpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.util.ProcessUtils
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GhostNexoraApp : Application() {
    @Inject
    lateinit var repository: ProfileRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        if (ProcessUtils.isMainProcess(this)) {
            appScope.launch {
                runCatching { repository.migrateLegacySecrets() }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val vpnChannel = NotificationChannel(
            CHANNEL_VPN_STATUS,
            getString(R.string.notif_channel_vpn),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_vpn_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val floatingChannel = NotificationChannel(
            CHANNEL_FLOATING_WINDOW,
            getString(R.string.notif_channel_floating),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.notif_channel_floating_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        notificationManager.createNotificationChannels(listOf(vpnChannel, floatingChannel))
    }

    companion object {
        const val CHANNEL_VPN_STATUS = "ghost_nexora_vpn_status"
        const val CHANNEL_FLOATING_WINDOW = "ghost_nexora_floating"
        const val NOTIF_ID_VPN = 1001
        const val NOTIF_ID_FLOATING = 1002
    }
}
