package com.ghostnexora.vpn.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.ProxyConfig
import com.ghostnexora.vpn.data.model.VpnProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    fun importFromUri(uri: Uri): ImportResult {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult.Error("No se pudo abrir el archivo")

            val jsonString = inputStream.bufferedReader().use { it.readText() }
            parseJson(jsonString)
        } catch (e: Exception) {
            ImportResult.Error("Error al leer el archivo: ${e.message}")
        }
    }

    fun importFromString(jsonString: String): ImportResult = parseJson(jsonString)

    private fun parseJson(jsonString: String): ImportResult {
        if (jsonString.isBlank()) return ImportResult.Error("El archivo está vacío")

        return try {
            val document = gson.fromJson(jsonString, VpnProfileDocument::class.java)

            if (document?.profiles.isNullOrEmpty()) {
                val profiles = gson.fromJson(jsonString, Array<VpnProfileJson>::class.java)
                    ?.toList()
                    ?: return ImportResult.Error("Formato JSON no reconocido")

                val mapped = profiles.mapNotNull { it.toVpnProfile() }
                if (mapped.isEmpty()) ImportResult.Error("No se encontraron perfiles válidos")
                else ImportResult.Success(mapped, document?.appName ?: "Importación externa")
            } else {
                val mapped = document.profiles!!.mapNotNull { it.toVpnProfile() }
                ImportResult.Success(mapped, document.appName ?: "Ghost Nexora VPN")
            }
        } catch (e: JsonSyntaxException) {
            ImportResult.Error("JSON malformado: ${e.message?.take(80)}")
        } catch (e: Exception) {
            ImportResult.Error("Error inesperado: ${e.message}")
        }
    }

    fun exportToFile(profiles: List<VpnProfile>): File? {
        return try {
            val jsonString = exportToString(profiles)
            val fileName = "ghost_nexora_export_${System.currentTimeMillis()}.json"
            val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
            val outputFile = File(outputDir, fileName)
            outputFile.writeText(jsonString)
            outputFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Guarda el JSON directamente en Descargas/GhostNexoraVPN.
     * Devuelve la Uri del archivo creado o null si falla.
     */
    fun exportToDownloads(
        profiles: List<VpnProfile>,
        fileName: String = defaultExportFileName()
    ): Uri? {
        return try {
            val jsonString = exportToString(profiles)
            val mimeType = "application/json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + File.separator + "GhostNexoraVPN"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = context.contentResolver.insert(collection, values) ?: return null

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonString.toByteArray())
                    out.flush()
                } ?: return null

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(dir, "GhostNexoraVPN").apply { mkdirs() }
                val outputFile = File(targetDir, fileName)
                outputFile.writeText(jsonString)
                Uri.fromFile(outputFile)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Escribe el JSON exportado en un Uri elegido manualmente con SAF.
     */
    fun exportToUri(
        uri: Uri,
        profiles: List<VpnProfile>
    ): Boolean {
        return try {
            val jsonString = exportToString(profiles)
            context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                out.write(jsonString.toByteArray())
                out.flush()
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun exportToString(profiles: List<VpnProfile>): String {
        val document = VpnProfileDocument(
            appName = "Ghost Nexora VPN",
            version = "1.0.0",
            exportedAt = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.getDefault()
            ).format(Date()),
            profiles = profiles.map { it.toJson() }
        )
        return gson.toJson(document)
    }

    fun validateJson(jsonString: String): ValidationResult {
        if (jsonString.isBlank()) return ValidationResult(false, "Archivo vacío", 0)

        return try {
            val doc = gson.fromJson(jsonString, VpnProfileDocument::class.java)
            val count = doc?.profiles?.size ?: 0
            if (count > 0) {
                ValidationResult(true, "Formato válido", count)
            } else {
                val arr = gson.fromJson(jsonString, Array<VpnProfileJson>::class.java)
                val arrCount = arr?.size ?: 0
                if (arrCount > 0) ValidationResult(true, "Formato array válido", arrCount)
                else ValidationResult(false, "No se encontraron perfiles", 0)
            }
        } catch (_: JsonSyntaxException) {
            ValidationResult(false, "JSON malformado", 0)
        }
    }

    private fun defaultExportFileName(): String =
        "ghost_nexora_export_${System.currentTimeMillis()}.json"
}

data class VpnProfileDocument(
    val appName: String? = "Ghost Nexora VPN",
    val version: String? = "1.0.0",
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
        val resolvedHost = host?.trim() ?: return null
        if (resolvedHost.isEmpty()) return null

        return VpnProfile(
            id = if (id.isNullOrBlank()) UUID.randomUUID().toString() else id,
            name = name?.trim()?.ifEmpty { resolvedHost } ?: resolvedHost,
            host = resolvedHost,
            port = port?.takeIf { it in 1..65535 } ?: 443,
            username = username ?: "",
            password = password ?: "",
            method = method ?: "ssh",
            connectionMode = ConnectionMode.fromStored(connectionMode, method, sslEnabled).id,
            sslEnabled = sslEnabled ?: false,
            sni = sni ?: "",
            payload = payload ?: "",
            proxy = ProxyConfig(
                host = proxy?.host ?: "",
                port = proxy?.port ?: 0,
                type = proxy?.type ?: ""
            ),
            tagsRaw = tags?.joinToString(",") ?: "",
            notes = notes ?: "",
            enabled = enabled ?: true,
            lastUsed = lastUsed ?: "",
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
    connectionMode = connectionMode ?: VpnProfile.empty().selectedMode.id,
    sslEnabled = sslEnabled,
    sni = sni,
    payload = payload,
    proxy = ProxyJson(
        host = proxy.host,
        port = proxy.port,
        type = proxy.type
    ),
    tags = tags,
    notes = notes,
    enabled = enabled,
    lastUsed = lastUsed
)

sealed class ImportResult {
    data class Success(val profiles: List<VpnProfile>, val sourceName: String) : ImportResult()
    data class Error(val message: String) : ImportResult()
}


data class ValidationResult(
    val isValid: Boolean,
    val message: String,
    val profileCount: Int
)
