package com.ghostnexora.vpn.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateServiceTest {
    @Test
    fun parsesOnlyExplicitVersionCodeMetadata() {
        assertEquals(31, GitHubUpdateService.parseVersionCode("versionCode: 31"))
        assertEquals(42, GitHubUpdateService.parseVersionCode("version_code=42"))
        assertNull(GitHubUpdateService.parseVersionCode("v1.0.31-build194"))
    }

    @Test
    fun comparesSemanticVersionsWithoutTreatingBuildTagAsVersionCode() {
        assertTrue(GitHubUpdateService.isNewer("v1.0.31-build194", "1.0.30"))
        assertFalse(GitHubUpdateService.isNewer("v1.0.31-build194", "1.0.31"))
        assertFalse(GitHubUpdateService.isNewer("release-194", "1.0.31"))
    }

    @Test
    fun buildsStableReleaseIdentity() {
        val release = GitHubReleaseResponse(id = 99, tagName = "v1.0.31")
        assertEquals("code:31", GitHubUpdateService.releaseIdentity(release, 31))
        assertEquals("release:99", GitHubUpdateService.releaseIdentity(release, null))
    }
}
