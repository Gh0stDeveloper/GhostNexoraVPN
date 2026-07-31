package com.ghostnexora.vpn.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class Gnx3ProtectionMode(val id: Byte) {
    PASSWORD(1),
    APP_MANAGED(2);

    companion object {
        fun fromId(id: Byte): Gnx3ProtectionMode =
            entries.firstOrNull { it.id == id }
                ?: throw Gnx3ConfigException("Modo de protección GNX3 no soportado")
    }
}

sealed class Gnx3ProtectionKey {
    data class Password(val value: CharArray) : Gnx3ProtectionKey()
    data class AppManaged(val value: ByteArray) : Gnx3ProtectionKey()

    val mode: Gnx3ProtectionMode
        get() = when (this) {
            is Password -> Gnx3ProtectionMode.PASSWORD
            is AppManaged -> Gnx3ProtectionMode.APP_MANAGED
        }
}

data class Gnx3EnvelopeInfo(
    val locked: Boolean,
    val protectionMode: Gnx3ProtectionMode
)

data class Gnx3DecodedConfig(
    val json: String,
    val info: Gnx3EnvelopeInfo
)

/**
 * Contenedor individual GNX3.
 *
 * El contenido usa una clave de datos aleatoria y AES-256-GCM. Esa clave se
 * envuelve con otra operación AES-GCM y el contenedor completo se autentica
 * además con HMAC-SHA256. La contraseña usa PBKDF2-HMAC-SHA256; el modo
 * administrado por la app usa expansión HMAC con salt aleatorio.
 *
 * Los salts y nonces se almacenan junto al ciphertext porque no son secretos.
 * Nunca se reutiliza un IV y no existe un IV fijo dentro del APK.
 */
object Gnx3ConfigCodec {
    private val MAGIC = byteArrayOf(
        'G'.code.toByte(),
        'N'.code.toByte(),
        'X'.code.toByte(),
        '3'.code.toByte()
    )
    private const val VERSION: Byte = 3
    private const val FLAG_LOCKED = 0x01
    private const val PASSWORD_ITERATIONS = 420_000
    private const val SALT_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val DATA_KEY_BYTES = 32
    private const val DERIVED_BYTES = 64
    private const val MAC_BYTES = 32
    private const val HEADER_BYTES = 20
    private const val MAX_ENCRYPTED_BYTES = 16 * 1024 * 1024
    private const val MAX_PLAINTEXT_BYTES = 4 * 1024 * 1024
    private const val TEXT_PREFIX = "GNX3:"
    private const val MAX_TEXT_ENVELOPE_CHARS = 23 * 1024 * 1024
    private val random = SecureRandom()

    fun encrypt(
        json: String,
        key: Gnx3ProtectionKey,
        locked: Boolean
    ): ByteArray {
        validateKey(key)
        val flags = if (locked) FLAG_LOCKED.toByte() else 0
        val salt = randomBytes(SALT_BYTES)
        val wrapNonce = randomBytes(NONCE_BYTES)
        val dataNonce = randomBytes(NONCE_BYTES)
        val dataKey = randomBytes(DATA_KEY_BYTES)
        val plaintext = json.toByteArray(Charsets.UTF_8)
        val compressed = gzip(plaintext)
        val derived = derive(key, salt)
        val wrappingKey = derived.copyOfRange(0, 32)
        val macKey = derived.copyOfRange(32, 64)

        return try {
            val wrappedKey = aesGcmEncrypt(
                key = wrappingKey,
                nonce = wrapNonce,
                plaintext = dataKey,
                aad = aad("key", flags, key.mode)
            )
            val ciphertext = aesGcmEncrypt(
                key = dataKey,
                nonce = dataNonce,
                plaintext = compressed,
                aad = aad("payload", flags, key.mode)
            )
            val iterations = if (key.mode == Gnx3ProtectionMode.PASSWORD) {
                PASSWORD_ITERATIONS
            } else {
                0
            }
            val header = ByteBuffer.allocate(HEADER_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .put(MAGIC)
                .put(VERSION)
                .put(flags)
                .put(key.mode.id)
                .putInt(iterations)
                .put(salt.size.toByte())
                .put(wrapNonce.size.toByte())
                .put(dataNonce.size.toByte())
                .putShort(wrappedKey.size.toShort())
                .putInt(ciphertext.size)
                .array()
            val body = ByteArrayOutputStream(
                header.size + salt.size + wrapNonce.size + dataNonce.size +
                    wrappedKey.size + ciphertext.size
            ).apply {
                write(header)
                write(salt)
                write(wrapNonce)
                write(dataNonce)
                write(wrappedKey)
                write(ciphertext)
            }.toByteArray()
            body + hmacSha256(macKey, body)
        } finally {
            NativeGuard.wipe(
                plaintext,
                compressed,
                dataKey,
                derived,
                wrappingKey,
                macKey
            )
        }
    }

    fun decrypt(container: ByteArray, key: Gnx3ProtectionKey): Gnx3DecodedConfig {
        validateKey(key)
        val parsed = parse(container)
        if (parsed.info.protectionMode != key.mode) {
            throw Gnx3ConfigException(
                "La protección indicada no corresponde al archivo GNX3"
            )
        }

        val derived = derive(key, parsed.salt, parsed.iterations)
        val wrappingKey = derived.copyOfRange(0, 32)
        val macKey = derived.copyOfRange(32, 64)
        var dataKey: ByteArray? = null
        var compressed: ByteArray? = null
        var plaintext: ByteArray? = null

        try {
            val expectedMac = hmacSha256(macKey, parsed.authenticatedBody)
            if (!MessageDigest.isEqual(expectedMac, parsed.mac)) {
                throw Gnx3ConfigException("Clave incorrecta o archivo GNX3 alterado")
            }
            dataKey = aesGcmDecrypt(
                key = wrappingKey,
                nonce = parsed.wrapNonce,
                ciphertext = parsed.wrappedKey,
                aad = aad("key", parsed.flags, parsed.info.protectionMode)
            )
            require(dataKey.size == DATA_KEY_BYTES) { "Clave de datos GNX3 inválida" }
            compressed = aesGcmDecrypt(
                key = dataKey,
                nonce = parsed.dataNonce,
                ciphertext = parsed.ciphertext,
                aad = aad("payload", parsed.flags, parsed.info.protectionMode)
            )
            plaintext = gunzipLimited(compressed, MAX_PLAINTEXT_BYTES)
            return Gnx3DecodedConfig(
                json = plaintext.toString(Charsets.UTF_8),
                info = parsed.info
            )
        } catch (error: AEADBadTagException) {
            throw Gnx3ConfigException("Clave incorrecta o archivo GNX3 alterado", error)
        } catch (error: Gnx3ConfigException) {
            throw error
        } catch (error: Throwable) {
            throw Gnx3ConfigException(
                error.message ?: "No se pudo abrir la configuración GNX3",
                error
            )
        } finally {
            NativeGuard.wipe(
                dataKey,
                compressed,
                plaintext,
                derived,
                wrappingKey,
                macKey
            )
        }
    }

    fun inspect(container: ByteArray): Gnx3EnvelopeInfo = parse(container).info

    fun isEncrypted(container: ByteArray): Boolean =
        container.size >= MAGIC.size &&
            container.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun isTextEnvelope(rawText: String): Boolean =
        rawText.trimStart().startsWith(TEXT_PREFIX)

    fun encodeTextEnvelope(container: ByteArray): String =
        TEXT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(container)

    fun decodeTextEnvelope(rawText: String): ByteArray {
        if (rawText.length > MAX_TEXT_ENVELOPE_CHARS) {
            throw Gnx3ConfigException("Contenedor GNX3 en texto demasiado grande")
        }
        val encoded = rawText.trim().removePrefix(TEXT_PREFIX)
        return runCatching { Base64.getUrlDecoder().decode(encoded) }
            .getOrElse { throw Gnx3ConfigException("Contenedor GNX3 en texto inválido", it) }
    }

    fun validatePassword(password: CharArray): Boolean = password.size >= 10

    private fun parse(container: ByteArray): ParsedContainer {
        try {
            require(container.size in (HEADER_BYTES + MAC_BYTES + 1)..MAX_ENCRYPTED_BYTES) {
                "Archivo GNX3 inválido o demasiado grande"
            }
            val buffer = ByteBuffer.wrap(container).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(MAGIC.size).also(buffer::get)
            require(magic.contentEquals(MAGIC)) { "Formato GNX3 no reconocido" }
            require(buffer.get() == VERSION) { "Versión GNX3 no soportada" }

            val flags = buffer.get()
            require((flags.toInt() and FLAG_LOCKED.inv()) == 0) {
                "Banderas GNX3 desconocidas"
            }
            val mode = Gnx3ProtectionMode.fromId(buffer.get())
            val iterations = buffer.int
            when (mode) {
                Gnx3ProtectionMode.PASSWORD ->
                    require(iterations in 100_000..2_000_000) { "Parámetros KDF GNX3 inválidos" }
                Gnx3ProtectionMode.APP_MANAGED ->
                    require(iterations == 0) { "Parámetros de protección GNX3 inválidos" }
            }

            val saltLength = buffer.get().toInt() and 0xff
            val wrapNonceLength = buffer.get().toInt() and 0xff
            val dataNonceLength = buffer.get().toInt() and 0xff
            val wrappedKeyLength = buffer.short.toInt() and 0xffff
            val ciphertextLength = buffer.int
            require(saltLength in 16..64) { "Salt GNX3 inválido" }
            require(wrapNonceLength in 12..16 && dataNonceLength in 12..16) {
                "Nonce GNX3 inválido"
            }
            require(wrappedKeyLength in 32..128) { "Clave envuelta GNX3 inválida" }
            require(ciphertextLength in 16..MAX_ENCRYPTED_BYTES) {
                "Contenido GNX3 inválido"
            }

            val required = HEADER_BYTES + saltLength + wrapNonceLength +
                dataNonceLength + wrappedKeyLength + ciphertextLength + MAC_BYTES
            require(required == container.size) { "Longitud del contenedor GNX3 inválida" }

            val salt = ByteArray(saltLength).also(buffer::get)
            val wrapNonce = ByteArray(wrapNonceLength).also(buffer::get)
            val dataNonce = ByteArray(dataNonceLength).also(buffer::get)
            val wrappedKey = ByteArray(wrappedKeyLength).also(buffer::get)
            val ciphertext = ByteArray(ciphertextLength).also(buffer::get)
            val mac = ByteArray(MAC_BYTES).also(buffer::get)
            return ParsedContainer(
                flags = flags,
                info = Gnx3EnvelopeInfo(
                    locked = flags.toInt() and FLAG_LOCKED != 0,
                    protectionMode = mode
                ),
                iterations = iterations,
                salt = salt,
                wrapNonce = wrapNonce,
                dataNonce = dataNonce,
                wrappedKey = wrappedKey,
                ciphertext = ciphertext,
                mac = mac,
                authenticatedBody = container.copyOfRange(0, container.size - MAC_BYTES)
            )
        } catch (error: Gnx3ConfigException) {
            throw error
        } catch (error: Throwable) {
            throw Gnx3ConfigException(error.message ?: "Contenedor GNX3 inválido", error)
        }
    }

    private fun validateKey(key: Gnx3ProtectionKey) {
        when (key) {
            is Gnx3ProtectionKey.Password -> require(validatePassword(key.value)) {
                "La contraseña GNX3 debe tener al menos 10 caracteres"
            }
            is Gnx3ProtectionKey.AppManaged -> require(key.value.size >= 32) {
                "Material de protección administrada inválido"
            }
        }
    }

    private fun derive(
        key: Gnx3ProtectionKey,
        salt: ByteArray,
        iterations: Int = if (key.mode == Gnx3ProtectionMode.PASSWORD) {
            PASSWORD_ITERATIONS
        } else {
            0
        }
    ): ByteArray = when (key) {
        is Gnx3ProtectionKey.Password -> {
            val spec = PBEKeySpec(key.value, salt, iterations, DERIVED_BYTES * 8)
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .encoded
            } finally {
                spec.clearPassword()
            }
        }
        is Gnx3ProtectionKey.AppManaged -> hkdfSha256(
            inputKey = key.value,
            salt = salt,
            info = "GhostNexoraVPN|GNX3|app-managed".toByteArray(Charsets.UTF_8),
            outputSize = DERIVED_BYTES
        )
    }

    private fun hkdfSha256(
        inputKey: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputSize: Int
    ): ByteArray {
        val prk = hmacSha256(salt, inputKey)
        val output = ByteArray(outputSize)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        try {
            while (offset < outputSize) {
                val blockInput = previous + info + byteArrayOf(counter.toByte())
                val block = hmacSha256(prk, blockInput)
                NativeGuard.wipe(previous, blockInput)
                previous = block
                val count = minOf(block.size, outputSize - offset)
                block.copyInto(output, offset, 0, count)
                offset += count
                counter += 1
            }
            return output
        } finally {
            NativeGuard.wipe(prk, previous)
        }
    }

    private fun aesGcmEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, nonce)
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    private fun aesGcmDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, nonce)
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun aad(
        scope: String,
        flags: Byte,
        mode: Gnx3ProtectionMode
    ): ByteArray = (
        "GhostNexoraVPN|GNX3|$scope|v3|${flags.toInt() and 0xff}|${mode.id}"
        ).toByteArray(Charsets.UTF_8)

    private fun gzip(input: ByteArray): ByteArray =
        ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { gzip -> gzip.write(input) }
            output.toByteArray()
        }

    private fun gunzipLimited(input: ByteArray, maxBytes: Int): ByteArray =
        GZIPInputStream(ByteArrayInputStream(input)).use { gzip ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = gzip.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw Gnx3ConfigException(
                        "La configuración GNX3 excede el límite permitido"
                    )
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also(random::nextBytes)

    private data class ParsedContainer(
        val flags: Byte,
        val info: Gnx3EnvelopeInfo,
        val iterations: Int,
        val salt: ByteArray,
        val wrapNonce: ByteArray,
        val dataNonce: ByteArray,
        val wrappedKey: ByteArray,
        val ciphertext: ByteArray,
        val mac: ByteArray,
        val authenticatedBody: ByteArray
    )
}

class Gnx3ConfigException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
