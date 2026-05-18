
package com.ghostnexora.vpn.update

import com.google.gson.annotations.SerializedName

data class GitHubReleaseResponse(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("body") val body: String = "",
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("assets") val assets: List<GitHubAsset> = emptyList()
)

data class GitHubAsset(
    @SerializedName("name") val name: String = "",
    @SerializedName("browser_download_url") val browserDownloadUrl: String = "",
    @SerializedName("content_type") val contentType: String = ""
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
    val downloadUrl: String = "",
    val error: String? = null,
    val message: String? = null
)
