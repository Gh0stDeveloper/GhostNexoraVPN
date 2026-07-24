package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Convierte un perfil de Ghost Nexora en una configuración autocontenida de
 * Xray Core con inbound TUN. El primer outbound siempre es el transporte VPN,
 * de modo que no existe un fallback silencioso a conexión directa.
 */
object XrayConfigFactory {

    fun build(profile: VpnProfile, sshSocksPort: Int? = null): String {
        val primaryOutbound = when {
            sshSocksPort != null -> socksOutbound(sshSocksPort)
            profile.selectedMode == ConnectionMode.V2RAY -> v2rayOutbound(profile)
            profile.selectedMode == ConnectionMode.TROJAN -> trojanOutbound(profile)
            profile.selectedMode == ConnectionMode.UDP -> hysteria2Outbound(profile)
            else -> error("No hay outbound Xray para ${profile.connectionModeLabel}")
        }

        val config = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning").put("dnsLog", false))
            .put("stats", JSONObject())
            .put(
                "policy",
                JSONObject()
                    .put(
                        "levels",
                        JSONObject().put(
                            "8",
                            JSONObject()
                                .put("handshake", 8)
                                .put("connIdle", 300)
                                .put("uplinkOnly", 2)
                                .put("downlinkOnly", 2)
                        )
                    )
                    .put(
                        "system",
                        JSONObject()
                            .put("statsOutboundUplink", true)
                            .put("statsOutboundDownlink", true)
                    )
            )
            .put("dns", protectedDns())
            .put("inbounds", JSONArray().put(tunInbound()))
            .put(
                "outbounds",
                JSONArray()
                    .put(primaryOutbound)
                    .put(JSONObject().put("tag", "dns-out").put("protocol", "dns").put("settings", JSONObject()))
                    .put(
                        JSONObject()
                            .put("tag", "block")
                            .put("protocol", "blackhole")
                            .put("settings", JSONObject())
                    )
            )
            .put("routing", routing())

        return config.toString()
    }

    private fun tunInbound(): JSONObject = JSONObject()
        .put("tag", "tun")
        .put("protocol", "tun")
        .put(
            "settings",
            JSONObject()
                .put("name", "ghostnexora0")
                .put("MTU", 1500)
                .put("userLevel", 8)
        )
        .put(
            "sniffing",
            JSONObject()
                .put("enabled", true)
                .put("routeOnly", false)
                .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
        )

    private fun protectedDns(): JSONObject = JSONObject()
        .put("queryStrategy", "UseIPv4")
        .put("hosts", JSONObject())
        .put(
            "servers",
            JSONArray().put(
                JSONObject()
                    .put("address", "https://1.1.1.1/dns-query")
                    .put("queryStrategy", "UseIPv4")
            )
        )

    private fun routing(): JSONObject = JSONObject()
        .put("domainStrategy", "AsIs")
        .put(
            "rules",
            JSONArray().put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun"))
                    .put("network", "tcp,udp")
                    .put("port", "53")
                    .put("outboundTag", "dns-out")
            )
        )

    private fun socksOutbound(port: Int): JSONObject {
        require(port in 1..65535) { "Puerto SOCKS SSH inválido" }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "socks")
            .put(
                "settings",
                JSONObject()
                    .put("address", "127.0.0.1")
                    .put("port", port)
                    .put("level", 8)
            )
    }

    private fun v2rayOutbound(profile: VpnProfile): JSONObject {
        require(profile.host.isNotBlank()) { "El servidor V2Ray es obligatorio" }
        require(profile.username.isNotBlank()) { "El UUID/User ID de V2Ray es obligatorio" }

        val options = parseOptions(profile.payload)
        val tags = profile.tags.map(String::lowercase)
        val protocol = when {
            tags.any { it == "vmess" } -> "vmess"
            options["protocol"]?.equals("vmess", true) == true -> "vmess"
            else -> "vless"
        }

        val settings = JSONObject()
            .put("address", profile.host.trim())
            .put("port", profile.port)
            .put("id", profile.username.trim())
            .put("level", 8)

        if (protocol == "vmess") {
            settings.put("security", options["cipher"].orEmpty().ifBlank { "auto" })
        } else {
            settings.put("encryption", options["encryption"].orEmpty().ifBlank { "none" })
            options["flow"]?.takeIf { it.isNotBlank() }?.let { settings.put("flow", it) }
        }

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", protocol)
            .put("settings", settings)
            .put("streamSettings", streamSettings(profile, options))
            .put("mux", JSONObject().put("enabled", false).put("concurrency", -1))
    }

    private fun trojanOutbound(profile: VpnProfile): JSONObject {
        require(profile.host.isNotBlank()) { "El servidor Trojan es obligatorio" }
        require(profile.password.isNotBlank()) { "La contraseña Trojan es obligatoria" }
        require(profile.sni.isNotBlank()) { "Trojan requiere SNI para TLS" }

        val options = parseOptions(profile.payload)
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put(
                "settings",
                JSONObject()
                    .put("address", profile.host.trim())
                    .put("port", profile.port)
                    .put("password", profile.password)
                    .put("level", 8)
            )
            .put("streamSettings", streamSettings(profile, options, forceTls = true))
            .put("mux", JSONObject().put("enabled", false).put("concurrency", -1))
    }

    private fun hysteria2Outbound(profile: VpnProfile): JSONObject {
        require(profile.host.isNotBlank()) { "El servidor UDP/Hysteria2 es obligatorio" }
        require(profile.password.isNotBlank()) { "La autenticación UDP/Hysteria2 es obligatoria" }
        require(profile.sni.isNotBlank()) { "UDP/Hysteria2 requiere SNI para TLS" }

        val options = parseOptions(profile.payload)
        val hysteriaSettings = JSONObject()
            .put("version", 2)
            .put("auth", profile.password)
        options["udpIdleTimeout"]?.toIntOrNull()?.let { hysteriaSettings.put("udpIdleTimeout", it) }

        val stream = JSONObject()
            .put("network", "hysteria")
            .put("security", "tls")
            .put("hysteriaSettings", hysteriaSettings)
            .put("tlsSettings", tlsSettings(profile.sni, options))

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "hysteria")
            .put(
                "settings",
                JSONObject()
                    .put("address", profile.host.trim())
                    .put("port", profile.port)
                    .put("version", 2)
            )
            .put("streamSettings", stream)
            .put("mux", JSONObject().put("enabled", false).put("concurrency", -1))
    }

    private fun streamSettings(
        profile: VpnProfile,
        options: Map<String, String>,
        forceTls: Boolean = false
    ): JSONObject {
        val network = (options["net"] ?: options["type"] ?: options["network"] ?: "tcp")
            .lowercase()
            .let { if (it == "raw") "tcp" else it }

        val stream = JSONObject().put("network", network)
        when (network) {
            "ws" -> stream.put(
                "wsSettings",
                JSONObject()
                    .put("host", options["host"].orEmpty())
                    .put("path", options["path"].orEmpty().ifBlank { "/" })
            )

            "grpc" -> stream.put(
                "grpcSettings",
                JSONObject()
                    .put("serviceName", options["serviceName"] ?: options["path"].orEmpty().trimStart('/'))
                    .put("authority", options["authority"] ?: options["host"].orEmpty())
                    .put("multiMode", options["mode"]?.equals("multi", true) == true)
            )

            "h2", "http" -> {
                stream.put("network", "h2")
                stream.put(
                    "httpSettings",
                    JSONObject()
                        .put("host", JSONArray(splitHosts(options["host"])))
                        .put("path", options["path"].orEmpty().ifBlank { "/" })
                )
            }

            "xhttp" -> stream.put(
                "xhttpSettings",
                JSONObject()
                    .put("host", options["host"].orEmpty())
                    .put("path", options["path"].orEmpty().ifBlank { "/" })
                    .putOpt("mode", options["mode"]?.takeIf { it.isNotBlank() })
            )
        }

        val security = when {
            forceTls -> "tls"
            options["security"]?.equals("reality", true) == true -> "reality"
            options["security"]?.equals("tls", true) == true -> "tls"
            profile.sslEnabled -> "tls"
            else -> null
        }

        if (security != null) {
            val sni = profile.sni.ifBlank { options["sni"].orEmpty() }.ifBlank { profile.host }
            stream.put("security", security)
            if (security == "reality") {
                val reality = tlsSettings(sni, options)
                options["pbk"]?.takeIf { it.isNotBlank() }?.let { reality.put("publicKey", it) }
                options["sid"]?.takeIf { it.isNotBlank() }?.let { reality.put("shortId", it) }
                options["spx"]?.takeIf { it.isNotBlank() }?.let { reality.put("spiderX", it) }
                stream.put("realitySettings", reality)
            } else {
                stream.put("tlsSettings", tlsSettings(sni, options))
            }
        }

        return stream
    }

    private fun tlsSettings(sni: String, options: Map<String, String>): JSONObject = JSONObject()
        .put("allowInsecure", false)
        .put("serverName", sni)
        .putOpt("fingerprint", (options["fp"] ?: options["fingerprint"])?.takeIf { it.isNotBlank() })
        .putOpt(
            "alpn",
            options["alpn"]?.takeIf { it.isNotBlank() }?.let { JSONArray(it.split(',').map(String::trim)) }
        )

    private fun parseOptions(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        if (raw.trimStart().startsWith("{")) {
            return runCatching {
                val json = JSONObject(raw)
                json.keys().asSequence().associateWith { key -> json.optString(key) }
            }.getOrDefault(emptyMap())
        }

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

    private fun splitHosts(value: String?): List<String> = value
        .orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
}
