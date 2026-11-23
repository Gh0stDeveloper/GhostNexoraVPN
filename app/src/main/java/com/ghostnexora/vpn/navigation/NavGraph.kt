package com.ghostnexora.vpn.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.ghostnexora.vpn.R
import com.ghostnexora.vpn.ui.screens.about.AboutScreen
import com.ghostnexora.vpn.ui.screens.approuting.AppRoutingScreen
import com.ghostnexora.vpn.ui.screens.compatibility.CompatibilityScreen
import com.ghostnexora.vpn.ui.screens.history.HistoryScreen
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

@Composable
fun GhostNavHost(
    navController: NavHostController,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier,
    dashboardLogsExpanded: Boolean = false,
    onDashboardLogsExpanded: (Boolean) -> Unit = {}
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
        composable(Screen.Dashboard.route) { entry ->
            DashboardScreen(
                lifecycleOwner = entry,
                logsExpanded = dashboardLogsExpanded,
                onLogsExpanded = onDashboardLogsExpanded,
                onNavigateToProfiles = {
                    navController.navigate(Screen.Profiles.route)
                }
            )
        }

        composable(Screen.Profiles.route) {
            ProfileListScreen(
                onBack = { navController.popBackStack() },
                onCreateNew = { navController.navigate(Screen.CreateProfile.route) },
                onEditProfile = { profileId ->
                    navController.navigate(Screen.EditProfile.createRoute(profileId))
                }
            )
        }

        composable(Screen.CreateProfile.route) {
            CreateEditProfileScreen(
                profileId = null,
                onBack = { navController.popBackStack() }
            )
        }

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

        composable(Screen.AppRouting.route) {
            AppRoutingScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Compatibility.route) {
            CompatibilityScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Import.route) {
            ImportScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Export.route) {
            ExportScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.History.route) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Logs.route) {
            LogsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onCheckUpdates = onCheckUpdates
            )
        }

        composable(Screen.Documentation.route) {
            DocumentationScreen()
        }

        composable(Screen.About.route) {
            AboutScreen()
        }
    }
}

@Composable
fun GhostNavigationDrawer(
    navController: NavHostController,
    drawerState: DrawerState,
    gesturesEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        scrimColor = BackgroundDeep.copy(alpha = 0.7f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(Dimens.DrawerWidth),
                drawerContainerColor = SurfaceDark,
                drawerContentColor = TextPrimary
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Dimens.SpaceMD)) {
                    DrawerHeader(onClose = { coroutineScope.launch { drawerState.close() } })
                    val groups = listOf(
                        R.string.drawer_connection to listOf(Screen.Dashboard, Screen.Profiles, Screen.CreateProfile, Screen.AppRouting),
                        R.string.drawer_files to listOf(Screen.Import, Screen.Export),
                        R.string.drawer_activity to listOf(Screen.History, Screen.Logs),
                        R.string.drawer_system to listOf(Screen.Settings, Screen.Compatibility, Screen.Documentation, Screen.About)
                    )
                    groups.forEach { (label, screens) ->
                        Text(
                            stringResource(label),
                            modifier = Modifier.padding(horizontal = Dimens.SpaceXL, vertical = Dimens.SpaceSM),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        screens.forEach { screen ->
                            GhostDrawerItem(
                                screen = screen,
                                isSelected = currentRoute == screen.route,
                                onClick = {
                                    coroutineScope.launch { drawerState.close() }
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(Screen.Dashboard.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        content = content
    )
}

@Composable
private fun DrawerHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(NeonGreen.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Shield, null, tint = NeonGreen, modifier = Modifier.size(21.dp))
        }
        Text(
            stringResource(R.string.app_name),
            modifier = Modifier.weight(1f).padding(start = Dimens.SpaceMD),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, stringResource(R.string.drawer_close))
        }
    }
    HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(horizontal = Dimens.SpaceMD))
}

@Composable
private fun GhostDrawerItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) NeonGreen.copy(alpha = 0.12f) else TextPrimary.copy(alpha = 0f)
    val contentColor = if (isSelected) NeonGreen else TextSecondary
    val icon = if (isSelected) screen.iconSelected else screen.icon

    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.DrawerIconSize),
                tint = contentColor
            )
        },
        label = {
            Text(
                text = stringResource(screen.title),
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