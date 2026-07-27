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

/**
 * Formato cifrado portable de Ghost Nexora VPN.
 *
 * Capas:
 * 1. JSON -> GZIP.
 * 2. Clave de datos aleatoria de 256 bits.
 * 3. AES-256-GCM para el contenido.
 * 4. PBKDF2-HMAC-SHA256 para derivar KEK + clave MAC desde la contraseña.
 * 5. AES-256-GCM separado para envolver la clave de datos.
 * 6. HMAC-SHA256 sobre todo el contenedor antes de intentar descifrarlo.
 *
 * Los nonces/salt no son secretos criptográficos y se almacenan en un formato
 * binario compacto, sin campos JSON legibles. Ninguna contraseña o clave maestra
 * se almacena dentro del archivo o del APK.
 */
object SecureConfigCodec {
    private val MAGIC = byteArrayOf('G'.code.toByte(), 'N'.code.toByte(), 'X'.code.toByte(), '2'.code.toByte())
    private const val VERSION: Byte = 2
    private const val ITERATIONS = 310_000
    private const val SALT_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val DATA_KEY_BYTES = 32
    private const val MAC_BYTES = 32
    private const val DERIVED_BYTES = 64
    private const val MAX_ENCRYPTED_BYTES = 32 * 1024 * 1024
    private const val MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024
    private const val HEADER_BYTES = 4 + 1 + 4 + 1 + 1 + 1 + 2 + 4
    private const val TEXT_PREFIX = "GNX2:"

    private val secureRandom = SecureRandom()

    fun encrypt(json: String, passphrase: CharArray): ByteArray {
        requireStrongPassphrase(passphrase)

        val salt = randomBytes(SALT_BYTES)
        val wrapNonce = randomBytes(NONCE_BYTES)
        val dataNonce = randomBytes(NONCE_BYTES)
        val dataKey = randomBytes(DATA_KEY_BYTES)
        val compressed = gzip(json.toByteArray(Charsets.UTF_8))
        val derived = derive(passphrase, salt)
        val wrappingKey = derived.copyOfRange(0, 32)
        val macKey = derived.copyOfRange(32, 64)

        return try {
            val wrappedKey = aesGcmEncrypt(
                key = wrappingKey,
                nonce = wrapNonce,
                plaintext = dataKey,
                aad = aad("key")
            )
            val ciphertext = aesGcmEncrypt(
                key = dataKey,
                nonce = dataNonce,
                plaintext = compressed,
                aad = aad("payload")
            )

            val header = ByteBuffer.allocate(HEADER_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .put(MAGIC)
                .put(VERSION)
                .putInt(ITERATIONS)
                .put(salt.size.toByte())
                .put(wrapNonce.size.toByte())
                .put(dataNonce.size.toByte())
                .putShort(wrappedKey.size.toShort())
                .putInt(ciphertext.size)
                .array()

            val body = ByteArrayOutputStream(header.size + salt.size + wrapNonce.size + dataNonce.size + wrappedKey.size + ciphertext.size)
                .apply {
                    write(header)
                    write(salt)
                    write(wrapNonce)
                    write(dataNonce)
                    write(wrappedKey)
                    write(ciphertext)
                }
                .toByteArray()

            val mac = hmacSha256(macKey, body)
            body + mac
        } finally {
            NativeGuard.wipe(dataKey, compressed, derived, wrappingKey, macKey)
        }
    }

    fun decrypt(container: ByteArray, passphrase: CharArray): String {
        require(container.size in (HEADER_BYTES + MAC_BYTES + 1)..MAX_ENCRYPTED_BYTES) {
            "Archivo cifrado inválido o demasiado grande"
        }
        requireStrongPassphrase(passphrase)

        val parsed = parse(container)
        val derived = derive(passphrase, parsed.salt, parsed.iterations)
        val wrappingKey = derived.copyOfRange(0, 32)
        val macKey = derived.copyOfRange(32, 64)
        var dataKey: ByteArray? = null
        var compressed: ByteArray? = null

        try {
            val expectedMac = hmacSha256(macKey, parsed.authenticatedBody)
            if (!MessageDigest.isEqual(expectedMac, parsed.mac)) {
                throw SecureConfigException("Contraseña incorrecta o archivo alterado")
            }

            dataKey = aesGcmDecrypt(
                key = wrappingKey,
                nonce = parsed.wrapNonce,
                ciphertext = parsed.wrappedKey,
                aad = aad("key")
            )
            require(dataKey.size == DATA_KEY_BYTES) { "Clave de datos inválida" }

            compressed = aesGcmDecrypt(
                key = dataKey,
                nonce = parsed.dataNonce,
                ciphertext = parsed.ciphertext,
                aad = aad("payload")
            )
            val plaintext = gunzipLimited(compressed, MAX_PLAINTEXT_BYTES)
            return plaintext.toString(Charsets.UTF_8)
        } catch (error: AEADBadTagException) {
            throw SecureConfigException("Contraseña incorrecta o archivo alterado", error)
        } catch (error: SecureConfigException) {
            throw error
        } catch (error: Throwable) {
            throw SecureConfigException(error.message ?: "No se pudo descifrar la configuración", error)
        } finally {
            NativeGuard.wipe(dataKey, compressed, derived, wrappingKey, macKey)
        }
    }

    fun isEncrypted(container: ByteArray): Boolean =
        container.size >= MAGIC.size && container.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun isTextEnvelope(rawText: String): Boolean = rawText.trimStart().startsWith(TEXT_PREFIX)

    fun encodeTextEnvelope(container: ByteArray): String =
        TEXT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(container)

    fun decodeTextEnvelope(rawText: String): ByteArray {
        val encoded = rawText.trim().removePrefix(TEXT_PREFIX)
        return runCatching { Base64.getUrlDecoder().decode(encoded) }
            .getOrElse { throw SecureConfigException("Contenedor GNX2 en texto inválido", it) }
    }

    fun validatePassphrase(passphrase: CharArray): Boolean = passphrase.size >= 10

    private fun requireStrongPassphrase(passphrase: CharArray) {
        require(validatePassphrase(passphrase)) {
            "La contraseña de protección debe tener al menos 10 caracteres"
        }
    }

    private fun parse(container: ByteArray): ParsedContainer {
        val buffer = ByteBuffer.wrap(container).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC)) { "Formato GNX2 no reconocido" }

        val version = buffer.get()
        require(version == VERSION) { "Versión de configuración cifrada no soportada: $version" }

        val iterations = buffer.int
        require(iterations in 100_000..2_000_000) { "Parámetros KDF inválidos" }

        val saltLength = buffer.get().toInt() and 0xff
        val wrapNonceLength = buffer.get().toInt() and 0xff
        val dataNonceLength = buffer.get().toInt() and 0xff
        val wrappedKeyLength = buffer.short.toInt() and 0xffff
        val ciphertextLength = buffer.int

        require(saltLength in 16..64) { "Salt inválido" }
        require(wrapNonceLength in 12..16 && dataNonceLength in 12..16) { "Nonce inválido" }
        require(wrappedKeyLength in 32..128) { "Clave envuelta inválida" }
        require(ciphertextLength in 16..MAX_ENCRYPTED_BYTES) { "Contenido cifrado inválido" }

        val required = HEADER_BYTES + saltLength + wrapNonceLength + dataNonceLength + wrappedKeyLength + ciphertextLength + MAC_BYTES
        require(required == container.size) { "Longitud del contenedor GNX2 inválida" }

        val salt = ByteArray(saltLength).also(buffer::get)
        val wrapNonce = ByteArray(wrapNonceLength).also(buffer::get)
        val dataNonce = ByteArray(dataNonceLength).also(buffer::get)
        val wrappedKey = ByteArray(wrappedKeyLength).also(buffer::get)
        val ciphertext = ByteArray(ciphertextLength).also(buffer::get)
        val mac = ByteArray(MAC_BYTES).also(buffer::get)
        val authenticatedBody = container.copyOfRange(0, container.size - MAC_BYTES)

        return ParsedContainer(
            iterations = iterations,
            salt = salt,
            wrapNonce = wrapNonce,
            dataNonce = dataNonce,
            wrappedKey = wrappedKey,
            ciphertext = ciphertext,
            mac = mac,
            authenticatedBody = authenticatedBody
        )
    }

    private fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, DERIVED_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    private fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun aad(scope: String): ByteArray =
        NativeGuard.domainSeparator() + "|$scope|$VERSION".toByteArray(Charsets.UTF_8)

    private fun gzip(input: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
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
                if (total > maxBytes) throw SecureConfigException("La configuración descifrada excede el límite permitido")
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    private data class ParsedContainer(
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

class SecureConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)
