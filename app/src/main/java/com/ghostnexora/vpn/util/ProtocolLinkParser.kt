package com.ghostnexora.vpn.util

import android.util.Base64
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.ProxyConfig
import com.ghostnexora.vpn.data.model.VpnProfile
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Importador de enlaces de protocolos compatibles con el motor actual.
 * Conserva parámetros de transporte importantes en el campo de opciones para
 * que [com.ghostnexora.vpn.tunnel.XrayConfigFactory] pueda reconstruirlos.
 */
object ProtocolLinkParser {

    fun parseText(rawText: String): List<VpnProfile> {
        if (rawText.isBlank()) return emptyList()
        return rawText
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .flatMap { parseLine(it).asSequence() }
            .toList()
    }

    fun supportsProtocolLinks(rawText: String): Boolean = parseText(rawText).isNotEmpty()

    private fun parseLine(line: String): List<VpnProfile> = when {
        line.startsWith("vmess://", ignoreCase = true) -> parseVmess(line)?.let(::listOf).orEmpty()
        line.startsWith("vless://", ignoreCase = true) -> parseVless(line)?.let(::listOf).orEmpty()
        line.startsWith("trojan://", ignoreCase = true) -> parseTrojan(line)?.let(::listOf).orEmpty()
        line.startsWith("hysteria2://", ignoreCase = true) || line.startsWith("hy2://", ignoreCase = true) ->
            parseHysteria2(line)?.let(::listOf).orEmpty()
        else -> emptyList()
    }

    private fun parseVmess(link: String): VpnProfile? {
        val encoded = link.substringAfter("vmess://").trim()
        val decodedJson = decodeBase64ToString(encoded) ?: return null
        val json = runCatching { JSONObject(decodedJson) }.getOrNull() ?: return null

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
            sslEnabled = security == "tls",
            sni = sni,
            payload = optionsString(
                "protocol" to "vmess",
                "net" to json.optString("net", "tcp"),
                "host" to json.optString("host"),
                "path" to json.optString("path"),
                "type" to json.optString("type"),
                "security" to security,
                "cipher" to json.optString("scy", "auto"),
                "sni" to sni,
                "fp" to json.optString("fp"),
                "alpn" to json.optString("alpn"),
                "serviceName" to json.optString("serviceName"),
                "authority" to json.optString("authority")
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
        val name = decodeQueryComponent(uri.rawFragment.orEmpty()).ifBlank { "VLESS $host:$port" }

        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name,
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
                "mode" to query["mode"].orEmpty()
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
        val name = decodeQueryComponent(uri.rawFragment.orEmpty()).ifBlank { "Trojan $host:$port" }

        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name,
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
                "sni" to sni,
                "fp" to query["fp"].orEmpty(),
                "alpn" to query["alpn"].orEmpty(),
                "serviceName" to query["serviceName"].orEmpty(),
                "authority" to query["authority"].orEmpty()
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
        val name = decodeQueryComponent(uri.rawFragment.orEmpty()).ifBlank { "Hysteria2 $host:$port" }

        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name,
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
                "udpIdleTimeout" to query["udpIdleTimeout"].orEmpty()
            ),
            proxy = ProxyConfig(),
            tagsRaw = "hysteria2,udp",
            notes = "Importado desde ${uri.scheme}://",
            enabled = true
        )
    }

    private fun optionsString(vararg values: Pair<String, String>): String = values
        .filter { (_, value) -> value.isNotBlank() }
        .joinToString(" | ") { (key, value) -> "$key=$value" }

    private fun safeUri(link: String): URI? = runCatching {
        URI(link)
    }.getOrNull() ?: runCatching {
        URI(URLDecoder.decode(link, StandardCharsets.UTF_8.name()))
    }.getOrNull()

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&")
            .mapNotNull { part ->
                val pieces = part.split("=", limit = 2)
                val key = pieces.getOrNull(0)?.let(::decodeQueryComponent).orEmpty()
                val value = pieces.getOrNull(1)?.let(::decodeQueryComponent).orEmpty()
                if (key.isBlank()) null else key to value
            }
            .toMap()
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
        return runCatching {
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }
}
