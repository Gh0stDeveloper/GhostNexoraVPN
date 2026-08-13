package com.ghostnexora.vpn.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExportNotesUiArchitectureTest {
    @Test
    fun individualExportExposesExclusiveProtectionAndEditabilityChoices() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/ui/screens/profiles/ProfileListScreen.kt"
        )
        assertTrue(source.contains("Configuración editable"))
        assertTrue(source.contains("Configuración bloqueada"))
        assertTrue(source.contains("Cifrado automático por la aplicación"))
        assertTrue(source.contains("Contraseña personalizada"))
        assertTrue(source.contains("RadioButton"))
        assertTrue(source.contains("nonce/IV aleatorio nuevo en cada exportación"))
    }

    @Test
    fun importedProfileBecomesActiveSoItsCreatorNoteAppearsOnHome() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/ui/screens/importexport/ImportExportViewModel.kt"
        )
        val saveIndex = source.indexOf("repository.saveProfiles(unique)")
        val activateIndex = source.indexOf("repository.setActiveProfileId(unique.first().id)")
        assertTrue(saveIndex >= 0)
        assertTrue(activateIndex > saveIndex)
    }

    @Test
    fun dashboardRendersSanitizedCreatorNoteBelowTheActiveProfile() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/ui/screens/dashboard/DashboardScreen.kt"
        )
        val profileIndex = source.indexOf("ActiveProfileCard(state.activeProfile, onProfiles)")
        val noteIndex = source.indexOf("CreatorNoteSection(state.activeProfile)")
        assertTrue(profileIndex >= 0)
        assertTrue(noteIndex > profileIndex)
        assertTrue(source.contains("HtmlNoteView("))
        assertTrue(source.contains("profile?.displayNoteHtml.orEmpty()"))
    }

    @Test
    fun dashboardCreatorNoteKeepsItsCompleteContentVerticallyScrollable() {
        val dashboard = sourceFile(
            "src/main/java/com/ghostnexora/vpn/ui/screens/dashboard/DashboardScreen.kt"
        )
        val noteView = sourceFile(
            "src/main/java/com/ghostnexora/vpn/ui/components/HtmlNoteView.kt"
        )

        assertTrue(dashboard.contains("Contenido completo"))
        assertTrue(dashboard.contains("Desliza dentro de la nota para leer toda la información"))
        assertTrue(noteView.contains("ScrollableNoteWebView(context)"))
        assertTrue(noteView.contains("rememberNestedScrollInteropConnection()"))
        assertTrue(noteView.contains(".nestedScroll(nestedScrollInteropConnection)"))
        assertTrue(noteView.contains("ViewCompat.setNestedScrollingEnabled(this, true)"))
        assertTrue(noteView.contains("requestDisallowInterceptTouchEvent"))
        assertTrue(noteView.contains("isScrollbarFadingEnabled = false"))
        assertTrue(noteView.contains("overflow-y: auto"))
    }

    @Test
    fun logPageUsesOneFullHeightConsoleInsteadOfOneCardPerEvent() {
        val dashboard = sourceFile(
            "src/main/java/com/ghostnexora/vpn/ui/screens/dashboard/DashboardScreen.kt"
        )
        val logSection = dashboard
            .substringAfter("private fun LogPage")
            .substringBefore("private fun stateColor")
        assertFalse(logSection.contains("GhostCard("))
        assertTrue(logSection.contains("maxHeight = null"))

        val console = sourceFile(
            "src/main/java/com/ghostnexora/vpn/ui/components/HttpInjectorLogConsole.kt"
        )
        assertTrue(console.contains("Surface("))
        assertTrue(console.contains("items(orderedLogs"))
        assertFalse(console.contains("GhostCard("))
        assertTrue(console.contains("maxHeight: Dp?"))
    }

    private fun sourceFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate source file: $relativePath")
        return file.readText()
    }
}
