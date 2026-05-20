package com.ghostnexora.vpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import java.io.File
import dagger.hilt.android.HiltAndroidApp

/**
 * Clase Application principal de Ghost Nexora VPN.
 *
 * Responsabilidades:
 * - Inicializar Hilt (inyección de dependencias)
 * - Crear los canales de notificación obligatorios (Android 8+)
 */
@HiltAndroidApp
class GhostNexoraApp : Application() {

    override fun onCreate() {
        super.onCreate()
        prepareEmbeddedGeoData()
        createNotificationChannels()
    }

    // ══════════════════════════════════════════════════════════════════════
    // CANALES DE NOTIFICACIÓN
    // Android 8+ requiere canales antes de mostrar cualquier notificación.
    // Se crean una sola vez; si ya existen, la llamada es ignorada.
    // ══════════════════════════════════════════════════════════════════════

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // ── Canal 1: Estado VPN ───────────────────────────────────────────
        val vpnChannel = NotificationChannel(
            CHANNEL_VPN_STATUS,
            getString(R.string.notif_channel_vpn),
            NotificationManager.IMPORTANCE_LOW  // Sin sonido; es persistente
        ).apply {
            description = getString(R.string.notif_channel_vpn_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        // ── Canal 2: Ventana flotante ─────────────────────────────────────
        val floatingChannel = NotificationChannel(
            CHANNEL_FLOATING_WINDOW,
            getString(R.string.notif_channel_floating),
            NotificationManager.IMPORTANCE_MIN   // Completamente silencioso
        ).apply {
            description = getString(R.string.notif_channel_floating_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        notificationManager.createNotificationChannels(
            listOf(vpnChannel, floatingChannel)
        )
    }

    private fun prepareEmbeddedGeoData() {
        listOf("geoip.dat", "geosite.dat").forEach { fileName ->
            val destination = File(filesDir, fileName)
            if (destination.exists()) return@forEach

            runCatching {
                assets.open(fileName).use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    companion object {
        const val CHANNEL_VPN_STATUS      = "ghost_nexora_vpn_status"
        const val CHANNEL_FLOATING_WINDOW = "ghost_nexora_floating"

        // IDs de notificación
        const val NOTIF_ID_VPN      = 1001
        const val NOTIF_ID_FLOATING = 1002
    }
}
