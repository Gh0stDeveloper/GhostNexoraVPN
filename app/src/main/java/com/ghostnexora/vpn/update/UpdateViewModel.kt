package com.ghostnexora.vpn.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.BuildConfig
import com.ghostnexora.vpn.data.local.DataStoreManager
import com.ghostnexora.vpn.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStoreManager
) : ViewModel() {
    private val service = GitHubUpdateService()
    private val automaticCheckWindowMs = 24 * 60 * 60 * 1000L

    private val _uiState = MutableStateFlow(
        UpdateUiState(
            currentVersion = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE
        )
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdates(force: Boolean = false) {
        if (_uiState.value.checking) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val lastCheck = dataStore.lastUpdateCheckAt.first()
            if (!force && lastCheck > 0L && now - lastCheck < automaticCheckWindowMs) {
                return@launch
            }

            _uiState.update {
                it.copy(
                    checking = true,
                    error = null,
                    message = if (force) "Checking GitHub Releases…" else null,
                    lastCheckedAt = now
                )
            }
            dataStore.setLastUpdateCheckAt(now)

            try {
                val release = withContext(Dispatchers.IO) { service.latestRelease() }
                val remoteTag = release.tagName.ifBlank { release.name }
                val remoteVersionName = GitHubUpdateService.extractVersionName(
                    remoteTag,
                    release.name,
                    release.body
                ).orEmpty().ifBlank { remoteTag }
                val remoteVersionCode = GitHubUpdateService.parseVersionCode(release.body)
                val identity = GitHubUpdateService.releaseIdentity(release, remoteVersionCode)
                val dismissedIdentity = dataStore.dismissedUpdateIdentity.first()

                val apkAsset = release.assets
                    .asSequence()
                    .filter { asset ->
                        asset.name.endsWith(".apk", ignoreCase = true) ||
                            asset.contentType.contains(
                                "application/vnd.android.package-archive",
                                ignoreCase = true
                            )
                    }
                    .filterNot { asset ->
                        val lower = asset.name.lowercase()
                        lower.contains("debug") || lower.contains("unsigned") || lower.contains("unaligned")
                    }
                    .maxByOrNull(GitHubAsset::size)

                val checksumAsset = release.assets.firstOrNull { asset ->
                    asset.name.endsWith(".sha256", ignoreCase = true) ||
                        asset.name.endsWith(".sha256.txt", ignoreCase = true)
                }
                val expectedSha256 = checksumAsset
                    ?.browserDownloadUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let { url ->
                        withContext(Dispatchers.IO) {
                            runCatching { extractSha256(service.downloadText(url)) }.getOrNull()
                        }
                    }
                    .orEmpty()
                    .ifBlank { extractSha256(release.body).orEmpty() }

                val newer = when {
                    remoteVersionCode != null -> remoteVersionCode > BuildConfig.VERSION_CODE
                    else -> GitHubUpdateService.isNewer(remoteVersionName, BuildConfig.VERSION_NAME)
                }
                val dismissed = !force && dismissedIdentity == identity
                if (!newer && dismissedIdentity.isNotBlank()) {
                    dataStore.setDismissedUpdateIdentity("")
                }

                _uiState.update {
                    it.copy(
                        checking = false,
                        available = newer && apkAsset != null && !dismissed,
                        dismissed = dismissed,
                        latestVersion = remoteVersionName,
                        latestVersionCode = remoteVersionCode ?: 0,
                        releaseNotes = release.body,
                        releaseUrl = release.htmlUrl,
                        publishedAt = release.publishedAt,
                        downloadUrl = apkAsset?.browserDownloadUrl.orEmpty(),
                        expectedSha256 = expectedSha256,
                        updateIdentity = identity,
                        error = null,
                        message = when {
                            release.draft || release.prerelease -> "The latest release is not a stable production release."
                            newer && apkAsset == null -> "A newer release exists, but it has no production APK asset."
                            force && !newer -> "Ghost Nexora VPN is already up to date."
                            else -> null
                        }
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        checking = false,
                        error = error.message ?: "Unable to check GitHub Releases"
                    )
                }
            }
        }
    }

    fun dismissUpdatePrompt() {
        val identity = _uiState.value.updateIdentity
        viewModelScope.launch {
            if (identity.isNotBlank()) dataStore.setDismissedUpdateIdentity(identity)
        }
        _uiState.update { it.copy(available = false, dismissed = true) }
    }

    fun downloadAndInstall() {
        val state = _uiState.value
        if (state.needsInstallPermission && state.pendingApkPath.isNotBlank()) {
            openInstallPermissionSettings()
            return
        }
        if (state.downloadUrl.isBlank()) {
            _uiState.update { it.copy(error = "No production APK is available for this release") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    downloading = true,
                    installing = false,
                    downloadProgress = 0,
                    error = null,
                    message = null
                )
            }
            try {
                val file = withContext(Dispatchers.IO) {
                    val suffix = state.latestVersionCode.takeIf { it > 0 }?.toString()
                        ?: state.latestVersion.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val destination = File(context.cacheDir, "ghostnexora-$suffix.apk")
                    val cachedIsValid = destination.exists() && runCatching {
                        verifyDownloadedApk(destination, state.expectedSha256)
                    }.isSuccess
                    if (!cachedIsValid) {
                        destination.delete()
                        service.downloadToFile(state.downloadUrl, destination) { progress ->
                            _uiState.update {
                                it.copy(downloadProgress = progress, message = "Downloading update: $progress%")
                            }
                        }
                        verifyDownloadedApk(destination, state.expectedSha256)
                    }
                    destination
                }
                _uiState.update { it.copy(downloading = false, installing = true, downloadProgress = 100) }
                openInstallerOrPermission(file)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        downloading = false,
                        installing = false,
                        error = error.message ?: "Unable to download or validate the update"
                    )
                }
            }
        }
    }

    fun resumePendingInstall() {
        val state = _uiState.value
        if (!state.needsInstallPermission || state.pendingApkPath.isBlank()) return
        if (!PermissionHelper.hasInstallUnknownAppsPermission(context)) return
        val file = File(state.pendingApkPath)
        if (!file.exists()) {
            _uiState.update {
                it.copy(
                    needsInstallPermission = false,
                    pendingApkPath = "",
                    installing = false,
                    error = "The downloaded update is no longer available"
                )
            }
            return
        }
        launchPackageInstaller(file)
    }

    fun clearTransientMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    private fun openInstallerOrPermission(file: File) {
        if (!PermissionHelper.hasInstallUnknownAppsPermission(context)) {
            _uiState.update {
                it.copy(
                    downloading = false,
                    installing = false,
                    needsInstallPermission = true,
                    pendingApkPath = file.absolutePath,
                    message = "Allow Ghost Nexora VPN to install this verified update."
                )
            }
            openInstallPermissionSettings()
            return
        }
        launchPackageInstaller(file)
    }

    private fun openInstallPermissionSettings() {
        context.startActivity(
            PermissionHelper.installUnknownAppsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun launchPackageInstaller(file: File) {
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
        _uiState.update {
            it.copy(
                downloading = false,
                installing = false,
                available = false,
                dismissed = true,
                needsInstallPermission = false,
                pendingApkPath = "",
                message = "Android package installer opened"
            )
        }
    }

    private fun verifyDownloadedApk(file: File, expectedSha256: String) {
        require(file.exists() && file.length() > 0L) { "The downloaded APK is empty" }
        val normalizedExpected = expectedSha256.trim().lowercase()
        if (normalizedExpected.matches(Regex("[a-f0-9]{64}"))) {
            val actual = sha256(file)
            require(actual.equals(normalizedExpected, ignoreCase = true)) {
                "The APK SHA-256 checksum does not match the release metadata"
            }
        }

        val packageInfo = readArchiveInfo(file) ?: error("Android could not read the downloaded APK")
        require(packageInfo.packageName == context.packageName) {
            "The downloaded APK belongs to a different application"
        }
        val archiveVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        require(archiveVersionCode > BuildConfig.VERSION_CODE.toLong()) {
            "The downloaded APK is not newer than the installed version"
        }
    }

    @Suppress("DEPRECATION")
    private fun readArchiveInfo(file: File): PackageInfo? {
        val packageManager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractSha256(text: String): String? =
        Regex("""(?i)(?:sha-?256|APK_SHA256)\s*[:=]?\s*([a-f0-9]{64})""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
}
