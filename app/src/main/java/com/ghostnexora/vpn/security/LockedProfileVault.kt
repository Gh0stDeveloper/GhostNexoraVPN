package com.ghostnexora.vpn.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ghostnexora.vpn.data.model.ProxyConfig
import com.ghostnexora.vpn.data.model.TlsVerificationMode
import com.ghostnexora.vpn.data.model.VpnProfile
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sella la configuración completa de un perfil bloqueado antes de guardarlo.
 *
 * La fila Room conserva únicamente una vista opaca. El servicio VPN abre el
 * sobre bajo demanda con una clave AES no exportable de Android Keystore.
 */
@Singleton
class LockedProfileVault @Inject constructor() {
    private val gson = Gson()
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    fun seal(
        profile: VpnProfile,
        packageId: String = profile.lockedPackageId
    ): VpnProfile {
        require(packageId.isNotBlank()) { "El paquete bloqueado no tiene identidad" }
        val normalized = profile.copy(
            isLocked = true,
            sealedConfig = "",
            lockedPackageId = packageId,
            protectionVersion = PROTECTION_VERSION,
            noteHtml = HtmlNoteSanitizer.sanitize(profile.displayNoteHtml)
        )
        val plaintext = gson.toJson(normalized).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        return try {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(aad(normalized.id, packageId))
            val encrypted = cipher.doFinal(plaintext)
            val packed = ByteArray(2 + cipher.iv.size + encrypted.size)
            packed[0] = LOCAL_FORMAT_VERSION
            packed[1] = cipher.iv.size.toByte()
            cipher.iv.copyInto(packed, destinationOffset = 2)
            encrypted.copyInto(packed, destinationOffset = 2 + cipher.iv.size)
            normalized.toOpaqueRecord(
                PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
            )
        } finally {
            NativeGuard.wipe(plaintext)
        }
    }

    fun open(record: VpnProfile): VpnProfile {
        require(record.isLocked && isSealed(record)) {
            "El perfil bloqueado no contiene un sobre local válido"
        }
        NativeGuard.requireProtectedRuntime()
        val packed = Base64.decode(record.sealedConfig.removePrefix(PREFIX), Base64.NO_WRAP)
        require(packed.size > 2 + 12 + 16) { "Sobre local truncado" }
        require(packed[0] == LOCAL_FORMAT_VERSION) { "Versión de sobre local no soportada" }
        val nonceLength = packed[1].toInt() and 0xff
        require(nonceLength in 12..16 && packed.size > 2 + nonceLength + 16) {
            "Nonce del sobre local inválido"
        }
        val nonce = packed.copyOfRange(2, 2 + nonceLength)
        val ciphertext = packed.copyOfRange(2 + nonceLength, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, nonce)
        )
        cipher.updateAAD(aad(record.id, record.lockedPackageId))
        val plaintext = cipher.doFinal(ciphertext)
        return try {
            val decoded = gson.fromJson(
                plaintext.toString(Charsets.UTF_8),
                VpnProfile::class.java
            )
            require(decoded.id == record.id) { "Identidad local del perfil alterada" }
            require(decoded.lockedPackageId == record.lockedPackageId) {
                "Identidad del paquete bloqueado alterada"
            }
            decoded.copy(
                name = record.name,
                noteHtml = record.noteHtml,
                enabled = record.enabled,
                lastUsed = record.lastUsed,
                createdAt = record.createdAt,
                isFavorite = record.isFavorite,
                isLocked = true,
                sealedConfig = "",
                protectionVersion = PROTECTION_VERSION
            )
        } finally {
            NativeGuard.wipe(plaintext, nonce, ciphertext, packed)
        }
    }

    fun visible(record: VpnProfile): VpnProfile =
        if (!record.isLocked) {
            record
        } else {
            record.toOpaqueRecord(sealed = "")
        }

    fun isSealed(profile: VpnProfile): Boolean =
        profile.sealedConfig.startsWith(PREFIX)

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
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

    private fun VpnProfile.toOpaqueRecord(sealed: String): VpnProfile = copy(
        host = "",
        port = 0,
        username = "",
        password = "",
        method = "locked",
        connectionMode = "locked",
        sslEnabled = false,
        sni = "",
        tlsVerificationMode = TlsVerificationMode.STRICT.id,
        payload = "",
        proxy = ProxyConfig(),
        tagsRaw = "",
        notes = "",
        isLocked = true,
        sealedConfig = sealed,
        protectionVersion = PROTECTION_VERSION
    )

    private fun aad(profileId: String, packageId: String): ByteArray =
        "GhostNexoraVPN|locked-profile|v1|$profileId|$packageId"
            .toByteArray(Charsets.UTF_8)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ghostnexora.locked.profile.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "gnxlock1:"
        const val PROTECTION_VERSION = 3
        const val LOCAL_FORMAT_VERSION: Byte = 1
    }
}
