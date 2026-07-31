package com.ghostnexora.vpn.tunnel

import java.net.Socket

/**
 * Process-local bridge between [android.net.VpnService.protect] and transport
 * code that must create its sockets outside the Android TUN.
 *
 * GhostVpnService and the tunnel engines run in the same private :vpn process.
 * The service installs the delegate before any core starts and clears it only
 * after every transport has been stopped. A missing or rejected delegate fails
 * closed instead of risking a recursive VPN route.
 */
internal object OutboundSocketProtection {
    fun interface Protector {
        fun protect(socket: Socket): Boolean
    }

    @Volatile
    private var protector: Protector? = null

    fun install(protector: Protector) {
        this.protector = protector
    }

    fun clear() {
        protector = null
    }

    fun protect(socket: Socket): Boolean = protector?.protect(socket) == true
}
