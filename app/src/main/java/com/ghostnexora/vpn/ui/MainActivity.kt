package com.ghostnexora.vpn.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ghostnexora.vpn.navigation.GhostNavHost
import com.ghostnexora.vpn.navigation.GhostNavigationDrawer
import com.ghostnexora.vpn.navigation.Screen
import com.ghostnexora.vpn.ui.components.UpdateDialog
import com.ghostnexora.vpn.ui.screens.settings.SettingsViewModel
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.GhostNexoraTheme
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.SurfaceDark
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.neonGlow
import com.ghostnexora.vpn.update.UpdateViewModel
import com.ghostnexora.vpn.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GhostNexoraApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.uiState.collectAsState()

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val currentTitle = screenTitle(currentRoute)

    SensitiveWindowProtection(currentRoute)

    NativePermissionBootstrap(
        enabled = settingsState.initialized && settingsState.firstLaunch,
        settingsViewModel = settingsViewModel
    )

    LaunchedEffect(settingsState.initialized, settingsState.firstLaunch) {
        if (settingsState.initialized && !settingsState.firstLaunch) {
            updateViewModel.checkForUpdates(force = false)
        }
    }

    LaunchedEffect(updateState.message, updateState.error) {
        val text = updateState.error ?: updateState.message
        if (!text.isNullOrBlank()) {
            snackbarHostState.showSnackbar(text)
            updateViewModel.clearTransientMessage()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingsViewModel.refreshPermissions()
                updateViewModel.resumePendingInstall()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (updateState.available && !updateState.dismissed) {
        UpdateDialog(
            state = updateState,
            onDismiss = updateViewModel::dismissUpdatePrompt,
            onUpdateNow = updateViewModel::downloadAndInstall
        )
    }

    GhostNavigationDrawer(
        navController = navController,
        drawerState = drawerState
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                GhostTopBar(
                    title = currentTitle,
                    onMenuClick = {
                        coroutineScope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    }
                )
            },
            containerColor = BackgroundDark,
            contentColor = TextPrimary
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

@Composable
private fun SensitiveWindowProtection(route: String?) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val sensitive = route == Screen.Profiles.route ||
        route == Screen.CreateProfile.route ||
        route?.startsWith("edit_profile") == true ||
        route == Screen.Import.route ||
        route == Screen.Export.route

    DisposableEffect(activity, sensitive) {
        if (sensitive) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (sensitive) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Requests only permissions required for normal operation using Android's own
 * dialogs. Overlay and unknown-source access remain contextual and can be
 * opened from Settings or when their feature is used.
 */
@Composable
private fun NativePermissionBootstrap(
    enabled: Boolean,
    settingsViewModel: SettingsViewModel
) {
    if (!enabled) return

    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        settingsViewModel.refreshPermissions()
        step = 1
    }
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        settingsViewModel.refreshPermissions()
        step = 2
    }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        settingsViewModel.refreshPermissions()
        step = 3
    }

    LaunchedEffect(enabled, step) {
        if (!enabled) return@LaunchedEffect
        when (step) {
            0 -> {
                val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                if (needsNotification) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    step = 1
                }
            }

            1 -> {
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) vpnLauncher.launch(vpnIntent) else step = 2
            }

            2 -> {
                if (PermissionHelper.isBatteryOptimizationIgnored(context)) {
                    step = 3
                } else {
                    runCatching {
                        batteryLauncher.launch(PermissionHelper.batteryOptimizationIntent(context))
                    }.onFailure { step = 3 }
                }
            }

            else -> settingsViewModel.completeFirstLaunch()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GhostTopBar(
    title: String,
    onMenuClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "VPN protegida · túnel cifrado",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Abrir navegación",
                    tint = NeonCyan
                )
            }
        },
        actions = {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.padding(end = 16.dp)
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = SurfaceDark,
            scrolledContainerColor = SurfaceDark,
            navigationIconContentColor = NeonCyan,
            titleContentColor = TextPrimary,
            actionIconContentColor = NeonCyan
        ),
        modifier = Modifier.neonGlow(NeonCyan, radius = 4.dp, alpha = 0.08f)
    )
}

private fun screenTitle(route: String?): String = when {
    route == null -> "Ghost Nexora VPN"
    route == Screen.Dashboard.route -> "Inicio"
    route == Screen.Profiles.route -> "Perfiles VPN"
    route == Screen.CreateProfile.route -> "Nuevo perfil"
    route.startsWith("edit_profile") -> "Editar perfil"
    route == Screen.AppRouting.route -> "Enrutamiento de aplicaciones"
    route == Screen.Compatibility.route -> "Compatibilidad"
    route == Screen.Import.route -> "Importar perfiles"
    route == Screen.Export.route -> "Exportar perfiles"
    route == Screen.History.route -> "Historial"
    route == Screen.Logs.route -> "Registros"
    route == Screen.Settings.route -> "Ajustes"
    route == Screen.Documentation.route -> "Documentación"
    route == Screen.About.route -> "Acerca de"
    else -> "Ghost Nexora VPN"
}
