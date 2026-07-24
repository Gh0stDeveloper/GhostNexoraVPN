package com.ghostnexora.vpn.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SecureConfigCodecTest {
    private val password = "correct horse battery staple".toCharArray()
    private val sample = """{"profiles":[{"host":"vpn.example.com","password":"secret-value"}]}"""

    @Test
    fun roundTripRestoresOriginalPayload() {
        val encrypted = SecureConfigCodec.encrypt(sample, password)
        assertTrue(SecureConfigCodec.isEncrypted(encrypted))
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("secret-value"))
        val decrypted = SecureConfigCodec.decrypt(encrypted, password)
        assertTrue(decrypted == sample)
    }

    @Test
    fun samePayloadProducesDifferentCiphertext() {
        val first = SecureConfigCodec.encrypt(sample, password)
        val second = SecureConfigCodec.encrypt(sample, password)
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun tamperingIsRejected() {
        val encrypted = SecureConfigCodec.encrypt(sample, password)
        encrypted[encrypted.lastIndex / 2] = (encrypted[encrypted.lastIndex / 2].toInt() xor 0x01).toByte()
        try {
            SecureConfigCodec.decrypt(encrypted, password)
            fail("El contenedor alterado debía ser rechazado")
        } catch (expected: SecureConfigException) {
            assertTrue(expected.message.orEmpty().isNotBlank())
        }
    }

    @Test
    fun wrongPasswordIsRejected() {
        val encrypted = SecureConfigCodec.encrypt(sample, password)
        try {
            SecureConfigCodec.decrypt(encrypted, "another secure password".toCharArray())
            fail("La contraseña incorrecta debía ser rechazada")
        } catch (expected: SecureConfigException) {
            assertTrue(expected.message.orEmpty().contains("incorrecta", ignoreCase = true))
        }
    }

    @Test
    fun textEnvelopeRoundTripWorks() {
        val encrypted = SecureConfigCodec.encrypt(sample, password)
        val envelope = SecureConfigCodec.encodeTextEnvelope(encrypted)
        assertTrue(SecureConfigCodec.isTextEnvelope(envelope))
        val restored = SecureConfigCodec.decodeTextEnvelope(envelope)
        assertTrue(restored.contentEquals(encrypted))
    }
}
