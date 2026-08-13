package com.ghostnexora.vpn.tunnel;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-local bridge between android.net.VpnService.protect(Socket) and the
 * transport code running in the private :vpn process.
 *
 * The protector is installed by GhostVpnService before any outbound transport
 * starts. A missing or rejected protector fails closed so a transport socket
 * can never recurse into the VPN TUN.
 */
public final class OutboundSocketProtection {
    @FunctionalInterface
    public interface Protector {
        boolean protect(Socket socket);
    }

    private static final AtomicReference<Protector> PROTECTOR = new AtomicReference<>();

    private OutboundSocketProtection() {
    }

    public static void install(Protector protector) {
        if (protector == null) {
            throw new IllegalArgumentException("protector == null");
        }
        PROTECTOR.set(protector);
    }

    public static void clear() {
        PROTECTOR.set(null);
    }

    public static boolean protect(Socket socket) {
        Protector protector = PROTECTOR.get();
        return protector != null && socket != null && protector.protect(socket);
    }
}
