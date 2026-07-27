package com.ghostnexora.vpn.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnownHostStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val file = context.filesDir.resolve("ssh_known_hosts")

    fun list(): List<KnownHostEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .map(String::trim)
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull(::parseLine)
        }.getOrDefault(emptyList())
    }

    fun count(): Int = list().size

    fun clear(): Boolean = runCatching {
        if (file.exists()) file.writeText("") else file.createNewFile()
        true
    }.getOrDefault(false)

    private fun parseLine(line: String): KnownHostEntry? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 3) return null
        val hosts = parts[0]
        val algorithm = parts[1]
        val keyBytes = runCatching { Base64.decode(parts[2], Base64.DEFAULT) }.getOrNull()
        val fingerprint = keyBytes?.let {
            val digest = MessageDigest.getInstance("SHA-256").digest(it)
            "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
        }.orEmpty()
        return KnownHostEntry(hosts, algorithm, fingerprint)
    }
}

data class KnownHostEntry(
    val host: String,
    val algorithm: String,
    val fingerprint: String
)
