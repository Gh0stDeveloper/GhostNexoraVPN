package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.TlsVerificationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.X509TrustManager

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
    fun customCompatibilityUsesOnlyItsScopedCertificatePolicy() {
        assertNull(TlsTransport.trustManagersFor(TlsVerificationMode.STRICT))

        val managers = TlsTransport.trustManagersFor(TlsVerificationMode.CUSTOM_SNI)
        assertNotNull(managers)
        val trustManager = managers!!.single() as X509TrustManager
        val rejection = runCatching {
            trustManager.checkServerTrusted(emptyArray<X509Certificate>(), "RSA")
        }.exceptionOrNull()

        assertTrue(rejection is CertificateException)
        assertTrue(trustManager.acceptedIssuers.isEmpty())
        assertTrue(TlsVerificationMode.STRICT.verifiesCertificateChain)
        assertFalse(TlsVerificationMode.CUSTOM_SNI.verifiesCertificateChain)
    }

    @Test
    fun storedCompatibilityAliasesAreAcceptedWithoutChangingSafeDefault() {
        assertEquals(
            TlsVerificationMode.CUSTOM_SNI,
            TlsVerificationMode.fromStored("allow_sni_mismatch")
        )
        assertEquals(TlsVerificationMode.STRICT, TlsVerificationMode.fromStored(null))
        assertTrue(TlsVerificationMode.STRICT.verifiesHostname)
        assertTrue(TlsVerificationMode.STRICT.verifiesCertificateChain)
    }
}
