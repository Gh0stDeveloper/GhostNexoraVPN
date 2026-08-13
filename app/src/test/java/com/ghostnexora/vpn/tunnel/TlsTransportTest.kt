package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.TlsVerificationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters

class TlsTransportTest {
    @Test
    fun strictModeSendsSniAndEnablesHostnameVerification() {
        val parameters = TlsTransport.configureParameters(
            SSLParameters(),
            "www.twitter.com",
            TlsVerificationMode.STRICT
        )
        assertEquals("HTTPS", parameters.endpointIdentificationAlgorithm)
        assertEquals("www.twitter.com", (parameters.serverNames.single() as SNIHostName).asciiName)
    }

    @Test
    fun customCompatibilitySendsSameSniWithoutSanMatching() {
        val parameters = TlsTransport.configureParameters(
            SSLParameters(),
            "www.twitter.com",
            TlsVerificationMode.CUSTOM_SNI
        )
        assertNull(parameters.endpointIdentificationAlgorithm)
        assertEquals("www.twitter.com", (parameters.serverNames.single() as SNIHostName).asciiName)
    }

    @Test
    fun storedCompatibilityAliasesAreAcceptedWithoutChangingSafeDefault() {
        assertEquals(
            TlsVerificationMode.CUSTOM_SNI,
            TlsVerificationMode.fromStored("allow_sni_mismatch")
        )
        assertEquals(TlsVerificationMode.STRICT, TlsVerificationMode.fromStored(null))
        assertTrue(TlsVerificationMode.STRICT.verifiesHostname)
    }
}
