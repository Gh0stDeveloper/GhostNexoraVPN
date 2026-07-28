package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableXrayConfigFactoryTest {
    @Test
    fun vlessConfigUsesCanonicalEndpointAndExplicitTunRouting() {
        val profile = VpnProfile(
            name = "VLESS test",
            host = "vpn.example.com",
            port = 443,
            username = "a3482e88-686a-4a58-8126-99c9df64b7bf",
            connectionMode = ConnectionMode.V2RAY.id,
            sslEnabled = true,
            sni = "cdn.example.com",
            payload = "protocol=vless|network=ws|host=cdn.example.com|path=/vpn|security=tls"
        )

        val root = JSONObject(StableXrayConfigFactory.build(profile))
        val inbound = root.getJSONArray("inbounds").getJSONObject(0)
        assertEquals(StableXrayConfigFactory.TUN_MTU, inbound.getJSONObject("settings").getInt("mtu"))

        val proxy = root.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("proxy", proxy.getString("tag"))
        assertEquals("vless", proxy.getString("protocol"))
        val endpoint = proxy.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertEquals("vpn.example.com", endpoint.getString("address"))
        assertEquals(443, endpoint.getInt("port"))
        assertEquals(profile.username, endpoint.getJSONArray("users").getJSONObject(0).getString("id"))
        assertEquals("ws", proxy.getJSONObject("streamSettings").getString("network"))

        val rules = root.getJSONObject("routing").getJSONArray("rules")
        assertEquals("dns-out", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals("proxy", rules.getJSONObject(1).getString("outboundTag"))
        assertEquals("tcp,udp", rules.getJSONObject(1).getString("network"))
    }

    @Test
    fun dnsHasBootstrapAddressesAndNoDirectTunFallback() {
        val profile = VpnProfile(
            name = "VMess test",
            host = "vmess.example.com",
            port = 443,
            username = "a3482e88-686a-4a58-8126-99c9df64b7bf",
            connectionMode = ConnectionMode.V2RAY.id,
            sslEnabled = true,
            sni = "vmess.example.com",
            tagsRaw = "vmess",
            payload = "network=grpc|serviceName=tunnel|security=tls"
        )

        val root = JSONObject(StableXrayConfigFactory.build(profile))
        val hosts = root.getJSONObject("dns").getJSONObject("hosts")
        assertTrue(hosts.getJSONArray("cloudflare-dns.com").length() >= 2)
        assertTrue(hosts.getJSONArray("dns.google").length() >= 2)

        val outbounds = root.getJSONArray("outbounds")
        assertTrue((0 until outbounds.length()).any { outbounds.getJSONObject(it).getString("tag") == "direct" })
        val tunRules = root.getJSONObject("routing").getJSONArray("rules")
        assertTrue((0 until tunRules.length()).none {
            tunRules.getJSONObject(it).optString("outboundTag") == "direct"
        })
    }

    @Test
    fun sshBridgeUsesCanonicalSocksServer() {
        val profile = VpnProfile(
            name = "SSH test",
            host = "ssh.example.com",
            port = 443,
            username = "user",
            password = "password",
            connectionMode = ConnectionMode.SSL_SNI.id,
            sslEnabled = true,
            sni = "front.example.com"
        )

        val root = JSONObject(StableXrayConfigFactory.build(profile, sshSocksPort = 10808))
        val proxy = root.getJSONArray("outbounds").getJSONObject(0)
        val server = proxy.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("socks", proxy.getString("protocol"))
        assertEquals("127.0.0.1", server.getString("address"))
        assertEquals(10808, server.getInt("port"))
    }

    @Test
    fun hysteria2UsesFinalMaskForObfuscationBandwidthAndPortHopping() {
        val profile = VpnProfile(
            name = "Hysteria2 test",
            host = "hy2.example.com",
            port = 443,
            password = "auth-secret",
            connectionMode = ConnectionMode.UDP.id,
            sslEnabled = true,
            sni = "cdn.example.com",
            payload = "alpn=h3|obfs=salamander|obfs-password=mask-secret|" +
                "udpIdleTimeout=60s|ports=443,8443-8445|hopInterval=30s|" +
                "upmbps=20|downmbps=100"
        )

        val root = JSONObject(StableXrayConfigFactory.build(profile))
        val proxy = root.getJSONArray("outbounds").getJSONObject(0)
        val settings = proxy.getJSONObject("settings")
        val stream = proxy.getJSONObject("streamSettings")
        val hysteria = stream.getJSONObject("hysteriaSettings")
        val finalMask = stream.getJSONObject("finalmask")
        val salamander = finalMask.getJSONArray("udp").getJSONObject(0)
        val quic = finalMask.getJSONObject("quicParams")
        val udpHop = quic.getJSONObject("udpHop")

        assertEquals("hysteria", proxy.getString("protocol"))
        assertEquals("hy2.example.com", settings.getString("address"))
        assertEquals(2, settings.getInt("version"))
        assertEquals("hysteria", stream.getString("network"))
        assertEquals("tls", stream.getString("security"))
        assertEquals("auth-secret", hysteria.getString("auth"))
        assertEquals(60, hysteria.getInt("udpIdleTimeout"))
        assertEquals("cdn.example.com", stream.getJSONObject("tlsSettings").getString("serverName"))
        assertEquals("salamander", salamander.getString("type"))
        assertEquals("mask-secret", salamander.getJSONObject("settings").getString("password"))
        assertEquals("20mbps", quic.getString("brutalUp"))
        assertEquals("100mbps", quic.getString("brutalDown"))
        assertEquals("brutal", quic.getString("congestion"))
        assertEquals("443,8443-8445", udpHop.getString("ports"))
        assertEquals("30", udpHop.getString("interval"))
    }
}
