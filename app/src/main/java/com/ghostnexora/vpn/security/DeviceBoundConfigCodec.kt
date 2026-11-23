package com.ghostnexora.vpn.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Local backup envelope whose DEK can be unwrapped only by this Android installation. */
@Singleton
class DeviceBoundConfigCodec @Inject constructor() {
    private val keyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }
    private val random = SecureRandom()

    fun encrypt(plaintext: ByteArray): ByteArray {
        val dataKey = ByteArray(KEY_BYTES).also(random::nextBytes)
        return try {
            val wrap = Cipher.getInstance(TRANSFORMATION)
            wrap.init(Cipher.ENCRYPT_MODE, hardwareKey(create = true))
            check(wrap.iv.size == NONCE_BYTES)
            val contentNonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val header = MAGIC + wrap.iv + contentNonce
            wrap.updateAAD(header)
            val wrapped = wrap.doFinal(dataKey)
            val content = Cipher.getInstance(TRANSFORMATION)
            content.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(128, contentNonce))
            content.updateAAD(header + wrapped)
            header + wrapped + content.doFinal(plaintext)
        } finally {
            NativeGuard.wipe(dataKey)
        }
    }

    fun decrypt(file: ByteArray): ByteArray {
        require(matches(file) && file.size > PAYLOAD_OFFSET + TAG_BYTES) { "Invalid device backup" }
        val header = file.copyOfRange(0, HEADER_BYTES)
        val wrap = Cipher.getInstance(TRANSFORMATION)
        wrap.init(Cipher.DECRYPT_MODE, hardwareKey(create = false), GCMParameterSpec(128, header, MAGIC.size, NONCE_BYTES))
        wrap.updateAAD(header)
        val dataKey = wrap.doFinal(file, HEADER_BYTES, KEY_BYTES + TAG_BYTES)
        return try {
            val content = Cipher.getInstance(TRANSFORMATION)
            content.init(Cipher.DECRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(128, header, MAGIC.size + NONCE_BYTES, NONCE_BYTES))
            content.updateAAD(file, 0, PAYLOAD_OFFSET)
            content.doFinal(file, PAYLOAD_OFFSET, file.size - PAYLOAD_OFFSET)
        } finally {
            NativeGuard.wipe(dataKey)
        }
    }

    @Synchronized
    private fun hardwareKey(create: Boolean): SecretKey {
        val existing = keyStore.getKey(ALIAS, null) as? SecretKey
        if (existing != null) {
            requireHardware(existing)
            return existing
        }
        check(create) { "This backup requires the original Android installation" }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        val key = generator.generateKey()
        try {
            requireHardware(key)
            return key
        } catch (error: Exception) {
            keyStore.deleteEntry(ALIAS)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun requireHardware(key: SecretKey) {
        val info = SecretKeyFactory.getInstance("AES", PROVIDER).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        val hardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } else {
            info.isInsideSecureHardware
        }
        check(hardware) { "Android Keystore hardware is required for this backup" }
    }

    companion object {
        private const val ALIAS = "ghostnexora-gnx-device-v1"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val MAGIC = byteArrayOf(0x47, 0x4e, 0x48, 0x31) // GNH1
        private const val NONCE_BYTES = 12
        private const val KEY_BYTES = 32
        private const val TAG_BYTES = 16
        private const val HEADER_BYTES = 4 + 2 * NONCE_BYTES
        private const val PAYLOAD_OFFSET = HEADER_BYTES + KEY_BYTES + TAG_BYTES

        fun matches(bytes: ByteArray) = bytes.size >= MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }
    }
}
