package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.util.ProtocolLinkParser
import org.json.JSONObject
import org.json.JSONException
import java.net.URI

internal data class DnsTunnelProfile(
    val domain: String,
    val resolver: String,
    val transport: String,
    val publicKey: String,
    val backend: VpnProfile
) {
    companion object {
        private val domainPattern =
            Regex("(?=.{1,100}$)[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+")

        fun parse(payload: String): DnsTunnelProfile = try {
            parseJson(JSONObject(payload))
        } catch (_: JSONException) {
            throw IllegalArgumentException("Perfil JSON DNSTT inválido")
        }

        private fun parseJson(root: JSONObject): DnsTunnelProfile {
            require(root.optString("type") == "gnx-dnstt" && root.optInt("version") == 1) {
                "Versión del perfil DNSTT no compatible"
            }
            val domain = root.optString("domain").trimEnd('.')
            require(domainPattern.matches(domain)) { "Dominio DNSTT inválido" }
            val key = root.optString("publicKey")
            require(key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } &&
                key.any { it != '0' }) { "Clave pública DNSTT inválida" }

            val transport = root.optString("transport", "udp")
            val resolver = root.optString("resolver")
            val resolverValid = when (transport) {
                "udp", "dot" -> validResolver(resolver)
                "doh" -> runCatching {
                    URI(resolver).let {
                        it.scheme == "https" && it.host != null && it.rawUserInfo == null &&
                            it.rawFragment == null && it.rawQuery == null && it.port in (-1..65535) && it.port != 0 &&
                            it.rawPath.isNotBlank()
                    }
                }.getOrDefault(false)
                else -> false
            }
            require(resolverValid) { "Resolver DNSTT inválido" }

            val outbound = root.getJSONObject("outbound")
            val stream = outbound.optJSONObject("streamSettings")
            require(stream == null || stream.optString("network") in setOf("", "raw", "tcp")) {
                "DNSTT requiere un backend TCP directo"
            }
            val backend = requireNotNull(ProtocolLinkParser.parseXrayJson(outbound.toString()).singleOrNull()) {
                "Backend DNSTT no compatible"
            }
            require(backend.selectedMode in setOf(
                ConnectionMode.V2RAY, ConnectionMode.TROJAN, ConnectionMode.SHADOWSOCKS,
                ConnectionMode.HTTPS_PROXY, ConnectionMode.SOCKS5_TLS
            )) { "Backend DNSTT no compatible" }

            return DnsTunnelProfile(domain, resolver, transport, key, backend)
        }

        private fun validResolver(address: String): Boolean = runCatching {
            URI("dns://$address").let {
                it.host != null && it.port in 1..65535 && it.rawUserInfo == null &&
                    it.rawPath.isNullOrEmpty() && it.rawQuery == null && it.rawFragment == null
            }
        }.getOrDefault(false)
    }
}
