package com.ghostnexora.vpn.update

import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class GitHubUpdateService(
    private val owner: String = "Gh0stDeveloper",
    private val repo: String = "GhostNexoraVPN"
) {
    private val gson = Gson()

    fun latestRelease(): GitHubReleaseResponse {
        val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
        val connection = openConnection(url)
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("GitHub returned HTTP $code: ${body.ifBlank { "no details" }}")
            }
            return gson.fromJson(body, GitHubReleaseResponse::class.java)
        } finally {
            connection.disconnect()
        }
    }

    fun downloadToFile(
        downloadUrl: String,
        destination: File,
        progress: (Int) -> Unit = {}
    ): File {
        val connection = openConnection(URL(downloadUrl), readTimeoutMs = 60_000)
        val partial = File(destination.parentFile, "${destination.name}.part")
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Update download failed ($code): ${body.ifBlank { "no details" }}")
            }

            destination.parentFile?.mkdirs()
            partial.delete()
            val expectedLength = connection.contentLengthLong
            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (expectedLength > 0L) {
                            progress(((downloaded * 100L) / expectedLength).toInt().coerceIn(0, 100))
                        }
                    }
                    output.fd.sync()
                }
            }
            if (expectedLength > 0L && downloaded != expectedLength) {
                throw IllegalStateException("Incomplete APK download: $downloaded of $expectedLength bytes")
            }
            if (partial.length() <= 0L) error("The downloaded APK is empty")
            destination.delete()
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
            progress(100)
            return destination
        } finally {
            partial.takeIf { it.exists() && !destination.exists() }?.delete()
            connection.disconnect()
        }
    }

    fun downloadText(downloadUrl: String): String {
        val connection = openConnection(URL(downloadUrl))
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Resource download failed ($code): ${body.ifBlank { "no details" }}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: URL, readTimeoutMs: Int = 20_000): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "GhostNexoraVPN/${BuildInfo.USER_AGENT_VERSION}")
        }

    private object BuildInfo {
        const val USER_AGENT_VERSION = "1.0"
    }

    companion object {
        private val semanticRegex = Regex("""(?i)(?:^|[^0-9])v?(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?""")

        fun isNewer(remoteTag: String, currentVersion: String): Boolean {
            val remote = semanticVersion(remoteTag) ?: return false
            val current = semanticVersion(currentVersion) ?: return false
            val size = maxOf(remote.size, current.size)
            for (index in 0 until size) {
                val remotePart = remote.getOrElse(index) { 0 }
                val currentPart = current.getOrElse(index) { 0 }
                if (remotePart != currentPart) return remotePart > currentPart
            }
            return false
        }

        fun extractVersionName(vararg values: String): String? = values
            .asSequence()
            .mapNotNull { semanticRegex.find(it)?.groupValues?.drop(1)?.take(3) }
            .map { it.joinToString(".") }
            .firstOrNull()

        /** Parses only explicit release metadata; semantic tags are not versionCode values. */
        fun parseVersionCode(text: String): Int? =
            Regex("""(?i)(?:versionCode|version_code)\s*[:=]\s*(\d+)""")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }

        fun releaseIdentity(release: GitHubReleaseResponse, versionCode: Int?): String = when {
            versionCode != null -> "code:$versionCode"
            release.id > 0L -> "release:${release.id}"
            else -> "tag:${release.tagName.ifBlank { release.name }.trim().lowercase()}"
        }

        private fun semanticVersion(value: String): List<Int>? {
            val match = semanticRegex.find(value) ?: return null
            return match.groupValues.drop(1).map { it.toIntOrNull() ?: 0 }
        }
    }
}
