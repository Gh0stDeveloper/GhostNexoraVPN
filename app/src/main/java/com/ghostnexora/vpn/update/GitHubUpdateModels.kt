package com.ghostnexora.vpn.update

import com.google.gson.annotations.SerializedName

data class GitHubReleaseResponse(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("body") val body: String = "",
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("prerelease") val prerelease: Boolean = false,
    @SerializedName("draft") val draft: Boolean = false,
    @SerializedName("published_at") val publishedAt: String = "",
    @SerializedName("assets") val assets: List<GitHubAsset> = emptyList()
)

data class GitHubAsset(
    @SerializedName("name") val name: String = "",
    @SerializedName("browser_download_url") val browserDownloadUrl: String = "",
    @SerializedName("content_type") val contentType: String = "",
    @SerializedName("size") val size: Long = 0L
)

data class UpdateUiState(
    val checking: Boolean = false,
    val available: Boolean = false,
    val downloading: Boolean = false,
    val installing: Boolean = false,
    val dismissed: Boolean = false,
    val currentVersion: String = "",
    val latestVersion: String = "",
    val releaseNotes: String = "",
    val releaseUrl: String = "",
    val publishedAt: String = "",
    val downloadUrl: String = "",
    val expectedSha256: String = "",
    val currentVersionCode: Int = 0,
    val latestVersionCode: Int = 0,
    val updateIdentity: String = "",
    val lastCheckedAt: Long = 0L,
    val downloadProgress: Int = 0,
    val pendingApkPath: String = "",
    val needsInstallPermission: Boolean = false,
    val error: String? = null,
    val message: String? = null
)
