package com.ghostnexora.vpn.tunnel

import com.jcraft.jsch.JSch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JschRuntimeTest {
    @Test
    fun installsApplicationOwnedRandomProviderWithoutReflection() {
        val messages = mutableListOf<String>()

        JschRuntime.install(messages::add)

        assertEquals(
            AndroidSecureRandomProvider::class.java.name,
            JSch.getConfig("random")
        )
        assertTrue(JschRuntime.isDirectProviderInstalled())
        assertTrue(messages.any { it.contains("algoritmos esenciales verificados") })
    }

    @Test
    fun everyRuntimeLoadedProviderExistsBeforeOpeningTheNetwork() {
        JschRuntime.verifyEssentialProviders()

        JschRuntime.getEssentialProviderKeys().forEach { algorithm ->
            val className = JSch.getConfig(algorithm)
            assertTrue(className.isNotBlank())
            assertEquals(className, Class.forName(className).name)
        }
    }

    @Test
    fun providerFillsRequestedRange() {
        val provider = AndroidSecureRandomProvider()
        val bytes = ByteArray(64)

        provider.fill(bytes, 8, 40)

        assertTrue(bytes.copyOfRange(8, 48).any { it.toInt() != 0 })
        assertTrue(bytes.copyOfRange(0, 8).all { it.toInt() == 0 })
        assertTrue(bytes.copyOfRange(48, 64).all { it.toInt() == 0 })
    }
}
