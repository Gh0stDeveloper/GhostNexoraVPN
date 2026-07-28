package com.ghostnexora.vpn.util

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile


data class ProfileTechnicalSummary(
    val protocol: String,
    val server: String,
    val transport: String,
    val security: String,
    val sni: String,
    val hostHeader: String,
    val path: String,
    val serviceName: String,
    val proxy: String,
    val warnings: List<String>
)

object ProfileTechnicalSummaries {
    fun from(profile: VpnProfile): ProfileTechnicalSummary {
        val options = parseOptions(profile.payload)
        val protocol = when (profile.selectedMode) {
            ConnectionMode.V2RAY -> options["protocol"]?.uppercase()
                ?: if (profile.tags.any { it.equals("vmess", true) }) "VMESS" else "VLESS"
            ConnectionMode.TROJAN -> "TROJAN"
            ConnectionMode.UDP -> "HYSTERIA2"
            else -> "SSH"
        }
        val transport = when {
            profile.selectedMode.isSsh && profile.selectedMode.requiresProxy -> "${profile.proxy.type.ifBlank { "http" }.uppercase()} → SSH"
            profile.selectedMode.isSsh && profile.selectedMode.requiresPayload -> "HTTP payload → SSH"
            profile.selectedMode.isSsh -> "TCP"
            else -> normalizeTransport(options["net"] ?: options["type"] ?: "tcp")
        }
        val security = when {
            options["security"].equals("reality", true) -> "REALITY"
            profile.selectedMode.usesTls || profile.sslEnabled || options["security"].equals("tls", true) -> "TLS"
            profile.selectedMode.isSsh -> "SSH"
            else -> "Sin TLS"
        }
        val sni = profile.sni.ifBlank { options["sni"].orEmpty() }
        val hostHeader = options["host"].orEmpty().ifBlank { options["authority"].orEmpty() }
        val path = options["path"].orEmpty().ifBlank { "/" }
        val serviceName = options["serviceName"].orEmpty()
        val proxy = profile.proxy.takeIf { it.host.isNotBlank() && it.port in 1..65535 }
            ?.let { "${it.type.ifBlank { "http" }.uppercase()} ${it.host}:${it.port}" }
            .orEmpty()
        val warnings = buildList {
            if ((security == "TLS" || security == "REALITY") && sni.isBlank()) add("SNI no configurado")
            if (profile.selectedMode == ConnectionMode.V2RAY && protocol == "VLESS" && profile.username.isBlank()) add("UUID vacío")
            if (profile.selectedMode.requiresPayload && PayloadEngine.validate(profile.payload).isValid.not()) add("Payload con errores de sintaxis")
            if (profile.selectedMode.requiresProxy && proxy.isBlank()) add("Proxy incompleto")
        }
        return ProfileTechnicalSummary(
            protocol = protocol,
            server = profile.serverAddress,
            transport = transport,
            security = security,
            sni = sni,
            hostHeader = hostHeader,
            path = path,
            serviceName = serviceName,
            proxy = proxy,
            warnings = warnings
        )
    }

    private fun normalizeTransport(value: String): String = when (value.trim().lowercase()) {
        "ws", "websocket" -> "WebSocket"
        "grpc" -> "gRPC"
        "xhttp", "splithttp" -> "XHTTP"
        "httpupgrade" -> "HTTPUpgrade"
        "kcp", "mkcp" -> "mKCP"
        "hysteria", "hysteria2" -> "QUIC / Hysteria2"
        else -> "TCP"
    }

    private fun parseOptions(raw: String): Map<String, String> {
        if (raw.isBlank() || !raw.contains('=')) return emptyMap()
        return raw
            .split('|', ';', '\n')
            .mapNotNull { token ->
                val parts = token.trim().split('=', limit = 2)
                val key = parts.getOrNull(0)?.trim().orEmpty()
                val value = parts.getOrNull(1)?.trim().orEmpty()
                if (key.isBlank()) null else key to value
            }
            .toMap()
    }
}