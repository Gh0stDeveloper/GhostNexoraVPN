package com.ghostnexora.vpn.util

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.ProxyConfig
import com.ghostnexora.vpn.data.model.VpnProfile
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

/** Imports protocol links and standard Xray JSON without discarding transport parameters. */
object ProtocolLinkParser {
    fun parseText(rawText: String): List<VpnProfile> {
        if (rawText.isBlank()) return emptyList()
        val trimmed = rawText.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseXrayJson(trimmed).takeIf(List<VpnProfile>::isNotEmpty)?.let { return it }
        }
        return rawText.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .flatMap { parseLine(it).asSequence() }
            .toList()
    }

    fun supportsProtocolLinks(rawText: String): Boolean = parseText(rawText).isNotEmpty()

    fun parseXrayJson(rawText: String): List<VpnProfile> {
        val trimmed = rawText.trim()
        return runCatching {
            val outbounds = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> {
                    val root = JSONObject(trimmed)
                    root.optJSONArray("outbounds")
                        ?: root.optJSONObject("outbound")?.let { JSONArray().put(it) }
                        ?: root.takeIf { it.has("protocol") }?.let { JSONArray().put(it) }
                        ?: JSONArray()
                }
            }
            buildList {
                for (index in 0 until outbounds.length()) {
                    val outbound = outbounds.optJSONObject(index) ?: continue
                    parseXrayOutbound(outbound)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseLine(line: String): List<VpnProfile> = when {
        line.startsWith("vmess://", true) -> parseVmess(line)?.let(::listOf).orEmpty()
        line.startsWith("vless://", true) -> parseVless(line)?.let(::listOf).orEmpty()
        line.startsWith("trojan://", true) -> parseTrojan(line)?.let(::listOf).orEmpty()
        line.startsWith("hysteria2://", true) || line.startsWith("hy2://", true) -> parseHysteria2(line)?.let(::listOf).orEmpty()
        line.startsWith("ssh://", true) -> parseSsh(line)?.let(::listOf).orEmpty()
        else -> emptyList()
    }

    private fun parseVmess(link: String): VpnProfile? {
        val decoded = decodeBase64ToString(link.substringAfter("vmess://").trim()) ?: return null
        val json = runCatching { JSONObject(decoded) }.getOrNull() ?: return null
        val host = json.optString("add").trim()
        val port = json.optString("port").toIntOrNull() ?: json.optInt("port", 443)
        val uuid = json.optString("id").trim()
        if (host.isBlank() || port !in 1..65535 || uuid.isBlank()) return null

        val security = json.optString("tls").trim().lowercase()
        val sni = json.optString("sni").ifBlank { json.optString("host") }.ifBlank { host }
        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = json.optString("ps").ifBlank { "VMess $host:$port" },
            host = host,
            port = port,
            username = uuid,
            method = "v2ray",
            connectionMode = ConnectionMode.V2RAY.id,
            sslEnabled = security == "tls" || security == "reality",
            sni = sni,
            payload = optionsString(
                "protocol" to "vmess",
                "net" to json.optString("net", "tcp"),
                "host" to json.optString("host"),
                "path" to json.optString("path"),
                "type" to json.optString("type"),
                "headerType" to json.optString("type"),
                "security" to security,
                "cipher" to json.optString("scy", "auto"),
                "aid" to json.optString("aid", "0"),
                "sni" to sni,
                "fp" to json.optString("fp"),
                "alpn" to json.optString("alpn"),
                "serviceName" to json.optString("serviceName"),
                "authority" to json.optString("authority"),
                "mode" to json.optString("mode"),
                "seed" to json.optString("seed")
            ),
            proxy = ProxyConfig(),
            tagsRaw = "vmess,v2ray",
            notes = "Importado desde vmess://",
            enabled = true
        )
    }

    private fun parseVless(link: String): VpnProfile? {
        val uri = safeUri(link) ?: return null
        val userId = decodeQueryComponent(uri.rawUserInfo.orEmpty()).trim()
        val host = uri.host.orEmpty().trim()
        val port = uri.port.takeIf { it in 1..65535 } ?: 443
        if (userId.isBlank() || host.isBlank()) return null

        val query = parseQuery(uri.rawQuery)
        val security = query["security"].orEmpty().lowercase()
        val sni = query["sni"].orEmpty().ifBlank { query["host"].orEmpty() }.ifBlank { host }
        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = decodeQueryComponent(uri.rawFragment.orEmpty()).ifBlank { "VLESS $host:$port" },
            host = host,
            port = port,
            username = userId,
            method = "v2ray",
            connectionMode = ConnectionMode.V2RAY.id,
            sslEnabled = security == "tls" || security == "reality",
            sni = sni,
            payload = optionsString(
                "protocol" to "vless",
                "net" to query["type"].orEmpty(),
                "host" to query["host"].orEmpty(),
                "path" to query["path"].orEmpty(),
                "flow" to query["flow"].orEmpty(),
                "security" to security,
                "encryption" to query["encryption"].orEmpty().ifBlank { "none" },
                "sni" to sni,
                "fp" to query["fp"].orEmpty(),
                "pbk" to query["pbk"].orEmpty(),
                "sid" to query["sid"].orEmpty(),
                "spx" to query["spx"].orEmpty(),
                "alpn" to query["alpn"].orEmpty(),
                "serviceName" to query["serviceName"].orEmpty(),
                "authority" to query["authority"].orEmpty(),
                "mode" to query["mode"].orEmpty(),
                "headerType" to query["headerType"].orEmpty(),
                "seed" to query["seed"].orEmpty(),
                "packetEncoding" to query["packetEncoding"].orEmpty()
            ),
            proxy = ProxyConfig(),
            tagsRaw = "vless,v2ray",
            notes = "Importado desde vless://",
            enabled = true
        )
    }

    private fun parseTrojan(link: String): VpnProfile? {
        val uri = safeUri(link) ?: return null
        val password = decodeQueryComponent(uri.rawUserInfo.orEmpty()).trim()
        val host = uri.host.orEmpty().trim()
        val port = uri.port.takeIf { it in 1..65535 } ?: 443
        if (password.isBlank() || host.isBlank()) return null

        val query = parseQuery(uri.rawQuery)
        val sni = query["sni"].orEmpty().ifBlank { query["host"].orEmpty() }.ifBlank { host }
        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = decodeQueryComponent(uri.rawFragment.orEmpty()).ifBlank { "Trojan $host:$port" },
            host = host,
            port = port,
            password = password,
            method = "trojan",
            connectionMode = ConnectionMode.TROJAN.id,
            sslEnabled = true,
            sni = sni,
            payload = optionsString(
                "net" to query["type"].orEmpty(),
                "host" to query["host"].orEmpty(),
                "path" to query["path"].orEmpty(),
                "security" to query["security"].orEmpty().ifBlank { "tls" },
                "sni" to sni,
                "fp" to query["fp"].orEmpty(),
                "alpn" to query["alpn"].orEmpty(),
                "serviceName" to query["serviceName"].orEmpty(),
                "authority" to query["authority"].orEmpty(),
                "mode" to query["mode"].orEmpty(),
                "headerType" to query["headerType"].orEmpty()
            ),
            proxy = ProxyConfig(),
            tagsRaw = "trojan",
            notes = "Importado desde trojan://",
            enabled = true
        )
    }

    private fun parseHysteria2(link: String): VpnProfile? {
        val uri = safeUri(link) ?: return null
        val auth = decodeQueryComponent(uri.rawUserInfo.orEmpty()).trim()
        val host = uri.host.orEmpty().trim()
        val port = uri.port.takeIf { it in 1..65535 } ?: 443
        if (auth.isBlank() || host.isBlank()) return null

        val query = parseQuery(uri.rawQuery)
        val sni = query["sni"].orEmpty().ifBlank { host }
        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = decodeQueryComponent(uri.rawFragment.orEmpty()).ifBlank { "Hysteria2 $host:$port" },
            host = host,
            port = port,
            password = auth,
            method = "udp",
            connectionMode = ConnectionMode.UDP.id,
            sslEnabled = true,
            sni = sni,
            payload = optionsString(
                "alpn" to query["alpn"].orEmpty(),
                "obfs" to query["obfs"].orEmpty(),
                "obfs-password" to query["obfs-password"].orEmpty(),
                "udpIdleTimeout" to query["udpIdleTimeout"].orEmpty(),
                "ports" to (query["ports"] ?: query["mport"]).orEmpty(),
                "hopInterval" to query["hopInterval"].orEmpty(),
                "upmbps" to query["upmbps"].orEmpty(),
                "downmbps" to query["downmbps"].orEmpty()
            ),
            proxy = ProxyConfig(),
            tagsRaw = "hysteria2,udp",
            notes = "Importado desde ${uri.scheme}://",
            enabled = true
        )
    }

    private fun parseSsh(link: String): VpnProfile? {
        val uri = safeUri(link) ?: return null
        val host = uri.host.orEmpty().trim()
        val port = uri.port.takeIf { it in 1..65535 } ?: 22
        val userInfo = decodeQueryComponent(uri.rawUserInfo.orEmpty())
        val username = userInfo.substringBefore(':').trim()
        val password = userInfo.substringAfter(':', "").trim()
        if (host.isBlank() || username.isBlank()) return null

        val query = parseQuery(uri.rawQuery)
        val payload = query["payload"].orEmpty()
        val sni = query["sni"].orEmpty()
        val proxyHost = query["proxyHost"].orEmpty().ifBlank {
            query["proxy"].orEmpty().substringBefore(':').takeUnless { it == query["proxy"].orEmpty() }.orEmpty()
        }
        val proxyPort = query["proxyPort"]?.toIntOrNull()
            ?: query["proxy"].orEmpty().substringAfter(':', "").toIntOrNull()
            ?: 0
        val proxyType = query["proxyType"].orEmpty().ifBlank { "http" }
        val requestedMode = query["mode"].orEmpty().lowercase()
        val mode = when {
            requestedMode in setOf("ssl_payload_proxy", "payload_proxy_ssl") -> ConnectionMode.SSH_PAYLOAD_PROXY_SSL
            requestedMode in setOf("payload_proxy", "proxy_payload") -> ConnectionMode.SSH_PAYLOAD_PROXY
            requestedMode in setOf("ssl_payload", "payload_ssl") -> ConnectionMode.SSH_PAYLOAD_SSL
            requestedMode == "proxy" -> ConnectionMode.SSH_PROXY
            requestedMode == "payload" -> ConnectionMode.SSH_PAYLOAD
            requestedMode in setOf("ssl", "tls") -> ConnectionMode.SSL_SNI
            proxyHost.isNotBlank() && payload.isNotBlank() && sni.isNotBlank() -> ConnectionMode.SSH_PAYLOAD_PROXY_SSL
            proxyHost.isNotBlank() && payload.isNotBlank() -> ConnectionMode.SSH_PAYLOAD_PROXY
            proxyHost.isNotBlank() -> ConnectionMode.SSH_PROXY
            payload.isNotBlank() && sni.isNotBlank() -> ConnectionMode.SSH_PAYLOAD_SSL
            payload.isNotBlank() -> ConnectionMode.SSH_PAYLOAD
            sni.isNotBlank() -> ConnectionMode.SSL_SNI
            else -> ConnectionMode.SSH_DIRECT
        }
        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = decodeQueryComponent(uri.rawFragment.orEmpty()).ifBlank { "SSH $host:$port" },
            host = host,
            port = port,
            username = username,
            password = password,
            method = "ssh",
            connectionMode = mode.id,
            sslEnabled = mode.usesTls,
            sni = sni,
            payload = payload,
            proxy = ProxyConfig(proxyHost, proxyPort, proxyType),
            tagsRaw = "ssh,imported",
            notes = "Importado desde ssh://",
            enabled = true
        )
    }

    private fun parseXrayOutbound(outbound: JSONObject): VpnProfile? {
        val protocol = outbound.optString("protocol").trim().lowercase()
        val settings = outbound.optJSONObject("settings") ?: JSONObject()
        val stream = outbound.optJSONObject("streamSettings") ?: JSONObject()
        return when (protocol) {
            "vless", "vmess" -> parseXrayVnext(protocol, outbound, settings, stream)
            "trojan" -> parseXrayTrojan(outbound, settings, stream)
            "hysteria", "hysteria2" -> parseXrayHysteria(outbound, settings, stream)
            else -> null
        }
    }

    private fun parseXrayVnext(
        protocol: String,
        outbound: JSONObject,
        settings: JSONObject,
        stream: JSONObject
    ): VpnProfile? {
        val server = settings.optJSONArray("vnext")?.optJSONObject(0) ?: return null
        val user = server.optJSONArray("users")?.optJSONObject(0) ?: return null
        val host = server.optString("address").trim()
        val port = server.optInt("port", 443)
        val uuid = user.optString("id").trim()
        if (host.isBlank() || port !in 1..65535 || uuid.isBlank()) return null
        val streamOptions = extractStreamOptions(stream, host)
        val security = stream.optString("security")
        val sni = streamOptions["sni"].orEmpty().ifBlank { host }
        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = outbound.optString("tag").ifBlank { "${protocol.uppercase()} $host:$port" },
            host = host,
            port = port,
            username = uuid,
            method = "v2ray",
            connectionMode = ConnectionMode.V2RAY.id,
            sslEnabled = security.equals("tls", true) || security.equals("reality", true),
            sni = sni,
            payload = optionsString(
                "protocol" to protocol,
                "flow" to user.optString("flow"),
                "encryption" to user.optString("encryption", if (protocol == "vless") "none" else ""),
                "cipher" to user.optString("security", "auto"),
                "aid" to user.optString("alterId", "0"),
                *streamOptions.entries.map { it.key to it.value }.toTypedArray()
            ),
            tagsRaw = "$protocol,v2ray,xray-json",
            notes = "Importado desde configuración JSON Xray",
            enabled = true
        )
    }

    private fun parseXrayTrojan(
        outbound: JSONObject,
        settings: JSONObject,
        stream: JSONObject
    ): VpnProfile? {
        val server = settings.optJSONArray("servers")?.optJSONObject(0) ?: return null
        val host = server.optString("address").trim()
        val port = server.optInt("port", 443)
        val password = server.optString("password")
        if (host.isBlank() || port !in 1..65535 || password.isBlank()) return null
        val options = extractStreamOptions(stream, host)
        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = outbound.optString("tag").ifBlank { "Trojan $host:$port" },
            host = host,
            port = port,
            password = password,
            method = "trojan",
            connectionMode = ConnectionMode.TROJAN.id,
            sslEnabled = true,
            sni = options["sni"].orEmpty().ifBlank { host },
            payload = optionsString(*options.entries.map { it.key to it.value }.toTypedArray()),
            tagsRaw = "trojan,xray-json",
            notes = "Importado desde configuración JSON Xray",
            enabled = true
        )
    }

    private fun parseXrayHysteria(
        outbound: JSONObject,
        settings: JSONObject,
        stream: JSONObject
    ): VpnProfile? {
        val host = settings.optString("address").trim()
        val port = settings.optInt("port", 443)
        val hysteria = stream.optJSONObject("hysteriaSettings") ?: JSONObject()
        val auth = hysteria.optString("auth").ifBlank { settings.optString("auth") }
        if (host.isBlank() || port !in 1..65535 || auth.isBlank()) return null
        val finalMask = stream.optJSONObject("finalmask") ?: JSONObject()
        val quicParams = finalMask.optJSONObject("quicParams") ?: JSONObject()
        val udpHop = quicParams.optJSONObject("udpHop") ?: JSONObject()
        val salamander = finalMask.optJSONArray("udp")?.let { masks ->
            (0 until masks.length())
                .asSequence()
                .mapNotNull(masks::optJSONObject)
                .firstOrNull { it.optString("type").equals("salamander", true) }
        }
        val options = extractStreamOptions(stream, host).toMutableMap().apply {
            hysteria.optString("udpIdleTimeout").takeIf(String::isNotBlank)
                ?.let { put("udpIdleTimeout", it) }
            val obfs = salamander?.optString("type")
                .orEmpty()
                .ifBlank { hysteria.optString("obfs") }
            val obfsPassword = salamander
                ?.optJSONObject("settings")
                ?.optString("password")
                .orEmpty()
                .ifBlank { hysteria.optString("obfsPassword") }
            obfs.takeIf(String::isNotBlank)?.let { put("obfs", it) }
            obfsPassword.takeIf(String::isNotBlank)?.let { put("obfs-password", it) }
            quicParams.optString("brutalUp").takeIf(String::isNotBlank)
                ?.let { put("upmbps", it) }
            quicParams.optString("brutalDown").takeIf(String::isNotBlank)
                ?.let { put("downmbps", it) }
            udpHop.optString("ports").takeIf(String::isNotBlank)
                ?.let { put("ports", it) }
            udpHop.optString("interval").takeIf(String::isNotBlank)
                ?.let { put("hopInterval", it) }
        }
        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = outbound.optString("tag").ifBlank { "Hysteria2 $host:$port" },
            host = host,
            port = port,
            password = auth,
            method = "udp",
            connectionMode = ConnectionMode.UDP.id,
            sslEnabled = true,
            sni = options["sni"].orEmpty().ifBlank { host },
            payload = optionsString(*options.entries.map { it.key to it.value }.toTypedArray()),
            tagsRaw = "hysteria2,udp,xray-json",
            notes = "Importado desde configuración JSON Xray",
            enabled = true
        )
    }

    private fun extractStreamOptions(stream: JSONObject, fallbackHost: String): Map<String, String> {
        val network = stream.optString("network", "tcp")
        val tls = stream.optJSONObject("tlsSettings") ?: JSONObject()
        val reality = stream.optJSONObject("realitySettings") ?: JSONObject()
        val ws = stream.optJSONObject("wsSettings") ?: JSONObject()
        val grpc = stream.optJSONObject("grpcSettings") ?: JSONObject()
        val xhttp = stream.optJSONObject("xhttpSettings") ?: JSONObject()
        val upgrade = stream.optJSONObject("httpupgradeSettings") ?: JSONObject()
        val transport = when (network.lowercase()) {
            "ws" -> ws
            "grpc" -> grpc
            "xhttp", "splithttp" -> xhttp
            "httpupgrade" -> upgrade
            else -> JSONObject()
        }
        val security = stream.optString("security")
        val sni = reality.optString("serverName")
            .ifBlank { tls.optString("serverName") }
            .ifBlank { fallbackHost }
        val alpn = tls.optJSONArray("alpn")?.let(::jsonArrayToCsv).orEmpty()
        return linkedMapOf(
            "net" to network,
            "security" to security,
            "sni" to sni,
            "host" to transport.optString("host").ifBlank { transport.optString("authority") },
            "path" to transport.optString("path"),
            "serviceName" to grpc.optString("serviceName"),
            "authority" to grpc.optString("authority"),
            "mode" to transport.optString("mode"),
            "fp" to reality.optString("fingerprint").ifBlank { tls.optString("fingerprint") },
            "pbk" to reality.optString("publicKey"),
            "sid" to reality.optString("shortId"),
            "spx" to reality.optString("spiderX"),
            "alpn" to alpn
        ).filterValues(String::isNotBlank)
    }

    private fun jsonArrayToCsv(array: JSONArray): String = buildList {
        for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add)
    }.joinToString(",")

    private fun optionsString(vararg values: Pair<String, String>): String = values
        .filter { (_, value) -> value.isNotBlank() }
        .joinToString(" | ") { (key, value) -> "$key=$value" }

    private fun safeUri(link: String): URI? = runCatching { URI(link) }.getOrNull()
        ?: runCatching { URI(URLDecoder.decode(link, StandardCharsets.UTF_8.name())) }.getOrNull()

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { part ->
            val pieces = part.split("=", limit = 2)
            val key = pieces.getOrNull(0)?.let(::decodeQueryComponent).orEmpty()
            val value = pieces.getOrNull(1)?.let(::decodeQueryComponent).orEmpty()
            if (key.isBlank()) null else key to value
        }.toMap()
    }

    private fun decodeQueryComponent(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

    private fun decodeBase64ToString(value: String): String? {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = when (normalized.length % 4) {
            2 -> "$normalized=="
            3 -> "$normalized="
            else -> normalized
        }
        return runCatching { String(Base64.getDecoder().decode(padded), Charsets.UTF_8) }.getOrNull()
    }
}
