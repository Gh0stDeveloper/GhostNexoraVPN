package com.ghostnexora.vpn.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoutingPreferencesTest {
    @Test
    fun onlySelectedRequiresAtLeastOneValidPackage() {
        assertFalse(AppRoutingPreferences(AppRoutingMode.ONLY_SELECTED).isValid)
        assertTrue(
            AppRoutingPreferences(
                AppRoutingMode.ONLY_SELECTED,
                setOf("com.example.player")
            ).isValid
        )
    }

    @Test
    fun normalizesAndRejectsMalformedPackages() {
        val preferences = AppRoutingPreferences(
            mode = AppRoutingMode.EXCLUDE_SELECTED,
            packages = setOf(" com.example.app ", "not-a-package", "com.example.app", "org.demo.tool")
        )
        assertEquals(setOf("com.example.app", "org.demo.tool"), preferences.normalizedPackages)
    }

    @Test
    fun allAppsModeDoesNotRequireASelection() {
        assertTrue(AppRoutingPreferences(AppRoutingMode.ALL, emptySet()).isValid)
    }
}