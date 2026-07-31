package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode

/**
 * Authoritative description of the executable data plane bundled by the app.
 *
 * AndroidLibXrayLite already owns the native TUN adapter used by every mode;
 * stacking a second hev-tun2socks process over the same descriptor would create
 * two packet consumers and an invalid routing graph. SSH therefore exposes a
 * loopback SOCKS hop to Xray, while V2Ray, Trojan and Hysteria2 use native Xray
 * outbounds directly.
 */
internal data class NativeRuntimePlan(
    val tunAdapter: String,
    val protocolCore: String,
    val localHop: String?,
    val carriesTcp: Boolean,
    val carriesUdp: Boolean,
    val limitations: String
)

internal object NativeRuntimeArchitecture {
    private const val XRAY_TUN = "AndroidLibXrayLite · Xray TUN nativo"

    fun plan(mode: ConnectionMode): NativeRuntimePlan = when {
        mode.isSsh -> NativeRuntimePlan(
            tunAdapter = XRAY_TUN,
            protocolCore = "JSch SSH",
            localHop = "127.0.0.1 SOCKS5 → SSH direct-tcpip",
            carriesTcp = true,
            carriesUdp = false,
            limitations = "TCP por SSH; BadVPN UDP Gateway no está empaquetado"
        )

        mode == ConnectionMode.V2RAY -> NativeRuntimePlan(
            tunAdapter = XRAY_TUN,
            protocolCore = "Xray VLESS/VMess",
            localHop = null,
            carriesTcp = true,
            carriesUdp = true,
            limitations = "La compatibilidad exacta depende del transporte admitido por el core incluido"
        )

        mode == ConnectionMode.TROJAN -> NativeRuntimePlan(
            tunAdapter = XRAY_TUN,
            protocolCore = "Xray Trojan/TLS",
            localHop = null,
            carriesTcp = true,
            carriesUdp = true,
            limitations = "TLS y credenciales se validan con la configuración del perfil"
        )

        mode == ConnectionMode.UDP -> NativeRuntimePlan(
            tunAdapter = XRAY_TUN,
            protocolCore = "Xray Hysteria2/QUIC",
            localHop = null,
            carriesTcp = true,
            carriesUdp = true,
            limitations = "Hysteria2 habilitado; Hysteria v1 y binario externo no están empaquetados"
        )

        else -> error("No existe un runtime nativo para ${mode.label}")
    }

    fun statusLine(mode: ConnectionMode): String {
        val plan = plan(mode)
        val traffic = buildList {
            if (plan.carriesTcp) add("TCP")
            if (plan.carriesUdp) add("UDP")
        }.joinToString("/")
        val hop = plan.localHop?.let { " · $it" }.orEmpty()
        return "${plan.tunAdapter} → ${plan.protocolCore}$hop · $traffic"
    }
}
