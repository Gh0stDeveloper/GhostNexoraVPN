package com.ghostnexora.vpn.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ghostnexora.vpn.navigation.GhostNavigationDrawer
import com.ghostnexora.vpn.ui.components.UpdateDialog
import com.ghostnexora.vpn.update.UpdateViewModel
import com.ghostnexora.vpn.ui.screens.settings.SettingsViewModel
import com.ghostnexora.vpn.ui.screens.onboarding.FirstLaunchPermissionsScreen
import com.ghostnexora.vpn.navigation.GhostNavHost
import com.ghostnexora.vpn.navigation.Screen
import com.ghostnexora.vpn.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GhostNexoraTheme {
                GhostNexoraApp()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// ROOT COMPOSABLE
// ══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GhostNexoraApp() {
    val navController     = rememberNavController()
    val drawerState       = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope    = rememberCoroutineScope()

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()

    val currentBackStack  by navController.currentBackStackEntryAsState()
    val currentRoute      = currentBackStack?.destination?.route
    val currentTitle      = screenTitle(currentRoute)

    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.uiState.collectAsState()

    LaunchedEffect(settingsState.firstLaunch) {
        if (!settingsState.firstLaunch) {
            updateViewModel.checkForUpdates(force = true)
        }
    }

    if (!settingsState.firstLaunch && updateState.available && !updateState.dismissed) {
        UpdateDialog(
            state = updateState,
            onDismiss = { updateViewModel.dismissUpdatePrompt() },
            onUpdateNow = { updateViewModel.downloadAndInstall() }
        )
    }

    if (settingsState.firstLaunch) {
        FirstLaunchPermissionsScreen(
            onComplete = {
                updateViewModel.checkForUpdates(force = true)
            }
        )
        return
    }

    GhostNavigationDrawer(
        navController = navController,
        drawerState   = drawerState
    ) {
        Scaffold(
            topBar = {
                GhostTopBar(
                    title       = currentTitle,
                    onMenuClick = {
                        coroutineScope.launch {
                            if (drawerState.isClosed) drawerState.open()
                            else drawerState.close()
                        }
                    },
                )
            },
            containerColor = BackgroundDark,
            contentColor   = TextPrimary
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundDark)
                    .padding(paddingValues)
            ) {
                GhostNavHost(
                    navController = navController,
                    onCheckUpdates = { updateViewModel.checkForUpdates(force = true) }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// TOP APP BAR
// ══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GhostTopBar(
    title: String,
    onMenuClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text  = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector        = Icons.Filled.Menu,
                    contentDescription = "Menú",
                    tint               = NeonCyan
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor              = SurfaceDark,
            scrolledContainerColor      = SurfaceDark,
            navigationIconContentColor  = NeonCyan,
            titleContentColor           = TextPrimary,
            actionIconContentColor      = NeonCyan
        ),
        modifier = Modifier.neonGlow(NeonCyan, radius = 4.dp, alpha = 0.08f)
    )
}

// ══════════════════════════════════════════════════════════════════════════
// HELPER — Título por ruta
// ══════════════════════════════════════════════════════════════════════════

private fun screenTitle(route: String?): String = when {
    route == null                        -> "Ghost Nexora VPN"
    route == Screen.Dashboard.route      -> "Dashboard"
    route == Screen.Profiles.route       -> "Perfiles VPN"
    route == Screen.CreateProfile.route  -> "Nuevo Perfil"
    route.startsWith("edit_profile")     -> "Editar Perfil"
    route == Screen.Import.route         -> "Importar Perfiles"
    route == Screen.Export.route         -> "Exportar Perfiles"
    route == Screen.History.route        -> "Historial"
    route == Screen.Logs.route           -> "Registros"
    route == Screen.Settings.route       -> "Ajustes"
    route == Screen.About.route          -> "Acerca de"
    else                                 -> "Ghost Nexora VPN"
}
