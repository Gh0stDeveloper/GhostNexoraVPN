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
import com.ghostnexora.vpn.data.model.TlsVerificationMode
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.security.AppManagedConfigKeyProvider
import com.ghostnexora.vpn.security.Gnx3ConfigCodec
import com.ghostnexora.vpn.security.Gnx3ConfigException
import com.ghostnexora.vpn.security.Gnx3ProtectionKey
import com.ghostnexora.vpn.security.Gnx3ProtectionMode
import com.ghostnexora.vpn.security.HtmlNoteSanitizer
import com.ghostnexora.vpn.security.LockedProfileVault
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
    @ApplicationContext private val context: Context,
    private val appManagedKeyProvider: AppManagedConfigKeyProvider,
    private val lockedProfileVault: LockedProfileVault
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
        if (rawText.length > MAX_IMPORT_TEXT_CHARS) {
            return ImportResult.Error("El contenido en texto excede el límite permitido")
        }
        return if (Gnx3ConfigCodec.isTextEnvelope(rawText)) {
            runCatching { Gnx3ConfigCodec.decodeTextEnvelope(rawText) }
                .fold(
                    onSuccess = { parseGnx3(it, passphrase) },
                    onFailure = { ImportResult.Error(safeError(it)) }
                )
        } else if (SecureConfigCodec.isTextEnvelope(rawText)) {
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
    ): Uri? = runCatching {
        writeToDownloads(encryptProfiles(profiles, passphrase), fileName)
    }.getOrNull()

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

    fun exportIndividualToCache(
        profile: VpnProfile,
        options: IndividualExportOptions
    ): File {
        require(!profile.isLocked) {
            "Una configuración bloqueada no se puede reexportar"
        }
        val encrypted = encryptIndividual(profile, options)
        val directory = File(context.cacheDir, "shared_configs").apply { mkdirs() }
        directory.listFiles()
            ?.filter { System.currentTimeMillis() - it.lastModified() > SHARED_FILE_MAX_AGE_MS }
            ?.forEach(File::delete)
        val safeName = profile.name
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .take(48)
            .ifBlank { "ghost_nexora_profile" }
        return File(directory, "${safeName}_${System.currentTimeMillis()}.gnx").apply {
            writeBytes(encrypted)
        }
    }

    fun exportIndividualToDownloads(
        profile: VpnProfile,
        options: IndividualExportOptions
    ): Uri? = runCatching {
        val encrypted = encryptIndividual(profile, options)
        writeToDownloads(
            encrypted,
            "${profile.name.ifBlank { "ghost_nexora_profile" }}_${System.currentTimeMillis()}.gnx"
        )
    }.getOrNull()

    fun validatePassphrase(passphrase: CharArray): ValidationResult =
        if (SecureConfigCodec.validatePassphrase(passphrase)) {
            ValidationResult(true, "Contraseña de protección válida", 0)
        } else {
            ValidationResult(false, "Usa al menos 10 caracteres", 0)
        }

    private fun parseImportBytes(bytes: ByteArray, passphrase: CharArray?): ImportResult {
        if (bytes.isEmpty()) return ImportResult.Error("El archivo está vacío")
        return if (Gnx3ConfigCodec.isEncrypted(bytes)) {
            parseGnx3(bytes, passphrase)
        } else if (SecureConfigCodec.isEncrypted(bytes)) {
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

    private fun parseGnx3(
        bytes: ByteArray,
        passphrase: CharArray?
    ): ImportResult {
        return try {
            val info = Gnx3ConfigCodec.inspect(bytes)
            if (info.locked) NativeGuard.requireProtectedRuntime()
            val decoded = when (info.protectionMode) {
                Gnx3ProtectionMode.PASSWORD -> {
                    val password = passphrase ?: return ImportResult.PasswordRequired(
                        "La configuración individual GNX3 requiere contraseña"
                    )
                    Gnx3ConfigCodec.decrypt(
                        bytes,
                        Gnx3ProtectionKey.Password(password)
                    )
                }
                Gnx3ProtectionMode.APP_MANAGED -> {
                    val appKey = appManagedKeyProvider.obtain()
                    try {
                        Gnx3ConfigCodec.decrypt(
                            bytes,
                            Gnx3ProtectionKey.AppManaged(appKey)
                        )
                    } finally {
                        NativeGuard.wipe(appKey)
                    }
                }
            }
            val document = gson.fromJson(decoded.json, Gnx3ProfileDocument::class.java)
                ?: return ImportResult.Error("El paquete GNX3 está vacío")
            require(document.formatVersion == 3) { "Documento GNX3 no soportado" }
            require(document.packageId.isNotBlank()) { "Paquete GNX3 sin identidad" }
            require(document.locked == decoded.info.locked) {
                "La política de bloqueo GNX3 fue alterada"
            }
            val mapped = document.profile?.toVpnProfile()
                ?: return ImportResult.Error("El paquete GNX3 no contiene un perfil válido")
            val sanitizedNote = HtmlNoteSanitizer.sanitize(document.noteHtml.orEmpty())
            val imported = mapped.copy(
                id = UUID.randomUUID().toString(),
                noteHtml = sanitizedNote,
                notes = "",
                isLocked = document.locked,
                sealedConfig = "",
                lockedPackageId = document.packageId,
                protectionVersion = 3,
                createdAt = System.currentTimeMillis(),
                lastUsed = "",
                isFavorite = false
            )
            val ready = if (document.locked) {
                lockedProfileVault.seal(imported, document.packageId)
            } else {
                imported
            }
            ImportResult.Success(
                profiles = listOf(lockedProfileVault.visible(ready)),
                sourceName = if (document.locked) {
                    "Configuración individual GNX3 bloqueada"
                } else {
                    "Configuración individual GNX3"
                },
                storageProfiles = listOf(ready)
            )
        } catch (error: Gnx3ConfigException) {
            ImportResult.Error(error.message ?: "No se pudo abrir la configuración GNX3")
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
        return ImportResult.Error("Formato GNX3/GNX2, JSON legado o enlace de protocolo no reconocido")
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

    private fun encryptIndividual(
        profile: VpnProfile,
        options: IndividualExportOptions
    ): ByteArray {
        require(!profile.isLocked) {
            "Una configuración bloqueada no se puede reexportar"
        }
        if (options.protectionMode == Gnx3ProtectionMode.PASSWORD) {
            val password = options.password
                ?: throw IllegalArgumentException("Falta la contraseña GNX3")
            require(Gnx3ConfigCodec.validatePassword(password)) {
                "La contraseña GNX3 debe tener al menos 10 caracteres"
            }
        }
        val packageId = UUID.randomUUID().toString()
        val sanitizedNote = HtmlNoteSanitizer.sanitize(options.noteHtml)
        val exportedProfile = profile.copy(
            id = UUID.randomUUID().toString(),
            noteHtml = sanitizedNote,
            notes = "",
            isLocked = options.locked,
            sealedConfig = "",
            lockedPackageId = packageId,
            protectionVersion = 3,
            lastUsed = "",
            isFavorite = false
        )
        val document = Gnx3ProfileDocument(
            packageId = packageId,
            locked = options.locked,
            noteHtml = sanitizedNote,
            exportedAt = utcNow(),
            profile = exportedProfile.toJson()
        )
        val jsonBytes = gson.toJson(document).toByteArray(Charsets.UTF_8)
        return try {
            when (options.protectionMode) {
                Gnx3ProtectionMode.PASSWORD -> Gnx3ConfigCodec.encrypt(
                    jsonBytes.toString(Charsets.UTF_8),
                    Gnx3ProtectionKey.Password(requireNotNull(options.password)),
                    options.locked
                )
                Gnx3ProtectionMode.APP_MANAGED -> {
                    val appKey = appManagedKeyProvider.obtain()
                    try {
                        Gnx3ConfigCodec.encrypt(
                            jsonBytes.toString(Charsets.UTF_8),
                            Gnx3ProtectionKey.AppManaged(appKey),
                            options.locked
                        )
                    } finally {
                        NativeGuard.wipe(appKey)
                    }
                }
            }
        } finally {
            NativeGuard.wipe(jsonBytes)
        }
    }

    private fun exportPlainJson(profiles: List<VpnProfile>): String {
        val document = VpnProfileDocument(
            appName = "Ghost Nexora VPN",
            version = BuildConfig.VERSION_NAME,
            exportedAt = utcNow(),
            profiles = profiles.map(VpnProfile::toJson)
        )
        return gson.toJson(document)
    }

    private fun utcNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun writeToDownloads(bytes: ByteArray, fileName: String): Uri? {
        val safeFileName = ensureGnxExtension(
            fileName
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .take(96)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_GNX)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + File.separator + "GhostNexoraVPN"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values) ?: return null
            return try {
                val stream = context.contentResolver.openOutputStream(uri)
                if (stream == null) {
                    context.contentResolver.delete(uri, null, null)
                    return null
                }
                stream.use { output ->
                    output.write(bytes)
                    output.flush()
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        }
        @Suppress("DEPRECATION")
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "GhostNexoraVPN"
        ).apply { mkdirs() }
        val output = File(directory, safeFileName)
        output.writeBytes(bytes)
        return Uri.fromFile(output)
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

    companion object {
        const val MAX_IMPORT_BYTES = 32 * 1024 * 1024
        const val MAX_IMPORT_TEXT_CHARS = 44 * 1024 * 1024
        const val MIME_GNX = "application/vnd.ghostnexora.gnx"
        private const val SHARED_FILE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}

data class IndividualExportOptions(
    val locked: Boolean,
    val noteHtml: String,
    val protectionMode: Gnx3ProtectionMode,
    val password: CharArray? = null
)

data class Gnx3ProfileDocument(
    val formatVersion: Int = 3,
    val packageId: String = "",
    val locked: Boolean = false,
    val noteHtml: String? = "",
    val exportedAt: String? = null,
    val profile: VpnProfileJson? = null
)

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
    val tlsVerificationMode: String? = TlsVerificationMode.STRICT.id,
    val payload: String? = "",
    val proxy: ProxyJson? = null,
    val tags: List<String>? = emptyList(),
    val notes: String? = "",
    val noteHtml: String? = "",
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
            tlsVerificationMode = TlsVerificationMode.fromStored(tlsVerificationMode).id,
            payload = payload.orEmpty(),
            proxy = ProxyConfig(
                host = proxy?.host.orEmpty(),
                port = proxy?.port ?: 0,
                type = proxy?.type.orEmpty()
            ),
            tagsRaw = tags?.joinToString(",").orEmpty(),
            notes = notes.orEmpty(),
            noteHtml = HtmlNoteSanitizer.sanitize(noteHtml.orEmpty()),
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
    tlsVerificationMode = selectedTlsVerificationMode.id,
    payload = payload,
    proxy = ProxyJson(proxy.host, proxy.port, proxy.type),
    tags = tags,
    notes = notes,
    noteHtml = HtmlNoteSanitizer.sanitize(displayNoteHtml),
    enabled = enabled,
    lastUsed = lastUsed
)

sealed class ImportResult {
    data class Success(
        val profiles: List<VpnProfile>,
        val sourceName: String,
        val storageProfiles: List<VpnProfile> = profiles
    ) : ImportResult()
    data class PasswordRequired(val message: String) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

data class ValidationResult(
    val isValid: Boolean,
    val message: String,
    val profileCount: Int
)
