package com.ghostnexora.vpn.navigation

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
    val title: String,
    val icon: ImageVector,
    val iconSelected: ImageVector = icon,
    val showInDrawer: Boolean = true
) {
    object Dashboard : Screen(
        route = "dashboard",
        title = "Inicio",
        icon = Icons.Outlined.Home,
        iconSelected = Icons.Filled.Home
    )

    object Profiles : Screen(
        route = "profiles",
        title = "Perfiles",
        icon = Icons.Outlined.VpnKey,
        iconSelected = Icons.Filled.VpnKey
    )

    object CreateProfile : Screen(
        route = "create_profile",
        title = "Crear Perfil",
        icon = Icons.Outlined.AddCircleOutline,
        iconSelected = Icons.Filled.AddCircle
    )

    object AppRouting : Screen(
        route = "app_routing",
        title = "Aplicaciones",
        icon = Icons.Outlined.Apps,
        iconSelected = Icons.Filled.Apps
    )

    object Compatibility : Screen(
        route = "compatibility",
        title = "Compatibilidad",
        icon = Icons.AutoMirrored.Outlined.FactCheck,
        iconSelected = Icons.AutoMirrored.Filled.FactCheck
    )

    object Import : Screen(
        route = "import",
        title = "Importar",
        icon = Icons.Outlined.FileDownload,
        iconSelected = Icons.Filled.FileDownload
    )

    object Export : Screen(
        route = "export",
        title = "Exportar",
        icon = Icons.Outlined.FileUpload,
        iconSelected = Icons.Filled.FileUpload
    )

    object History : Screen(
        route = "history",
        title = "Historial",
        icon = Icons.Outlined.History,
        iconSelected = Icons.Filled.History
    )

    object Logs : Screen(
        route = "logs",
        title = "Registros",
        icon = Icons.Outlined.Terminal,
        iconSelected = Icons.Filled.Terminal
    )

    object Settings : Screen(
        route = "settings",
        title = "Ajustes",
        icon = Icons.Outlined.Settings,
        iconSelected = Icons.Filled.Settings
    )

    object Documentation : Screen(
        route = "documentation",
        title = "Documentación",
        icon = Icons.Outlined.Description,
        iconSelected = Icons.Filled.Description
    )

    object About : Screen(
        route = "about",
        title = "Acerca de",
        icon = Icons.Outlined.Info,
        iconSelected = Icons.Filled.Info
    )

    object EditProfile : Screen(
        route = "edit_profile/{profileId}",
        title = "Editar Perfil",
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
