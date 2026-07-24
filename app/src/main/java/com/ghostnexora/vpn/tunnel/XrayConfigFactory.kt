package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Genera una configuración Xray sin salida directa de respaldo.
 *
 * Se emiten `method` (esquema Xray moderno) y `network` (compatibilidad con
 * cores Android anteriores) donde procede. Los dos describen el mismo
 * transporte y no crean rutas de bypass.
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

        return JSONObject()
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
                    .put(JSONObject().put("tag", "block").put("protocol", "blackhole").put("settings", JSONObject()))
            )
            .put("routing", routing())
            .toString()
    }

    private fun tunInbound(): JSONObject = JSONObject()
        .put("tag", "tun")
        .put("protocol", "tun")
        .put(
            "settings",
            JSONObject()
                .put("name", "ghostnexora0")
                .put("mtu", 1500)
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
        val security = options["security"].orEmpty().lowercase()
        val vlessEncryption = options["encryption"].orEmpty().ifBlank { "none" }

        if (protocol == "vless" && vlessEncryption.equals("none", true)) {
            require(profile.sslEnabled || security == "tls" || security == "reality") {
                "VLESS sin cifrado de protocolo requiere TLS o REALITY"
            }
        }
        if (protocol == "vless" && (profile.sslEnabled || security == "tls" || security == "reality")) {
            require(profile.sni.isNotBlank() || options["sni"].orEmpty().isNotBlank()) {
                "VLESS con TLS/REALITY requiere SNI"
            }
        }

        val settings = JSONObject()
            .put("address", profile.host.trim())
            .put("port", profile.port)
            .put("id", profile.username.trim())
            .put("level", 8)

        if (protocol == "vmess") {
            settings.put("security", options["cipher"].orEmpty().ifBlank { "auto" })
        } else {
            settings.put("encryption", vlessEncryption)
            options["flow"]?.takeIf(String::isNotBlank)?.let { settings.put("flow", it) }
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
        require(profile.host.isNotBlank()) { "El servidor Hysteria2 es obligatorio" }
        require(profile.password.isNotBlank()) { "La autenticación Hysteria2 es obligatoria" }
        require(profile.sni.isNotBlank()) { "Hysteria2 requiere SNI para TLS" }

        val options = parseOptions(profile.payload)
        val hysteriaSettings = JSONObject()
            .put("version", 2)
            .put("auth", profile.password)
        options["udpIdleTimeout"]?.toIntOrNull()?.let { hysteriaSettings.put("udpIdleTimeout", it.coerceIn(10, 600)) }
        options["obfs"]?.takeIf(String::isNotBlank)?.let { hysteriaSettings.put("obfs", it) }
        (options["obfs-password"] ?: options["obfsPassword"])
            ?.takeIf(String::isNotBlank)
            ?.let { hysteriaSettings.put("obfsPassword", it) }

        val stream = JSONObject()
            .put("method", "hysteria")
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
        val requested = (options["net"] ?: options["type"] ?: options["network"] ?: "tcp").lowercase()
        val transport = normalizeTransport(requested)
        val stream = JSONObject()
            .put("method", transport.modern)
            .put("network", transport.legacy)

        when (transport.modern) {
            "websocket" -> stream.put(
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

            "xhttp" -> {
                val xhttp = JSONObject()
                    .put("host", options["host"].orEmpty())
                    .put("path", options["path"].orEmpty().ifBlank { "/" })
                    .putOpt("mode", options["mode"]?.takeIf(String::isNotBlank))
                stream.put("xhttpSettings", xhttp)
                if (requested in setOf("h2", "http")) {
                    stream.put(
                        "httpSettings",
                        JSONObject()
                            .put("host", JSONArray(splitHosts(options["host"])))
                            .put("path", options["path"].orEmpty().ifBlank { "/" })
                    )
                }
            }

            "httpupgrade" -> stream.put(
                "httpupgradeSettings",
                JSONObject()
                    .put("host", options["host"].orEmpty())
                    .put("path", options["path"].orEmpty().ifBlank { "/" })
            )

            "mkcp" -> stream.put(
                "kcpSettings",
                JSONObject()
                    .put("header", JSONObject().put("type", options["headerType"].orEmpty().ifBlank { "none" }))
                    .putOpt("seed", options["seed"]?.takeIf(String::isNotBlank))
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
                require(transport.modern in setOf("raw", "xhttp", "grpc")) {
                    "REALITY solo se permite con Raw, XHTTP o gRPC"
                }
                val reality = tlsSettings(sni, options)
                require(options["pbk"].orEmpty().isNotBlank()) { "REALITY requiere public key (pbk)" }
                reality.put("publicKey", options["pbk"])
                options["sid"]?.takeIf(String::isNotBlank)?.let { reality.put("shortId", it) }
                options["spx"]?.takeIf(String::isNotBlank)?.let { reality.put("spiderX", it) }
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
        .putOpt("fingerprint", (options["fp"] ?: options["fingerprint"])?.takeIf(String::isNotBlank))
        .putOpt(
            "alpn",
            options["alpn"]?.takeIf(String::isNotBlank)?.let {
                JSONArray(it.split(',').map(String::trim).filter(String::isNotBlank))
            }
        )

    private fun normalizeTransport(raw: String): Transport = when (raw) {
        "ws", "websocket" -> Transport("websocket", "ws")
        "grpc" -> Transport("grpc", "grpc")
        "xhttp" -> Transport("xhttp", "xhttp")
        "h2", "http" -> Transport("xhttp", "h2")
        "httpupgrade" -> Transport("httpupgrade", "httpupgrade")
        "kcp", "mkcp" -> Transport("mkcp", "kcp")
        "hysteria" -> Transport("hysteria", "hysteria")
        else -> Transport("raw", "tcp")
    }

    private fun parseOptions(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        if (raw.trimStart().startsWith("{")) {
            return runCatching {
                val json = JSONObject(raw)
                json.keys().asSequence().associateWith(json::optString)
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

    private data class Transport(val modern: String, val legacy: String)
}
