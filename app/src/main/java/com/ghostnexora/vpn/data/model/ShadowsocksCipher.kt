package com.ghostnexora.vpn.data.model

import java.util.Base64

enum class ShadowsocksCipher(val id: String, private val keyBytes: Int? = null) {
    AES_128_GCM("aes-128-gcm"),
    AES_256_GCM("aes-256-gcm"),
    CHACHA20("chacha20-ietf-poly1305"),
    XCHACHA20("xchacha20-ietf-poly1305"),
    AES_128_GCM_2022("2022-blake3-aes-128-gcm", 16),
    AES_256_GCM_2022("2022-blake3-aes-256-gcm", 32),
    CHACHA20_2022("2022-blake3-chacha20-poly1305", 32);

    fun accepts(password: String): Boolean {
        if (password.isBlank()) return false
        val size = keyBytes ?: return true
        val keys = password.split(':')
        if (this == CHACHA20_2022 && keys.size != 1) return false
        return keys.all { key ->
            runCatching { Base64.getDecoder().decode(key).size == size }.getOrDefault(false)
        }
    }

    companion object {
        fun fromId(value: String): ShadowsocksCipher? = when (value.lowercase()) {
            "chacha20-poly1305" -> CHACHA20
            "xchacha20-poly1305" -> XCHACHA20
            else -> entries.firstOrNull { it.id.equals(value, ignoreCase = true) }
        }
    }
}
