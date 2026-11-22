package com.ghostnexora.vpn.navigation

import androidx.annotation.StringRes
import com.ghostnexora.vpn.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Rutas de navegación de Ghost Nexora VPN.
 * Cada objeto define la ruta de pantalla y sus metadatos para el drawer.
 */
sealed class Screen(
    val route: String,
    @StringRes val title: Int,
    val icon: ImageVector,
    val iconSelected: ImageVector = icon,
    val showInDrawer: Boolean = true
) {
    object Dashboard : Screen(
        route = "dashboard",
        title = R.string.nav_dashboard,
        icon = Icons.Outlined.Home,
        iconSelected = Icons.Filled.Home
    )

    object Profiles : Screen(
        route = "profiles",
        title = R.string.nav_profiles,
        icon = Icons.Outlined.VpnKey,
        iconSelected = Icons.Filled.VpnKey
    )

    object CreateProfile : Screen(
        route = "create_profile",
        title = R.string.nav_create_profile,
        icon = Icons.Outlined.AddCircleOutline,
        iconSelected = Icons.Filled.AddCircle
    )

    object AppRouting : Screen(
        route = "app_routing",
        title = R.string.nav_apps,
        icon = Icons.Outlined.Apps,
        iconSelected = Icons.Filled.Apps
    )

    object Compatibility : Screen(
        route = "compatibility",
        title = R.string.nav_compatibility,
        icon = Icons.AutoMirrored.Outlined.FactCheck,
        iconSelected = Icons.AutoMirrored.Filled.FactCheck
    )

    object Import : Screen(
        route = "import",
        title = R.string.nav_import,
        icon = Icons.Outlined.FileDownload,
        iconSelected = Icons.Filled.FileDownload
    )

    object Export : Screen(
        route = "export",
        title = R.string.nav_export,
        icon = Icons.Outlined.FileUpload,
        iconSelected = Icons.Filled.FileUpload
    )

    object History : Screen(
        route = "history",
        title = R.string.nav_history,
        icon = Icons.Outlined.History,
        iconSelected = Icons.Filled.History
    )

    object Logs : Screen(
        route = "logs",
        title = R.string.nav_logs,
        icon = Icons.Outlined.Terminal,
        iconSelected = Icons.Filled.Terminal
    )

    object Settings : Screen(
        route = "settings",
        title = R.string.nav_settings,
        icon = Icons.Outlined.Settings,
        iconSelected = Icons.Filled.Settings
    )

    object Documentation : Screen(
        route = "documentation",
        title = R.string.nav_documentation,
        icon = Icons.Outlined.Description,
        iconSelected = Icons.Filled.Description
    )

    object About : Screen(
        route = "about",
        title = R.string.nav_about,
        icon = Icons.Outlined.Info,
        iconSelected = Icons.Filled.Info
    )

    object EditProfile : Screen(
        route = "edit_profile/{profileId}",
        title = R.string.edit_profile_title,
        icon = Icons.Outlined.Edit,
        showInDrawer = false
    ) {
        fun createRoute(profileId: String) = "edit_profile/$profileId"
        const val ARG_PROFILE_ID = "profileId"
    }

    companion object {
        /**
         * Build the drawer model only after [Screen] and its singleton
         * subclasses have finished class initialization.
         *
         * Keeping this as a computed property is intentional. A static list
         * creates a class-initialization cycle when [Dashboard] is the first
         * screen referenced at launch. Release/R8 can then capture Dashboard's
         * not-yet-initialized INSTANCE as a null list element.
         */
        val drawerItems: List<Screen>
            get() = listOf(
                Dashboard,
                Profiles,
                CreateProfile,
                AppRouting,
                Compatibility,
                Import,
                Export,
                History,
                Logs,
                Settings,
                Documentation,
                About
            )
    }
}
