
package com.ghostnexora.vpn.update

import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class GitHubUpdateService(
    private val owner: String = "CHICO-CP",
    private val repo: String = "GhostNexoraVPN"
) {
    private val gson = Gson()

    fun latestRelease(): GitHubReleaseResponse {
        val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "GhostNexoraVPN")
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("GitHub respondió $code: ${body.ifBlank { "sin detalle" }}")
            }
            return gson.fromJson(body, GitHubReleaseResponse::class.java)
        } finally {
            connection.disconnect()
        }
    }

    fun downloadToFile(downloadUrl: String, destination: File, progress: (Int) -> Unit = {}): File {
        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "GhostNexoraVPN")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("No se pudo descargar la actualización ($code): ${body.ifBlank { "sin detalle" }}")
            }

            destination.parentFile?.mkdirs()
            val total = connection.contentLengthLong.coerceAtLeast(1L)
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        progress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                    }
                    output.flush()
                }
            }
            return destination
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun isNewer(remoteTag: String, currentVersion: String): Boolean {
            fun normalize(v: String): List<Int> {
                return v
                    .trim()
                    .removePrefix("v")
                    .split('.', '-', '_')
                    .mapNotNull { it.toIntOrNull() }
            }
            val remote = normalize(remoteTag)
            val current = normalize(currentVersion)
            val max = maxOf(remote.size, current.size)
            for (i in 0 until max) {
                val r = remote.getOrElse(i) { 0 }
                val c = current.getOrElse(i) { 0 }
                if (r != c) return r > c
            }
            return false
        }
    }
}
