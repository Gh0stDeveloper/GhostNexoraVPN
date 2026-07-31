package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.DnsMode
import com.ghostnexora.vpn.data.model.NetworkPreferences
import com.ghostnexora.vpn.data.model.VpnProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runtime Xray configuration designed for deterministic Android TUN routing.
 *
 * Unlike the old implicit configuration, every packet entering the TUN has an
 * explicit destination: DNS is hijacked by Xray and all remaining TCP/UDP is
 * routed to the selected proxy outbound. A direct outbound exists only for
 * core-owned bootstrap traffic and is never used as a silent fallback for TUN
 * traffic.
 */
object StableXrayConfigFactory {
    const val TUN_MTU = NetworkPreferences.DEFAULT_MTU

    fun build(
        profile: VpnProfile,
        sshSocksPort: Int? = null,
        preferences: NetworkPreferences = NetworkPreferences(),
        healthCheckPort: Int? = null
    ): String {
        val options = parseOptions(profile.payload)
        val isSshBridge = sshSocksPort != null
        healthCheckPort?.let {
            require(isSshBridge) { "Health-check inbound is only valid with an SSH SOCKS bridge" }
            require(it in 1..65535) { "Invalid health-check port" }
        }
        val proxy = when {
            sshSocksPort != null -> socksOutbound(sshSocksPort)
            profile.selectedMode == ConnectionMode.V2RAY -> v2rayOutbound(profile, options, preferences)
            profile.selectedMode == ConnectionMode.TROJAN -> trojanOutbound(profile, options, preferences)
            profile.selectedMode == ConnectionMode.UDP -> hysteria2Outbound(profile, options, preferences)
            else -> error("No Xray outbound for ${profile.connectionModeLabel}")
        }
        val inbounds = JSONArray().put(tunInbound(preferences))
        healthCheckPort?.let { inbounds.put(healthCheckInbound(it)) }

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning").put("dnsLog", true))
            .put("stats", JSONObject())
            .put("policy", policy())
            .put("dns", protectedDns(preferences))
            .put("inbounds", inbounds)
            .put(
                "outbounds",
                JSONArray()
                    .put(proxy)
                    .put(dnsOutbound(preferences, detourThroughProxy = isSshBridge))
                    .put(directOutbound())
                    .put(blockOutbound())
            )
            .put("routing", routing(isSshBridge, healthCheckPort))
            .toString()
    }

    fun summary(profile: VpnProfile, preferences: NetworkPreferences = NetworkPreferences()): String {
        val options = parseOptions(profile.payload)
        val network = normalizeNetwork(options["net"] ?: options["type"] ?: options["network"] ?: "tcp")
        val security = when {
            options["security"].equals("reality", true) -> "REALITY"
            profile.sslEnabled || options["security"].equals("tls", true) || profile.selectedMode.usesTls -> "TLS"
            else -> "none"
        }
        return "${profile.connectionModeLabel} · $network · $security · ${preferences.ipMode.label} · MTU ${preferences.validatedMtu}"
    }

    private fun policy(): JSONObject = JSONObject()
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

    private fun tunInbound(preferences: NetworkPreferences): JSONObject = JSONObject()
        .put("tag", "tun")
        .put("protocol", "tun")
        .put(
            "settings",
            JSONObject()
                .put("name", "ghostnexora0")
                .put("mtu", preferences.validatedMtu)
                .put("userLevel", 8)
        )
        .put(
            "sniffing",
            JSONObject()
                .put("enabled", true)
                .put("routeOnly", false)
                .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
        )

    /**
     * Loopback-only probe entrypoint used after the TUN loop starts. The app
     * connects here with SOCKS5 and performs a real TLS handshake through the
     * selected outbound. This avoids AndroidLibXrayLite's in-memory
     * CoreController.measureDelay pipe, which can fail independently from the
     * actual TUN/SOCKS route.
     */
    private fun healthCheckInbound(port: Int): JSONObject = JSONObject()
        .put("tag", "health-check")
        .put("listen", "127.0.0.1")
        .put("port", port)
        .put("protocol", "socks")
        .put(
            "settings",
            JSONObject()
                .put("auth", "noauth")
                .put("udp", false)
        )

    private fun protectedDns(preferences: NetworkPreferences): JSONObject {
        val strategy = preferences.ipMode.xrayQueryStrategy
        val servers = JSONArray()
        when (preferences.dnsMode) {
            DnsMode.AUTOMATIC -> {
                servers.put(dohServer("https://cloudflare-dns.com/dns-query", strategy))
                servers.put(dohServer("https://dns.google/dns-query", strategy))
            }
            DnsMode.CLOUDFLARE -> servers.put(dohServer("https://cloudflare-dns.com/dns-query", strategy))
            DnsMode.GOOGLE -> servers.put(dohServer("https://dns.google/dns-query", strategy))
            DnsMode.CUSTOM -> preferences.dnsServers().forEach { address ->
                servers.put(JSONObject().put("address", address).put("queryStrategy", strategy))
            }
        }
        return JSONObject()
            .put("queryStrategy", strategy)
            .put("disableCache", false)
            .put(
                "hosts",
                JSONObject()
                    .put("cloudflare-dns.com", JSONArray(listOf("1.1.1.1", "1.0.0.1")))
                    .put("dns.google", JSONArray(listOf("8.8.8.8", "8.8.4.4")))
            )
            .put("servers", servers)
    }

    private fun dohServer(address: String, strategy: String): JSONObject = JSONObject()
        .put("address", address)
        .put("queryStrategy", strategy)
        .put("skipFallback", false)

    private fun dnsOutbound(
        preferences: NetworkPreferences,
        detourThroughProxy: Boolean
    ): JSONObject = JSONObject()
        .put("tag", "dns-out")
        .put("protocol", "dns")
        .put(
            "settings",
            JSONObject()
                .put("network", "tcp")
                .put("address", preferences.dnsServers().first())
                .put("port", 53)
                .put("userLevel", 8)
        )
        .apply {
            if (detourThroughProxy) {
                put("proxySettings", JSONObject().put("tag", "proxy"))
            }
        }

    private fun directOutbound(): JSONObject = JSONObject()
        .put("tag", "direct")
        .put("protocol", "freedom")
        .put("settings", JSONObject().put("domainStrategy", "UseIP"))
        .put("streamSettings", JSONObject().put("sockopt", JSONObject().put("domainStrategy", "UseIP")))

    private fun blockOutbound(): JSONObject = JSONObject()
        .put("tag", "block")
        .put("protocol", "blackhole")
        .put("settings", JSONObject().put("response", JSONObject().put("type", "none")))

    private fun routing(isSshBridge: Boolean, healthCheckPort: Int?): JSONObject {
        val rules = JSONArray()
        if (healthCheckPort != null) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("health-check"))
                    .put("network", "tcp")
                    .put("outboundTag", "proxy")
            )
        }
        rules
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun"))
                    .put("network", "tcp,udp")
                    .put("port", "53")
                    .put("outboundTag", "dns-out")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun"))
                    .put("network", "tcp,udp")
                    .put("outboundTag", "proxy")
            )

        return JSONObject()
            // SSH direct-tcpip resolves destination names at the SSH server.
            // Avoid resolving them on the physical Android network first.
            .put("domainStrategy", if (isSshBridge) "AsIs" else "IPIfNonMatch")
            .put("rules", rules)
    }

    private fun socksOutbound(port: Int): JSONObject {
        require(port in 1..65535) { "Invalid SSH SOCKS port" }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "socks")
            .put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", "127.0.0.1")
                            .put("port", port)
                    )
                )
            )
    }

    private fun v2rayOutbound(profile: VpnProfile, options: Map<String, String>, preferences: NetworkPreferences): JSONObject {
        require(profile.host.isNotBlank()) { "V2Ray server is required" }
        require(profile.port in 1..65535) { "V2Ray port is invalid" }
        require(profile.username.isNotBlank()) { "V2Ray UUID/User ID is required" }

        val protocol = when {
            profile.tags.any { it.equals("vmess", true) } -> "vmess"
            options["protocol"].equals("vmess", true) -> "vmess"
            else -> "vless"
        }
        val security = options["cipher"].orEmpty().ifBlank { "auto" }
        val encryption = options["encryption"].orEmpty().ifBlank { "none" }
        val user = JSONObject()
            .put("id", profile.username.trim())
            .put("level", 8)

        if (protocol == "vmess") {
            user.put("security", security).put("alterId", options["aid"]?.toIntOrNull() ?: 0)
        } else {
            user.put("encryption", encryption)
            options["flow"]?.takeIf(String::isNotBlank)?.let { user.put("flow", it) }
            if (encryption.equals("none", true)) {
                require(profile.sslEnabled || options["security"].equals("tls", true) || options["security"].equals("reality", true)) {
                    "VLESS encryption=none requires TLS or REALITY"
                }
            }
        }

        val settings = JSONObject().put(
            "vnext",
            JSONArray().put(
                JSONObject()
                    .put("address", profile.host.trim())
                    .put("port", profile.port)
                    .put("users", JSONArray().put(user))
            )
        )

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", protocol)
            .put("settings", settings)
            .put("streamSettings", streamSettings(profile, options, preferences))
            .put("mux", JSONObject().put("enabled", false).put("concurrency", -1))
    }

    private fun trojanOutbound(profile: VpnProfile, options: Map<String, String>, preferences: NetworkPreferences): JSONObject {
        require(profile.host.isNotBlank()) { "Trojan server is required" }
        require(profile.password.isNotBlank()) { "Trojan password is required" }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", profile.host.trim())
                            .put("port", profile.port)
                            .put("password", profile.password)
                            .put("level", 8)
                    )
                )
            )
            .put("streamSettings", streamSettings(profile, options, preferences, forceTls = true))
            .put("mux", JSONObject().put("enabled", false).put("concurrency", -1))
    }

    private fun hysteria2Outbound(profile: VpnProfile, options: Map<String, String>, preferences: NetworkPreferences): JSONObject {
        require(profile.host.isNotBlank()) { "Hysteria2 server is required" }
        require(profile.password.isNotBlank()) { "Hysteria2 authentication is required" }
        val hysteria = JSONObject()
            .put("version", 2)
            .put("auth", profile.password)
        normalizeSeconds(options["udpIdleTimeout"], minimum = 2, maximum = 600)
            ?.let { hysteria.put("udpIdleTimeout", it) }

        val streamSettings = JSONObject()
            .put("network", "hysteria")
            .put("security", "tls")
            .put("hysteriaSettings", hysteria)
            .put("tlsSettings", tlsSettings(profile.sni.ifBlank { profile.host }, options))
        hysteriaFinalMask(options)?.let { streamSettings.put("finalmask", it) }

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
            .put("streamSettings", streamSettings)
            .put("mux", JSONObject().put("enabled", false).put("concurrency", -1))
    }

    /**
     * Xray 26.5 stores Hysteria2 obfuscation and QUIC tuning in finalmask,
     * not in hysteriaSettings. Keeping these values in the old object makes
     * imports look successful while the core silently ignores the options.
     */
    private fun hysteriaFinalMask(options: Map<String, String>): JSONObject? {
        val udpMasks = JSONArray()
        val obfs = options["obfs"].orEmpty().trim().lowercase()
        val obfsPassword = (options["obfs-password"] ?: options["obfsPassword"]).orEmpty().trim()
        if (obfs.isNotBlank() || obfsPassword.isNotBlank()) {
            require(obfs == "salamander") {
                "Hysteria2 obfuscation '$obfs' is not supported by the bundled Xray core"
            }
            require(obfsPassword.isNotBlank()) {
                "Hysteria2 Salamander obfuscation requires a password"
            }
            udpMasks.put(
                JSONObject()
                    .put("type", "salamander")
                    .put("settings", JSONObject().put("password", obfsPassword))
            )
        }

        val quicParams = JSONObject()
        normalizeMbps(options["upmbps"])?.let { quicParams.put("brutalUp", it) }
        normalizeMbps(options["downmbps"])?.let { quicParams.put("brutalDown", it) }
        if (quicParams.has("brutalUp") || quicParams.has("brutalDown")) {
            quicParams.put("congestion", "brutal")
        }

        val ports = normalizePortList(options["ports"])
        val hopInterval = normalizeSecondsRange(options["hopInterval"], minimum = 5)
        if (ports != null || hopInterval != null) {
            val udpHop = JSONObject()
            ports?.let { udpHop.put("ports", it) }
            hopInterval?.let { udpHop.put("interval", it) }
            quicParams.put("udpHop", udpHop)
        }

        if (udpMasks.length() == 0 && quicParams.length() == 0) return null
        return JSONObject()
            .putOpt("udp", udpMasks.takeIf { it.length() > 0 })
            .putOpt("quicParams", quicParams.takeIf { it.length() > 0 })
    }

    private fun streamSettings(
        profile: VpnProfile,
        options: Map<String, String>,
        preferences: NetworkPreferences,
        forceTls: Boolean = false
    ): JSONObject {
        val requested = options["net"] ?: options["type"] ?: options["network"] ?: "tcp"
        val network = normalizeNetwork(requested)
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
                    .put("multiMode", options["mode"].equals("multi", true))
            )
            "xhttp" -> stream.put(
                "xhttpSettings",
                JSONObject()
                    .put("host", options["host"].orEmpty())
                    .put("path", options["path"].orEmpty().ifBlank { "/" })
                    .putOpt("mode", options["mode"]?.takeIf(String::isNotBlank))
                    .putOpt("extra", options["extra"]?.takeIf(String::isNotBlank)?.let(::parseJsonObjectOrNull))
            )
            "httpupgrade" -> stream.put(
                "httpupgradeSettings",
                JSONObject()
                    .put("host", options["host"].orEmpty())
                    .put("path", options["path"].orEmpty().ifBlank { "/" })
            )
            "kcp" -> stream.put("kcpSettings", JSONObject().put("mtu", preferences.validatedMtu))
            "tcp" -> if (options["headerType"].equals("http", true)) {
                stream.put(
                    "tcpSettings",
                    JSONObject().put("header", JSONObject().put("type", "http"))
                )
            }
        }

        val security = when {
            options["security"].equals("reality", true) -> "reality"
            forceTls || profile.sslEnabled || options["security"].equals("tls", true) -> "tls"
            else -> null
        }
        if (security != null) {
            val sni = profile.sni.ifBlank { options["sni"].orEmpty() }.ifBlank { profile.host }
            stream.put("security", security)
            if (security == "reality") {
                val reality = tlsSettings(sni, options)
                val publicKey = options["pbk"] ?: options["publicKey"]
                require(!publicKey.isNullOrBlank()) { "REALITY public key is required" }
                reality.put("publicKey", publicKey)
                (options["sid"] ?: options["shortId"])?.takeIf(String::isNotBlank)?.let { reality.put("shortId", it) }
                (options["spx"] ?: options["spiderX"])?.takeIf(String::isNotBlank)?.let { reality.put("spiderX", it) }
                stream.put("realitySettings", reality)
            } else {
                stream.put("tlsSettings", tlsSettings(sni, options))
            }
        }
        return stream
    }

    private fun tlsSettings(sni: String, options: Map<String, String>): JSONObject = JSONObject()
        .put("serverName", sni)
        .putOpt("fingerprint", (options["fp"] ?: options["fingerprint"])?.takeIf(String::isNotBlank))
        .putOpt(
            "alpn",
            options["alpn"]?.takeIf(String::isNotBlank)?.let {
                JSONArray(it.split(',').map(String::trim).filter(String::isNotBlank))
            }
        )
        .putOpt("pinnedPeerCertSha256", options["pcs"]?.takeIf(String::isNotBlank))
        .putOpt("verifyPeerCertByName", options["vcn"]?.takeIf(String::isNotBlank))

    private fun normalizeMbps(raw: String?): String? {
        val value = raw?.trim()?.lowercase().orEmpty()
        if (value.isBlank()) return null
        val numeric = Regex("""\d+(?:\.\d+)?""")
        if (numeric.matches(value)) return "${value}mbps"
        require(Regex("""\d+(?:\.\d+)?\s*(?:k|m|g|t)?bps""").matches(value)) {
            "Invalid Hysteria2 bandwidth value: $value"
        }
        return value.replace(" ", "")
    }

    private fun normalizePortList(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        val normalized = value.replace(" ", "")
        require(Regex("""\d+(?:-\d+)?(?:,\d+(?:-\d+)?)*""").matches(normalized)) {
            "Invalid Hysteria2 port list: $value"
        }
        normalized.split(',').forEach { part ->
            val from = part.substringBefore('-').toInt()
            val to = part.substringAfter('-', part).toInt()
            require(from in 1..65535 && to in 1..65535 && from <= to) {
                "Invalid Hysteria2 port range: $part"
            }
        }
        return normalized
    }

    private fun normalizeSeconds(raw: String?, minimum: Int, maximum: Int): Int? {
        val value = raw?.trim()?.lowercase().orEmpty()
        if (value.isBlank()) return null
        val seconds = value.removeSuffix("seconds").removeSuffix("second").removeSuffix("s").trim().toIntOrNull()
            ?: error("Invalid duration: $value")
        require(seconds in minimum..maximum) {
            "Duration must be between $minimum and $maximum seconds"
        }
        return seconds
    }

    private fun normalizeSecondsRange(raw: String?, minimum: Int): String? {
        val value = raw?.trim()?.lowercase().orEmpty()
        if (value.isBlank()) return null
        val normalized = value
            .replace("seconds", "")
            .replace("second", "")
            .replace("s", "")
            .replace(" ", "")
        require(Regex("""\d+(?:-\d+)?""").matches(normalized)) {
            "Invalid duration range: $value"
        }
        normalized.split('-').forEach { seconds ->
            require(seconds.toInt() >= minimum) {
                "Hysteria2 hop interval must be at least $minimum seconds"
            }
        }
        return normalized
    }

    private fun normalizeNetwork(raw: String): String = when (raw.trim().lowercase()) {
        "ws", "websocket" -> "ws"
        "grpc" -> "grpc"
        "xhttp", "splithttp", "h2", "http" -> "xhttp"
        "httpupgrade" -> "httpupgrade"
        "kcp", "mkcp" -> "kcp"
        "hysteria", "hysteria2", "hy2" -> "hysteria"
        else -> "tcp"
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

    private fun parseJsonObjectOrNull(value: String): JSONObject? = runCatching { JSONObject(value) }.getOrNull()
}
