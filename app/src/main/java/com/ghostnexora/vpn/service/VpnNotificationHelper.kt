package com.ghostnexora.vpn.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ghostnexora.vpn.GhostNexoraApp
import com.ghostnexora.vpn.R
import com.ghostnexora.vpn.data.model.VpnConnectionState
import com.ghostnexora.vpn.ui.MainActivity

object VpnNotificationHelper {
    fun build(context: Context, state: VpnConnectionState): Notification {
        val builder = NotificationCompat.Builder(context, GhostNexoraApp.CHANNEL_VPN_STATUS)
            .setSmallIcon(R.drawable.ic_vpn_notification)
            .setContentIntent(openAppIntent(context))
            .setOngoing(state !is VpnConnectionState.Disconnected)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(state is VpnConnectionState.Connected)

        val (title, body) = notificationText(state)
        builder.setContentTitle(title).setContentText(body)

        if (state is VpnConnectionState.Connected || state is VpnConnectionState.Reconnecting || state is VpnConnectionState.Error) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_vpn_notification,
                    "Desconectar",
                    disconnectIntent(context)
                ).build()
            )
        }
        if (state is VpnConnectionState.Connecting || state is VpnConnectionState.Reconnecting || state is VpnConnectionState.Disconnecting) {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun update(context: Context, state: VpnConnectionState) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(GhostNexoraApp.NOTIF_ID_VPN, build(context, state))
    }

    private fun notificationText(state: VpnConnectionState): Pair<String, String> = when (state) {
        is VpnConnectionState.Connected -> "VPN protegida" to "${state.profileName} · ${state.serverIp}"
        is VpnConnectionState.Connecting -> "Conectando VPN" to "Perfil: ${state.profileName}"
        is VpnConnectionState.Reconnecting -> "Reconectando de forma segura" to "Intento ${state.attempt} · TUN protegido"
        is VpnConnectionState.Disconnecting -> "Desconectando" to "Cerrando la conexión VPN"
        is VpnConnectionState.Error -> "Protección VPN" to state.message.ifEmpty { "Error de conexión" }
        VpnConnectionState.Disconnected -> "Ghost Nexora VPN" to "Sin conexión activa"
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun disconnectIntent(context: Context): PendingIntent = PendingIntent.getService(
        context,
        1,
        Intent(context, GhostVpnService::class.java).apply { action = GhostVpnService.ACTION_DISCONNECT },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
