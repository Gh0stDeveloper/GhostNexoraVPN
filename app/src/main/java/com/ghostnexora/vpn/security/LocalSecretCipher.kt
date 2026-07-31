package com.ghostnexora.vpn.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ghostnexora.vpn.data.model.VpnProfile
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifra los campos sensibles que se persisten en Room.
 *
 * La clave AES es no exportable y vive en Android Keystore. Cada campo usa un
 * nonce aleatorio generado por Cipher y AAD ligado al ID del perfil/campo para
 * impedir intercambiar ciphertexts entre columnas o perfiles.
 */
@Singleton
class LocalSecretCipher @Inject constructor() {
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    fun protect(profile: VpnProfile): VpnProfile {
        if (profile.isLocked) return profile
        return profile.copy(
            username = encryptField(profile.id, "username", profile.username),
            password = encryptField(profile.id, "password", profile.password),
            payload = encryptField(profile.id, "payload", profile.payload)
        )
    }

    fun reveal(profile: VpnProfile): VpnProfile {
        if (profile.isLocked) return profile
        return profile.copy(
            username = decryptField(profile.id, "username", profile.username),
            password = decryptField(profile.id, "password", profile.password),
            payload = decryptField(profile.id, "payload", profile.payload)
        )
    }

    fun isProtected(profile: VpnProfile): Boolean =
        profile.isLocked ||
        listOf(profile.username, profile.password, profile.payload)
            .filter(String::isNotBlank)
            .all { it.startsWith(PREFIX) }

    private fun encryptField(profileId: String, field: String, value: String): String {
        if (value.isBlank() || value.startsWith(PREFIX)) return value

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad(profileId, field))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        val packed = ByteArray(1 + cipher.iv.size + encrypted.size)
        packed[0] = cipher.iv.size.toByte()
        cipher.iv.copyInto(packed, destinationOffset = 1)
        encrypted.copyInto(packed, destinationOffset = 1 + cipher.iv.size)
        return PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decryptField(profileId: String, field: String, value: String): String {
        if (value.isBlank() || !value.startsWith(PREFIX)) return value

        return runCatching {
            val packed = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            require(packed.size > 1 + 12 + 16) { "Secreto local truncado" }
            val nonceLength = packed[0].toInt() and 0xff
            require(nonceLength in 12..16 && packed.size > 1 + nonceLength) { "Nonce local inválido" }

            val nonce = packed.copyOfRange(1, 1 + nonceLength)
            val encrypted = packed.copyOfRange(1 + nonceLength, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad(profileId, field))
            val plaintext = cipher.doFinal(encrypted)
            try {
                plaintext.toString(Charsets.UTF_8)
            } finally {
                NativeGuard.wipe(plaintext)
            }
        }.getOrDefault("")
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun aad(profileId: String, field: String): ByteArray =
        "GhostNexoraVPN|profile-secret|$profileId|$field".toByteArray(Charsets.UTF_8)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ghostnexora.profile.secrets.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "gnxsec1:"
    }
}
