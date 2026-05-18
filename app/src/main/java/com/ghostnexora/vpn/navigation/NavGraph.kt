package com.ghostnexora.vpn.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.ghostnexora.vpn.ui.screens.about.AboutScreen
import com.ghostnexora.vpn.ui.screens.documentation.DocumentationScreen
import com.ghostnexora.vpn.ui.screens.dashboard.DashboardScreen
import com.ghostnexora.vpn.ui.screens.importexport.ExportScreen
import com.ghostnexora.vpn.ui.screens.importexport.ImportScreen
import com.ghostnexora.vpn.ui.screens.logs.LogsScreen
import com.ghostnexora.vpn.ui.screens.profiles.CreateEditProfileScreen
import com.ghostnexora.vpn.ui.screens.profiles.ProfileListScreen
import com.ghostnexora.vpn.ui.screens.settings.SettingsScreen
import com.ghostnexora.vpn.ui.theme.*
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════
// NAVIGATION HOST
// ══════════════════════════════════════════════════════════════════════════

@Composable
fun GhostNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier,
        enterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 }
        },
        exitTransition = {
            fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 10 }
        },
        popEnterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 10 }
        },
        popExitTransition = {
            fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 10 }
        }
    ) {
        // ── Dashboard ──────────────────────────────────────────────────────
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToProfiles = {
                    navController.navigate(Screen.Profiles.route)
                }
            )
        }

        // ── Lista de perfiles ──────────────────────────────────────────────
        composable(Screen.Profiles.route) {
            ProfileListScreen(
                onBack = { navController.popBackStack() },
                onCreateNew = { navController.navigate(Screen.CreateProfile.route) },
                onEditProfile = { profileId ->
                    navController.navigate(Screen.EditProfile.createRoute(profileId))
                }
            )
        }

        // ── Crear perfil ───────────────────────────────────────────────────
        composable(Screen.CreateProfile.route) {
            CreateEditProfileScreen(
                profileId = null,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Editar perfil (con argumento) ──────────────────────────────────
        composable(
            route = Screen.EditProfile.route,
            arguments = listOf(
                navArgument(Screen.EditProfile.ARG_PROFILE_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments
                ?.getString(Screen.EditProfile.ARG_PROFILE_ID)
            CreateEditProfileScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Importar ───────────────────────────────────────────────────────
        composable(Screen.Import.route) {
            ImportScreen(onBack = { navController.popBackStack() })
        }

        // ── Exportar ───────────────────────────────────────────────────────
        composable(Screen.Export.route) {
            ExportScreen(onBack = { navController.popBackStack() })
        }

        // ── Historial (placeholder Fase 2) ────────────────────────────────
        composable(Screen.History.route) {
            HistoryPlaceholderScreen()
        }

        // ── Logs ───────────────────────────────────────────────────────────
        composable(Screen.Logs.route) {
            LogsScreen(onBack = { navController.popBackStack() })
        }

        // ── Ajustes ────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        // ── Documentación ───────────────────────────────────────────────────
        composable(Screen.Documentation.route) {
            DocumentationScreen()
        }

        // ── Acerca de ──────────────────────────────────────────────────────
        composable(Screen.About.route) {
            AboutScreen()
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// NAVIGATION DRAWER COMPLETO
// ══════════════════════════════════════════════════════════════════════════

@Composable
fun GhostNavigationDrawer(
    navController: NavHostController,
    drawerState: DrawerState,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = BackgroundDeep.copy(alpha = 0.7f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(Dimens.DrawerWidth),
                drawerContainerColor = SurfaceDark,
                drawerContentColor = TextPrimary
            ) {
                // ── Header del Drawer ──────────────────────────────────────
                DrawerHeader()

                NeonDivider(
                    modifier = Modifier.padding(horizontal = Dimens.SpaceLG),
                    color = BorderSubtle
                )

                Spacer(modifier = Modifier.height(Dimens.SpaceSM))

                // ── Items del drawer ───────────────────────────────────────
                Screen.drawerItems.forEach { screen ->

                    // Separador antes de Settings y About
                    if (screen == Screen.Settings) {
                        Spacer(modifier = Modifier.weight(1f))
                        NeonDivider(
                            modifier = Modifier.padding(horizontal = Dimens.SpaceLG),
                            color = BorderSubtle
                        )
                        Spacer(modifier = Modifier.height(Dimens.SpaceXS))
                    }

                    GhostDrawerItem(
                        screen = screen,
                        isSelected = currentRoute == screen.route,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Dashboard.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.SpaceMD))
            }
        },
        content = content
    )
}

// ══════════════════════════════════════════════════════════════════════════
// COMPONENTES DEL DRAWER
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun DrawerHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.DrawerHeaderHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0.12f),
                        SurfaceDark
                    )
                )
            )
            .padding(Dimens.SpaceXXL),
        contentAlignment = Alignment.BottomStart
    ) {
        Column {
            // Logo / icono
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(NeonCyan.copy(0.3f), NeonCyanGlow)
                        )
                    )
                    .neonGlow(NeonCyan, radius = 12.dp, alpha = 0.4f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceMD))

            Text(
                text = "Ghost Nexora",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Text(
                text = "VPN Manager v1.0",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = NeonCyanDim,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

@Composable
private fun GhostDrawerItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) NeonCyan.copy(alpha = 0.1f) else TextPrimary.copy(alpha = 0f)
    val contentColor = if (isSelected) NeonCyan else TextSecondary
    val icon = if (isSelected) screen.iconSelected else screen.icon

    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = screen.title,
                modifier = Modifier.size(Dimens.DrawerIconSize),
                tint = contentColor
            )
        },
        label = {
            Text(
                text = screen.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = contentColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            )
        },
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceXXS),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = bgColor,
            unselectedContainerColor = TextPrimary.copy(alpha = 0f),
            selectedIconColor = NeonCyan,
            unselectedIconColor = TextSecondary,
            selectedTextColor = NeonCyan,
            unselectedTextColor = TextSecondary
        )
    )
}

// ══════════════════════════════════════════════════════════════════════════
// PLACEHOLDER — HISTORIAL (Fase 2)
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun HistoryPlaceholderScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceLG))
            Text(
                text = "Historial",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
            Text(
                text = "Disponible en Fase 2",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}
