package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.NetworkPreferences
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsTunnelProfileTest {
    private val fixture = """
        {
          "type":"gnx-dnstt","version":1,"domain":"t.example.net","transport":"udp",
          "resolver":"192.0.2.53:53","publicKey":"${"10".repeat(32)}",
          "outbound":{
            "protocol":"vless",
            "settings":{"vnext":[{"address":"backend.example.net","port":443,"users":[
              {"id":"3e897e8e-3403-48d7-8caf-e39f07a8e0a0","encryption":"none"}
            ]}]},
            "streamSettings":{"network":"raw","security":"tls"}
          }
        }
    """.trimIndent()

    @Test fun rewritesEndpointButKeepsOriginalTlsName() {
        val profile = DnsTunnelProfile.parse(fixture)
        val result = JSONObject(StableXrayConfigFactory.buildDnstt(profile.backend, 32123, NetworkPreferences()))
        val outbound = result.getJSONArray("outbounds").getJSONObject(0)
        val endpoint = outbound.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertEquals("127.0.0.1", endpoint.getString("address"))
        assertEquals(32123, endpoint.getInt("port"))
        assertEquals("backend.example.net", outbound.getJSONObject("streamSettings")
            .getJSONObject("tlsSettings").getString("serverName"))
        val rules = result.getJSONObject("routing").getJSONArray("rules")
        assertEquals("dns-out", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals("block", rules.getJSONObject(1).getString("outboundTag"))
    }

    @Test fun rejectsQuicAndInvalidResolver() {
        assertTrue(runCatching { DnsTunnelProfile.parse(fixture.replace("raw", "quic")) }.isFailure)
        assertTrue(runCatching { DnsTunnelProfile.parse(fixture.replace(":53", ":70000")) }.isFailure)
    }
}
