package com.ghostnexora.vpn.util

import com.ghostnexora.vpn.data.model.ConnectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolLinkParserTest {
    @Test
    fun parsesSshTlsPayloadProxyLink() {
        val link = "ssh://ghost:secret@ssh.example.com:443?mode=ssl_payload_proxy&sni=cdn.example.com&payload=CONNECT%20%5Bhost_port%5D%20HTTP%2F1.1%5Bcrlf%5D%5Bcrlf%5D&proxyHost=proxy.example.com&proxyPort=8080&proxyType=http#Servidor%20SSH"
        val profile = ProtocolLinkParser.parseText(link).single()

        assertEquals(ConnectionMode.SSH_PAYLOAD_PROXY_SSL, profile.selectedMode)
        assertEquals("ssh.example.com", profile.host)
        assertEquals("ghost", profile.username)
        assertEquals("secret", profile.password)
        assertEquals("cdn.example.com", profile.sni)
        assertEquals("proxy.example.com", profile.proxy.host)
        assertTrue(profile.payload.contains("[host_port]"))
    }

    @Test
    fun parsesVlessRealityLinkWithoutLosingParameters() {
        val link = "vless://a3482e88-686a-4a58-8126-99c9df64b7bf@vpn.example.com:443?type=grpc&security=reality&sni=cdn.example.com&pbk=public-key&sid=abcd&fp=chrome&serviceName=tunnel#Reality"
        val profile = ProtocolLinkParser.parseText(link).single()

        assertEquals(ConnectionMode.V2RAY, profile.selectedMode)
        assertEquals("vpn.example.com", profile.host)
        assertTrue(profile.payload.contains("security=reality"))
        assertTrue(profile.payload.contains("pbk=public-key"))
        assertTrue(profile.payload.contains("serviceName=tunnel"))
    }

    @Test
    fun parsesStandardXrayOutboundJson() {
        val json = """
            {
              "outbounds": [
                {
                  "tag": "production-vless",
                  "protocol": "vless",
                  "settings": {
                    "vnext": [{
                      "address": "vpn.example.com",
                      "port": 443,
                      "users": [{
                        "id": "a3482e88-686a-4a58-8126-99c9df64b7bf",
                        "encryption": "none",
                        "flow": "xtls-rprx-vision"
                      }]
                    }]
                  },
                  "streamSettings": {
                    "network": "grpc",
                    "security": "tls",
                    "grpcSettings": {"serviceName": "ghost"},
                    "tlsSettings": {"serverName": "cdn.example.com", "fingerprint": "chrome"}
                  }
                }
              ]
            }
        """.trimIndent()

        val profile = ProtocolLinkParser.parseXrayJson(json).single()
        assertEquals("production-vless", profile.name)
        assertEquals("cdn.example.com", profile.sni)
        assertTrue(profile.payload.contains("flow=xtls-rprx-vision"))
        assertTrue(profile.payload.contains("serviceName=ghost"))
    }

    @Test
    fun parsesCanonicalHysteria2FinalMaskOptions() {
        val json = """
            {
              "outbounds": [{
                "tag": "production-hy2",
                "protocol": "hysteria",
                "settings": {
                  "address": "hy2.example.com",
                  "port": 443,
                  "version": 2
                },
                "streamSettings": {
                  "network": "hysteria",
                  "security": "tls",
                  "hysteriaSettings": {
                    "auth": "auth-secret",
                    "udpIdleTimeout": 60
                  },
                  "tlsSettings": {
                    "serverName": "cdn.example.com",
                    "alpn": ["h3"]
                  },
                  "finalmask": {
                    "udp": [{
                      "type": "salamander",
                      "settings": {"password": "mask-secret"}
                    }],
                    "quicParams": {
                      "congestion": "brutal",
                      "brutalUp": "20mbps",
                      "brutalDown": "100mbps",
                      "udpHop": {
                        "ports": "443,8443-8445",
                        "interval": "30"
                      }
                    }
                  }
                }
              }]
            }
        """.trimIndent()

        val profile = ProtocolLinkParser.parseXrayJson(json).single()
        assertEquals(ConnectionMode.UDP, profile.selectedMode)
        assertEquals("hy2.example.com", profile.host)
        assertEquals("auth-secret", profile.password)
        assertEquals("cdn.example.com", profile.sni)
        assertTrue(profile.payload.contains("obfs=salamander"))
        assertTrue(profile.payload.contains("obfs-password=mask-secret"))
        assertTrue(profile.payload.contains("ports=443,8443-8445"))
        assertTrue(profile.payload.contains("hopInterval=30"))
        assertTrue(profile.payload.contains("upmbps=20mbps"))
        assertTrue(profile.payload.contains("downmbps=100mbps"))
        assertTrue(profile.payload.contains("udpIdleTimeout=60"))
    }
}
