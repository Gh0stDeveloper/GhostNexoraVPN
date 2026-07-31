package com.ghostnexora.vpn.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Gnx3ConfigCodecTest {
    private val password = "correct horse battery staple".toCharArray()
    private val appKey = ByteArray(32) { index -> (index * 7 + 3).toByte() }
    private val sample =
        """{"profile":{"host":"hidden.example","password":"secret"},"noteHtml":"<b>Hola</b>"}"""

    @Test
    fun passwordRoundTripPreservesLockedPolicy() {
        val encrypted = Gnx3ConfigCodec.encrypt(
            sample,
            Gnx3ProtectionKey.Password(password),
            locked = true
        )
        val info = Gnx3ConfigCodec.inspect(encrypted)
        assertTrue(info.locked)
        assertTrue(info.protectionMode == Gnx3ProtectionMode.PASSWORD)
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("hidden.example"))

        val decoded = Gnx3ConfigCodec.decrypt(
            encrypted,
            Gnx3ProtectionKey.Password(password)
        )
        assertTrue(decoded.json == sample)
        assertTrue(decoded.info.locked)
    }

    @Test
    fun appManagedRoundTripDoesNotRequirePassword() {
        val encrypted = Gnx3ConfigCodec.encrypt(
            sample,
            Gnx3ProtectionKey.AppManaged(appKey),
            locked = false
        )
        val decoded = Gnx3ConfigCodec.decrypt(
            encrypted,
            Gnx3ProtectionKey.AppManaged(appKey)
        )
        assertTrue(decoded.json == sample)
        assertFalse(decoded.info.locked)
        assertTrue(decoded.info.protectionMode == Gnx3ProtectionMode.APP_MANAGED)
    }

    @Test
    fun sameInputUsesFreshKeysAndNonces() {
        val first = Gnx3ConfigCodec.encrypt(
            sample,
            Gnx3ProtectionKey.AppManaged(appKey),
            locked = true
        )
        val second = Gnx3ConfigCodec.encrypt(
            sample,
            Gnx3ProtectionKey.AppManaged(appKey),
            locked = true
        )
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun tamperingIsRejectedBeforePlaintextIsReturned() {
        val encrypted = Gnx3ConfigCodec.encrypt(
            sample,
            Gnx3ProtectionKey.Password(password),
            locked = true
        )
        encrypted[encrypted.size / 2] =
            (encrypted[encrypted.size / 2].toInt() xor 0x01).toByte()
        try {
            Gnx3ConfigCodec.decrypt(
                encrypted,
                Gnx3ProtectionKey.Password(password)
            )
            fail("El archivo alterado debía rechazarse")
        } catch (expected: Gnx3ConfigException) {
            assertTrue(expected.message.orEmpty().isNotBlank())
        }
    }

    @Test
    fun wrongPasswordIsRejected() {
        val encrypted = Gnx3ConfigCodec.encrypt(
            sample,
            Gnx3ProtectionKey.Password(password),
            locked = true
        )
        try {
            Gnx3ConfigCodec.decrypt(
                encrypted,
                Gnx3ProtectionKey.Password("another secure password".toCharArray())
            )
            fail("La contraseña incorrecta debía rechazarse")
        } catch (expected: Gnx3ConfigException) {
            assertTrue(expected.message.orEmpty().contains("incorrecta", ignoreCase = true))
        }
    }

    @Test
    fun textEnvelopeRoundTripWorks() {
        val encrypted = Gnx3ConfigCodec.encrypt(
            sample,
            Gnx3ProtectionKey.AppManaged(appKey),
            locked = true
        )
        val text = Gnx3ConfigCodec.encodeTextEnvelope(encrypted)
        assertTrue(Gnx3ConfigCodec.isTextEnvelope(text))
        assertTrue(Gnx3ConfigCodec.decodeTextEnvelope(text).contentEquals(encrypted))
    }
}
