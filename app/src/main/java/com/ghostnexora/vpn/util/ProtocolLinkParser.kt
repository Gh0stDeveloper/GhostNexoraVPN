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
 * Convierte enlaces compartidos por la comunidad a perfiles internos de Ghost Nexora VPN.
 *
 * Soporta:
 * - vmess:// (Base64 JSON)
 * - vless:// (URI estándar)
 * - trojan:// (URI estándar)
 *
 * El objetivo es conservar la configuración en una forma importable, aunque el
 * motor de conexión aún no ejecute todos los protocolos de forma nativa.
 */
object ProtocolLinkParser {

    fun parseText(rawText: String): List<VpnProfile> {
        if (rawText.isBlank()) return emptyList()

        return rawText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .flatMap { line -> parseLine(line).asSequence() }
            .toList()
    }

    fun supportsProtocolLinks(rawText: String): Boolean =
        parseText(rawText).isNotEmpty()

    private fun parseLine(line: String): List<VpnProfile> {
        return when {
            line.startsWith("vmess://", ignoreCase = true) ->
                parseVmess(line)?.let(::listOf).orEmpty()

            line.startsWith("vless://", ignoreCase = true) ->
                parseVless(line)?.let(::listOf).orEmpty()

            line.startsWith("trojan://", ignoreCase = true) ->
                parseTrojan(line)?.let(::listOf).orEmpty()

            else -> emptyList()
        }
    }

    private fun parseVmess(link: String): VpnProfile? {
        val encoded = link.removePrefix("vmess://").trim()
        val decodedJson = decodeBase64ToString(encoded) ?: return null
        val payload = runCatching { JSONObject(decodedJson) }.getOrNull() ?: return null

        val host = payload.optString("add").trim()
        val port = payload.optString("port").toIntOrNull() ?: payload.optInt("port", 443)
        val uuid = payload.optString("id").trim()
        if (host.isBlank() || port !in 1..65535 || uuid.isBlank()) return null

        val sni = payload.optString("sni")
            .takeIf { it.isNotBlank() }
            ?: payload.optString("host").takeIf { it.isNotBlank() }
            ?: host

        val network = payload.optString("net", "tcp")
        val path = payload.optString("path")
        val tls = payload.optString("tls")
        val type = payload.optString("type")

        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = payload.optString("ps").ifBlank { "vmess $host:$port" },
            host = host,
            port = port,
            username = uuid,
            password = "",
            method = "v2ray",
            connectionMode = ConnectionMode.V2RAY.id,
            sslEnabled = tls.equals("tls", ignoreCase = true),
            sni = sni,
            payload = listOfNotNull(
                if (path.isNotBlank()) "path=$path" else null,
                if (network.isNotBlank()) "net=$network" else null,
                if (type.isNotBlank()) "type=$type" else null
            ).joinToString(" | "),
            proxy = ProxyConfig(),
            tagsRaw = "vmess,v2ray",
            notes = "Importado desde vmess://",
            enabled = true
        )
    }

    private fun parseVless(link: String): VpnProfile? {
        val uri = safeUri(link) ?: return null
        val userId = uri.userInfo.orEmpty().trim()
        val host = uri.host.orEmpty().trim()
        val port = uri.port.takeIf { it in 1..65535 } ?: 443
        if (userId.isBlank() || host.isBlank()) return null

        val query = parseQuery(uri.rawQuery)
        val sni = query["sni"]
            ?.ifBlank { null }
            ?: query["host"]
            ?.ifBlank { null }
            ?: host

        val security = query["security"].orEmpty()
        val path = query["path"].orEmpty()
        val flow = query["flow"].orEmpty()
        val network = query["type"].orEmpty()

        val name = uri.fragment?.takeIf { it.isNotBlank() } ?: "vless $host:$port"

        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            host = host,
            port = port,
            username = userId,
            password = "",
            method = "v2ray",
            connectionMode = ConnectionMode.V2RAY.id,
            sslEnabled = security.equals("tls", ignoreCase = true),
            sni = sni,
            payload = listOfNotNull(
                if (path.isNotBlank()) "path=$path" else null,
                if (network.isNotBlank()) "net=$network" else null,
                if (flow.isNotBlank()) "flow=$flow" else null
            ).joinToString(" | "),
            proxy = ProxyConfig(),
            tagsRaw = "vless,v2ray",
            notes = "Importado desde vless://",
            enabled = true
        )
    }

    private fun parseTrojan(link: String): VpnProfile? {
        val uri = safeUri(link) ?: return null
        val password = uri.userInfo.orEmpty().trim()
        val host = uri.host.orEmpty().trim()
        val port = uri.port.takeIf { it in 1..65535 } ?: 443
        if (password.isBlank() || host.isBlank()) return null

        val query = parseQuery(uri.rawQuery)
        val sni = query["sni"]
            ?.ifBlank { null }
            ?: query["host"]
            ?.ifBlank { null }
            ?: host

        val name = uri.fragment?.takeIf { it.isNotBlank() } ?: "trojan $host:$port"

        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            host = host,
            port = port,
            username = "",
            password = password,
            method = "trojan",
            connectionMode = ConnectionMode.TROJAN.id,
            sslEnabled = true,
            sni = sni,
            payload = query["path"].orEmpty(),
            proxy = ProxyConfig(),
            tagsRaw = "trojan",
            notes = "Importado desde trojan://",
            enabled = true
        )
    }

    private fun safeUri(link: String): URI? = runCatching {
        URI(link)
    }.getOrNull() ?: runCatching {
        // Algunos enlaces llegan con espacios o caracteres escapados.
        URI(URLDecoder.decode(link, StandardCharsets.UTF_8.name()))
    }.getOrNull()

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()

        return rawQuery.split("&")
            .mapNotNull { part ->
                val pieces = part.split("=", limit = 2)
                if (pieces.isEmpty()) return@mapNotNull null

                val key = decodeQueryComponent(pieces[0])
                val value = pieces.getOrNull(1)?.let(::decodeQueryComponent).orEmpty()
                if (key.isBlank()) null else key to value
            }
            .toMap()
    }

    private fun decodeQueryComponent(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

    private fun decodeBase64ToString(value: String): String? {
        val normalized = value
            .replace('-', '+')
            .replace('_', '/')

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
