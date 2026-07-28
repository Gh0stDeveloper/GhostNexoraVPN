package com.ghostnexora.vpn.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ghostnexora.vpn.BuildConfig
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.ProxyConfig
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.security.NativeGuard
import com.ghostnexora.vpn.security.SecureConfigCodec
import com.ghostnexora.vpn.security.SecureConfigException
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson: Gson = GsonBuilder().serializeNulls().create()

    fun importFromUri(uri: Uri, passphrase: CharArray? = null): ImportResult {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytesLimited(MAX_IMPORT_BYTES)
            } ?: return ImportResult.Error("No se pudo abrir el archivo")
            parseImportBytes(bytes, passphrase)
        } catch (error: Throwable) {
            ImportResult.Error("Error al leer el archivo: ${safeError(error)}")
        }
    }

    fun importFromString(rawText: String, passphrase: CharArray? = null): ImportResult {
        if (rawText.isBlank()) return ImportResult.Error("El contenido está vacío")
        return if (SecureConfigCodec.isTextEnvelope(rawText)) {
            val password = passphrase ?: return ImportResult.PasswordRequired("La configuración GNX2 requiere contraseña")
            runCatching { SecureConfigCodec.decodeTextEnvelope(rawText) }
                .fold(
                    onSuccess = { parseEncrypted(it, password) },
                    onFailure = { ImportResult.Error(safeError(it)) }
                )
        } else {
            parseImportText(rawText)
        }
    }

    fun exportToDownloads(
        profiles: List<VpnProfile>,
        passphrase: CharArray,
        fileName: String = defaultExportFileName()
    ): Uri? {
        return try {
            val encrypted = encryptProfiles(profiles, passphrase)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, ensureGnxExtension(fileName))
                    put(MediaStore.MediaColumns.MIME_TYPE, MIME_GNX)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + File.separator + "GhostNexoraVPN"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = context.contentResolver.insert(collection, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(encrypted)
                    output.flush()
                } ?: return null
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(dir, "GhostNexoraVPN").apply { mkdirs() }
                val outputFile = File(targetDir, ensureGnxExtension(fileName))
                outputFile.writeBytes(encrypted)
                Uri.fromFile(outputFile)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun exportToUri(uri: Uri, profiles: List<VpnProfile>, passphrase: CharArray): Boolean {
        return try {
            val encrypted = encryptProfiles(profiles, passphrase)
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(encrypted)
                output.flush()
            } ?: return false
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun exportToTextEnvelope(profiles: List<VpnProfile>, passphrase: CharArray): String =
        SecureConfigCodec.encodeTextEnvelope(encryptProfiles(profiles, passphrase))

    fun validatePassphrase(passphrase: CharArray): ValidationResult =
        if (SecureConfigCodec.validatePassphrase(passphrase)) {
            ValidationResult(true, "Contraseña de protección válida", 0)
        } else {
            ValidationResult(false, "Usa al menos 10 caracteres", 0)
        }

    private fun parseImportBytes(bytes: ByteArray, passphrase: CharArray?): ImportResult {
        if (bytes.isEmpty()) return ImportResult.Error("El archivo está vacío")
        return if (SecureConfigCodec.isEncrypted(bytes)) {
            val password = passphrase ?: return ImportResult.PasswordRequired("Este archivo .gnx requiere contraseña")
            parseEncrypted(bytes, password)
        } else {
            parseImportText(bytes.toString(Charsets.UTF_8))
        }
    }

    private fun parseEncrypted(bytes: ByteArray, passphrase: CharArray): ImportResult {
        return try {
            val json = SecureConfigCodec.decrypt(bytes, passphrase)
            parseJson(json)?.let { result ->
                when (result) {
                    is ImportResult.Success -> result.copy(sourceName = "Ghost Nexora cifrado GNX2")
                    else -> result
                }
            } ?: ImportResult.Error("El contenido descifrado no contiene perfiles válidos")
        } catch (error: SecureConfigException) {
            ImportResult.Error(error.message ?: "No se pudo descifrar la configuración")
        } catch (error: Throwable) {
            ImportResult.Error(safeError(error))
        }
    }

    private fun parseImportText(rawText: String): ImportResult {
        if (rawText.isBlank()) return ImportResult.Error("El archivo está vacío")
        val trimmed = rawText.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val xrayProfiles = ProtocolLinkParser.parseXrayJson(trimmed)
            if (xrayProfiles.isNotEmpty()) {
                return ImportResult.Success(xrayProfiles, "Configuración JSON Xray")
            }
            parseJson(trimmed)?.let { return it }
        }

        val protocolProfiles = ProtocolLinkParser.parseText(rawText)
        if (protocolProfiles.isNotEmpty()) {
            val source = when {
                rawText.contains("vmess://", true) -> "Enlaces VMess"
                rawText.contains("vless://", true) -> "Enlaces VLESS"
                rawText.contains("trojan://", true) -> "Enlaces Trojan"
                rawText.contains("hysteria2://", true) || rawText.contains("hy2://", true) -> "Enlaces Hysteria2"
                rawText.contains("ssh://", true) -> "Enlaces SSH"
                else -> "Enlaces compatibles"
            }
            return ImportResult.Success(protocolProfiles, source)
        }
        return ImportResult.Error("Formato GNX2, JSON legado o enlace de protocolo no reconocido")
    }

    private fun parseJson(jsonString: String): ImportResult? {
        val trimmed = jsonString.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        return try {
            if (trimmed.startsWith("[")) {
                val profiles = gson.fromJson(trimmed, Array<VpnProfileJson>::class.java)?.toList().orEmpty()
                val mapped = profiles.mapNotNull(VpnProfileJson::toVpnProfile)
                if (mapped.isEmpty()) ImportResult.Error("No se encontraron perfiles válidos")
                else ImportResult.Success(mapped, "Importación JSON legado")
            } else {
                val document = gson.fromJson(trimmed, VpnProfileDocument::class.java)
                val mapped = document?.profiles.orEmpty().mapNotNull(VpnProfileJson::toVpnProfile)
                if (mapped.isEmpty()) ImportResult.Error("No se encontraron perfiles válidos")
                else ImportResult.Success(mapped, document?.appName ?: "Importación externa")
            }
        } catch (error: JsonSyntaxException) {
            ImportResult.Error("JSON malformado: ${error.message?.take(80).orEmpty()}")
        } catch (error: Throwable) {
            ImportResult.Error(safeError(error))
        }
    }

    private fun encryptProfiles(profiles: List<VpnProfile>, passphrase: CharArray): ByteArray {
        val jsonBytes = exportPlainJson(profiles).toByteArray(Charsets.UTF_8)
        return try {
            SecureConfigCodec.encrypt(jsonBytes.toString(Charsets.UTF_8), passphrase)
        } finally {
            NativeGuard.wipe(jsonBytes)
        }
    }

    private fun exportPlainJson(profiles: List<VpnProfile>): String {
        val document = VpnProfileDocument(
            appName = "Ghost Nexora VPN",
            version = BuildConfig.VERSION_NAME,
            exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date()),
            profiles = profiles.map(VpnProfile::toJson)
        )
        return gson.toJson(document)
    }

    private fun defaultExportFileName(): String = "ghost_nexora_${System.currentTimeMillis()}.gnx"

    private fun ensureGnxExtension(name: String): String =
        if (name.endsWith(".gnx", ignoreCase = true)) name else "$name.gnx"

    private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "El archivo excede el límite permitido" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun safeError(error: Throwable): String =
        error.message?.take(160)?.takeIf(String::isNotBlank) ?: "Operación no disponible"

    private companion object {
        const val MAX_IMPORT_BYTES = 32 * 1024 * 1024
        const val MIME_GNX = "application/octet-stream"
    }
}

data class VpnProfileDocument(
    val appName: String? = "Ghost Nexora VPN",
    val version: String? = BuildConfig.VERSION_NAME,
    val exportedAt: String? = null,
    val profiles: List<VpnProfileJson>? = null
)

data class VpnProfileJson(
    val id: String? = null,
    val name: String? = null,
    val host: String? = null,
    val port: Int? = 443,
    val username: String? = "",
    val password: String? = "",
    val method: String? = "ssh",
    val connectionMode: String? = null,
    val sslEnabled: Boolean? = true,
    val sni: String? = "",
    val payload: String? = "",
    val proxy: ProxyJson? = null,
    val tags: List<String>? = emptyList(),
    val notes: String? = "",
    val enabled: Boolean? = true,
    val lastUsed: String? = ""
) {
    fun toVpnProfile(): VpnProfile? {
        val resolvedHost = host?.trim().orEmpty()
        if (resolvedHost.isEmpty()) return null
        return VpnProfile(
            id = id?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
            name = name?.trim()?.ifEmpty { resolvedHost } ?: resolvedHost,
            host = resolvedHost,
            port = port?.takeIf { it in 1..65535 } ?: 443,
            username = username.orEmpty(),
            password = password.orEmpty(),
            method = method ?: "ssh",
            connectionMode = ConnectionMode.fromStored(connectionMode, method, sslEnabled).id,
            sslEnabled = sslEnabled ?: false,
            sni = sni.orEmpty(),
            payload = payload.orEmpty(),
            proxy = ProxyConfig(
                host = proxy?.host.orEmpty(),
                port = proxy?.port ?: 0,
                type = proxy?.type.orEmpty()
            ),
            tagsRaw = tags?.joinToString(",").orEmpty(),
            notes = notes.orEmpty(),
            enabled = enabled ?: true,
            lastUsed = lastUsed.orEmpty(),
            createdAt = System.currentTimeMillis()
        )
    }
}

data class ProxyJson(
    val host: String? = "",
    val port: Int? = 0,
    val type: String? = ""
)

fun VpnProfile.toJson() = VpnProfileJson(
    id = id,
    name = name,
    host = host,
    port = port,
    username = username,
    password = password,
    method = method,
    connectionMode = connectionMode,
    sslEnabled = sslEnabled,
    sni = sni,
    payload = payload,
    proxy = ProxyJson(proxy.host, proxy.port, proxy.type),
    tags = tags,
    notes = notes,
    enabled = enabled,
    lastUsed = lastUsed
)

sealed class ImportResult {
    data class Success(val profiles: List<VpnProfile>, val sourceName: String) : ImportResult()
    data class PasswordRequired(val message: String) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

data class ValidationResult(
    val isValid: Boolean,
    val message: String,
    val profileCount: Int
)
