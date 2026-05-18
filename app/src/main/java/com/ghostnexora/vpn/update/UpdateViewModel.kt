
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
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val service = GitHubUpdateService()

    private val _uiState = MutableStateFlow(UpdateUiState(currentVersion = BuildConfig.VERSION_NAME))
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdates(force: Boolean = false) {
        if (_uiState.value.checking || (_uiState.value.available && !force)) return
        viewModelScope.launch {
            _uiState.update { it.copy(checking = true, error = null, message = null) }
            try {
                val release = withContext(Dispatchers.IO) { service.latestRelease() }
                val current = BuildConfig.VERSION_NAME
                val newer = GitHubUpdateService.isNewer(release.tagName.ifBlank { release.name }, current)
                val apkAsset = release.assets.firstOrNull { asset ->
                    asset.name.endsWith(".apk", ignoreCase = true) ||
                            asset.contentType.contains("application/vnd.android.package-archive", ignoreCase = true)
                }
                _uiState.update {
                    it.copy(
                        checking = false,
                        available = newer && apkAsset != null,
                        latestVersion = release.tagName.ifBlank { release.name },
                        releaseNotes = release.body,
                        downloadUrl = apkAsset?.browserDownloadUrl.orEmpty(),
                        message = if (newer && apkAsset == null) "Hay una nueva versión, pero no se encontró APK adjunto" else null
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
                    service.downloadToFile(state.downloadUrl, destination)
                }
                _uiState.update { it.copy(downloading = false, installing = true) }
                installApk(file)
                _uiState.update { it.copy(installing = false, available = false, dismissed = true, message = "Instalador abierto") }
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
