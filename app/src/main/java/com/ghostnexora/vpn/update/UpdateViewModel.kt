
package com.ghostnexora.vpn.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val service = GitHubUpdateService()
    private val lastCheckWindowMs = 6 * 60 * 60 * 1000L

    private val _uiState = MutableStateFlow(
        UpdateUiState(
            currentVersion = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE
        )
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdates(force: Boolean = false) {
        val current = _uiState.value
        val now = System.currentTimeMillis()

        if (!force && current.checking) return
        if (!force && current.lastCheckedAt > 0L && now - current.lastCheckedAt < lastCheckWindowMs) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(checking = true, error = null, message = null, lastCheckedAt = now) }
            try {
                val release = withContext(Dispatchers.IO) { service.latestRelease() }
                val remoteTag = release.tagName.ifBlank { release.name }.ifBlank { "0" }
                val remoteVersionCode = GitHubUpdateService.parseVersionCode(release.body)
                    ?: GitHubUpdateService.parseVersionCode(remoteTag)
                    ?: current.currentVersionCode + 1

                val apkAsset = release.assets.firstOrNull { asset ->
                    asset.name.endsWith(".apk", ignoreCase = true) ||
                        asset.contentType.contains("application/vnd.android.package-archive", ignoreCase = true)
                }
                val checksumAsset = release.assets.firstOrNull { asset ->
                    asset.name.endsWith(".sha256", ignoreCase = true) ||
                        asset.name.endsWith(".sha256.txt", ignoreCase = true)
                }

                val expectedSha256 = checksumAsset?.browserDownloadUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    withContext(Dispatchers.IO) {
                        runCatching { service.downloadText(url).trim() }.getOrNull().orEmpty()
                    }
                }.orEmpty().ifBlank {
                    Regex("""sha256\s*[:=]\s*([A-Fa-f0-9]{64})""", RegexOption.IGNORE_CASE)
                        .find(release.body)
                        ?.groupValues
                        ?.getOrNull(1)
                        .orEmpty()
                }

                val newer = when {
                    remoteVersionCode > 0 && remoteVersionCode != current.currentVersionCode ->
                        remoteVersionCode > current.currentVersionCode
                    else -> GitHubUpdateService.isNewer(remoteTag, current.currentVersion)
                }

                _uiState.update {
                    it.copy(
                        checking = false,
                        available = newer && apkAsset != null,
                        latestVersion = remoteTag,
                        latestVersionCode = remoteVersionCode,
                        releaseNotes = release.body,
                        downloadUrl = apkAsset?.browserDownloadUrl.orEmpty(),
                        expectedSha256 = expectedSha256,
                        message = when {
                            newer && apkAsset == null -> "Hay una nueva versión, pero no se encontró APK adjunto"
                            release.draft || release.prerelease -> "La release consultada es preliminar"
                            else -> null
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        checking = false,
                        error = e.message ?: "No se pudo comprobar actualizaciones"
                    )
                }
            }
        }
    }

    fun dismissUpdatePrompt() {
        _uiState.update { it.copy(available = false, dismissed = true) }
    }

    fun downloadAndInstall() {
        val state = _uiState.value
        if (state.downloadUrl.isBlank()) {
            _uiState.update { it.copy(error = "No hay un APK disponible para descargar") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(downloading = true, error = null) }
            try {
                val file = withContext(Dispatchers.IO) {
                    val destination = File(context.cacheDir, "ghostnexora-update.apk")
                    val downloaded = service.downloadToFile(state.downloadUrl, destination) { progress ->
                        _uiState.update { it.copy(message = "Descarga: $progress%") }
                    }
                    if (state.expectedSha256.isNotBlank()) {
                        verifyChecksum(downloaded, state.expectedSha256)
                    }
                    downloaded
                }
                _uiState.update { it.copy(downloading = false, installing = true) }
                installApk(file)
                _uiState.update {
                    it.copy(
                        installing = false,
                        available = false,
                        dismissed = true,
                        message = "Instalador abierto"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        downloading = false,
                        installing = false,
                        error = e.message ?: "No se pudo descargar/instalar la actualización"
                    )
                }
            }
        }
    }

    private fun verifyChecksum(file: File, expected: String) {
        val normalized = expected.trim().lowercase()
        if (!normalized.matches(Regex("[a-f0-9]{64}"))) return

        val actual = withContextOrThrow(file)
        if (!actual.equals(normalized, ignoreCase = true)) {
            throw IllegalStateException("El checksum SHA-256 no coincide")
        }
    }

    private fun withContextOrThrow(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun installApk(file: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
