package com.ghostnexora.vpn.util

import com.ghostnexora.vpn.data.model.VpnProfile
import java.security.MessageDigest
import java.util.Locale

object ProfileFingerprint {
    fun of(profile: VpnProfile): String {
        if (profile.isLocked) {
            return "locked:" + profile.lockedPackageId.ifBlank { profile.id }
        }
        val canonical = listOf(
            profile.selectedMode.id,
            profile.host.trim().lowercase(Locale.US),
            profile.port.toString(),
            profile.username.trim(),
            profile.password,
            profile.sslEnabled.toString(),
            profile.sni.trim().lowercase(Locale.US),
            profile.selectedTlsVerificationMode.id,
            profile.payload.trim(),
            profile.proxy.type.trim().lowercase(Locale.US),
            profile.proxy.host.trim().lowercase(Locale.US),
            profile.proxy.port.toString()
        ).joinToString("\u001F")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun uniqueAgainst(imported: List<VpnProfile>, existing: List<VpnProfile>): Pair<List<VpnProfile>, Int> {
        val seen = existing.mapTo(mutableSetOf(), ::of)
        val unique = ArrayList<VpnProfile>(imported.size)
        var skipped = 0
        imported.forEach { profile ->
            if (seen.add(of(profile))) unique += profile else skipped += 1
        }
        return unique to skipped
    }
}
