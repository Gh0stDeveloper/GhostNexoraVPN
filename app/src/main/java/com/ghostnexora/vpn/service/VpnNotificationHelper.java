package com.ghostnexora.vpn.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.ghostnexora.vpn.GhostNexoraApp;
import com.ghostnexora.vpn.R;
import com.ghostnexora.vpn.data.model.VpnConnectionState;
import com.ghostnexora.vpn.ui.MainActivity;

/** Notification rendering for the Java VPN service layer. */
public final class VpnNotificationHelper {
    private VpnNotificationHelper() {
    }

    public static Notification build(Context context, VpnConnectionState state) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context,
                GhostNexoraApp.CHANNEL_VPN_STATUS
        )
                .setSmallIcon(R.drawable.ic_vpn_notification)
                .setContentIntent(openAppIntent(context))
                .setOngoing(!(state instanceof VpnConnectionState.Disconnected))
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setShowWhen(state instanceof VpnConnectionState.Connected);

        String title;
        String body;
        if (state instanceof VpnConnectionState.Connected) {
            VpnConnectionState.Connected connected = (VpnConnectionState.Connected) state;
            title = "VPN protegida";
            body = connected.getProfileName() + " · " + connected.getServerIp();
        } else if (state instanceof VpnConnectionState.Connecting) {
            title = "Conectando VPN";
            body = "Perfil: " + ((VpnConnectionState.Connecting) state).getProfileName();
        } else if (state instanceof VpnConnectionState.Reconnecting) {
            VpnConnectionState.Reconnecting reconnecting = (VpnConnectionState.Reconnecting) state;
            title = "Reconectando de forma segura";
            body = "Intento " + reconnecting.getAttempt() + " · TUN protegido";
        } else if (state instanceof VpnConnectionState.Disconnecting) {
            title = "Desconectando";
            body = "Cerrando la conexión VPN";
        } else if (state instanceof VpnConnectionState.Error) {
            title = "Protección VPN";
            String message = ((VpnConnectionState.Error) state).getMessage();
            body = message == null || message.isEmpty() ? "Error de conexión" : message;
        } else {
            title = "Ghost Nexora VPN";
            body = "Sin conexión activa";
        }

        builder.setContentTitle(title).setContentText(body);

        if (state instanceof VpnConnectionState.Connected
                || state instanceof VpnConnectionState.Reconnecting
                || state instanceof VpnConnectionState.Error) {
            builder.addAction(new NotificationCompat.Action.Builder(
                    R.drawable.ic_vpn_notification,
                    "Desconectar",
                    disconnectIntent(context)
            ).build());
        }
        if (state instanceof VpnConnectionState.Connecting
                || state instanceof VpnConnectionState.Reconnecting
                || state instanceof VpnConnectionState.Disconnecting) {
            builder.setProgress(0, 0, true);
        }
        return builder.build();
    }

    public static void update(Context context, VpnConnectionState state) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(GhostNexoraApp.NOTIF_ID_VPN, build(context, state));
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent disconnectIntent(Context context) {
        Intent intent = new Intent(context, GhostVpnService.class);
        intent.setAction(GhostVpnService.ACTION_DISCONNECT);
        return PendingIntent.getService(
                context,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
