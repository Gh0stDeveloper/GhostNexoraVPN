package com.ghostnexora.vpn.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.annotation.Nullable;

import com.ghostnexora.vpn.BuildConfig;
import com.ghostnexora.vpn.GhostNexoraApp;
import com.ghostnexora.vpn.core.VpnRuntimeStateStore;
import com.ghostnexora.vpn.data.model.AppRoutingMode;
import com.ghostnexora.vpn.data.model.AppRoutingPreferences;
import com.ghostnexora.vpn.data.model.ConnectionMode;
import com.ghostnexora.vpn.data.model.LogLevel;
import com.ghostnexora.vpn.data.model.NetworkPreferences;
import com.ghostnexora.vpn.data.model.VpnConnectionState;
import com.ghostnexora.vpn.data.model.VpnProfile;
import com.ghostnexora.vpn.data.model.VpnTrafficStats;
import com.ghostnexora.vpn.data.repository.ProfileRepository;
import com.ghostnexora.vpn.data.repository.VpnRepositoryBridge;
import com.ghostnexora.vpn.tunnel.OutboundCheck;
import com.ghostnexora.vpn.tunnel.OutboundSocketProtection;
import com.ghostnexora.vpn.tunnel.TunnelLogEvent;
import com.ghostnexora.vpn.tunnel.TunnelLogEventParser;
import com.ghostnexora.vpn.tunnel.TunnelManager;
import com.ghostnexora.vpn.tunnel.TunnelRuntime;
import com.ghostnexora.vpn.tunnel.XrayTrafficDelta;
import com.ghostnexora.vpn.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import kotlin.Unit;
import kotlinx.coroutines.flow.StateFlow;

/**
 * Android VPN service implemented in Java.
 *
 * This class owns the VPN lifecycle and all Android data-plane primitives:
 * VpnService.Builder, TUN descriptor, underlying physical network selection,
 * VpnService.protect(Socket), transport start/stop, recovery and health work.
 * Kotlin remains in UI/models/persistence, but no Kotlin Service owns the VPN.
 */
@AndroidEntryPoint
public final class GhostVpnService extends VpnService {
    public static final String ACTION_CONNECT = "com.ghostnexora.vpn.CONNECT";
    public static final String ACTION_DISCONNECT = "com.ghostnexora.vpn.DISCONNECT";
    public static final String EXTRA_PROFILE_ID = "extra_profile_id";

    private static final long VPN_REGISTRATION_TIMEOUT_MS = 5_000L;
    private static final long DATA_PLANE_VERIFICATION_TIMEOUT_MS = 15_000L;
    private static final long[] RECONNECT_DELAYS = {1_000L, 2_000L, 5_000L, 10_000L, 30_000L};

    public static final StateFlow<VpnConnectionState> connectionState =
            VpnRuntimeStateStore.getConnectionState();
    public static final StateFlow<VpnTrafficStats> trafficStats =
            VpnRuntimeStateStore.getTrafficStats();

    @Inject
    public ProfileRepository repository;

    private final ExecutorService serviceExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ghost-vpn-service");
        thread.setDaemon(false);
        return thread;
    });
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ghost-vpn-monitor");
        thread.setDaemon(true);
        return thread;
    });
    /* Native core callbacks run inline before startLoop() returns; persistence must not run there. */
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ghost-vpn-log-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final Object tunnelLock = new Object();

    private VpnRepositoryBridge repositoryBridge;
    private ConnectivityManager connectivityManager;
    private TunnelManager tunnelManager;
    private ParcelFileDescriptor tunInterface;
    private TunnelRuntime tunnelRuntime;
    private VpnProfile activeProfile;
    private NetworkPreferences activeNetworkPreferences;
    private volatile Network underlyingNetwork;
    private volatile Network activeVpnNetwork;
    private volatile boolean physicalNetworkAvailable;
    private volatile String physicalNetworkType = "Sin red";
    private volatile boolean intentionalDisconnect;
    private volatile boolean destroyed;
    private volatile long sessionConnectedSince;
    private volatile long sessionReceivedBytes;
    private volatile long sessionSentBytes;
    private volatile long verifiedLatencyMs;
    private volatile int reconnectCount;
    private volatile int reconnectFailureCount;
    private volatile long dataPlaneVerificationGeneration;

    private volatile Future<?> dataPlaneVerificationTask;
    private volatile Future<?> dataPlaneTimeoutTask;
    private Future<?> healthTask;
    private Future<?> statsTask;
    private Future<?> reconnectTask;

    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    registerUnderlyingNetwork(network);
                    submit(() -> {
                        log(LogLevel.INFO, "Red física disponible: " + physicalNetworkType,
                                profileId(), "NETWORK");
                        if (VpnRuntimeStateStore.currentConnectionState()
                                instanceof VpnConnectionState.Reconnecting) {
                            triggerReconnect("La red física volvió a estar disponible");
                        }
                    });
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                        return;
                    }
                    physicalNetworkAvailable =
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    physicalNetworkType = networkType(capabilities);
                    underlyingNetwork = network;
                    try {
                        setUnderlyingNetworks(new Network[]{network});
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void onLost(Network network) {
                    submit(() -> handlePhysicalNetworkLost(network));
                }
            };

    public static void updateState(VpnConnectionState state) {
        VpnRuntimeStateStore.publishConnectionState(state);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        repositoryBridge = new VpnRepositoryBridge(repository);
        connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        tunnelManager = new TunnelManager(getApplicationContext(), status -> {
            TunnelLogEvent event = TunnelLogEventParser.INSTANCE.parse(status);
            if (event != null) {
                log(event.getLevel(), status, profileId(), event.getTag());
            }
            return Unit.INSTANCE;
        });
        OutboundSocketProtection.install(this::protect);

        Network initial = findUsablePhysicalNetwork(null);
        if (initial != null) {
            registerUnderlyingNetwork(initial);
        }
        registerPhysicalNetworkCallback();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_CONNECT.equals(action)) {
            startPreparingForeground("Preparando conexión");
            String profileId = intent.getStringExtra(EXTRA_PROFILE_ID);
            submit(() -> {
                if (profileId == null || profileId.trim().isEmpty()) {
                    repositoryBridge.setVpnDesiredConnected(false);
                    publishState(new VpnConnectionState.Error("Sin perfil especificado", ""));
                    log(LogLevel.ERROR, "No se especificó un perfil", null, "VPN");
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf();
                    return;
                }
                repositoryBridge.setVpnDesiredConnected(true);
                repositoryBridge.resetVpnRecovery();
                handleConnect(profileId);
            });
        } else if (ACTION_DISCONNECT.equals(action)) {
            submit(() -> {
                repositoryBridge.setVpnDesiredConnected(false);
                handleDisconnect();
            });
        } else if (VpnServiceContract.ACTION_QUERY_RUNTIME.equals(action)) {
            submit(() -> handleRuntimeQuery(startId));
        } else {
            startPreparingForeground("Restaurando sesión");
            submit(this::handleSystemRestart);
        }
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        submit(() -> {
            log(LogLevel.WARNING, "Permiso VPN revocado por Android", profileId(), "VPN");
            handleDisconnect();
        });
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        intentionalDisconnect = true;
        cancelTask(reconnectTask);
        cancelDataPlaneVerification();
        cancelTask(healthTask);
        cancelTask(statsTask);
        cleanupTunnel(true);
        OutboundSocketProtection.clear();
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Throwable ignored) {
        }
        try {
            setUnderlyingNetworks(null);
        } catch (Throwable ignored) {
        }
        scheduler.shutdownNow();
        serviceExecutor.shutdownNow();
        logExecutor.shutdown();
        super.onDestroy();
    }

    private void handleConnect(String profileId) {
        VpnProfile profile;
        try {
            profile = repositoryBridge.getProfileForConnection(profileId);
        } catch (Throwable error) {
            failStartup(profileId, null, safeMessage(error, "No se pudo abrir el perfil protegido"));
            return;
        }
        if (profile == null) {
            failStartup(profileId, null, "Perfil no encontrado");
            return;
        }

        intentionalDisconnect = false;
        cancelTask(reconnectTask);
        cancelDataPlaneVerification();
        cancelTask(healthTask);
        cancelTask(statsTask);
        cleanupTunnel(true);
        activeProfile = profile;
        reconnectCount = 0;
        reconnectFailureCount = 0;
        verifiedLatencyMs = 0L;
        sessionConnectedSince = 0L;
        publishState(new VpnConnectionState.Connecting(profile.getName()));
        startForeground(
                GhostNexoraApp.NOTIF_ID_VPN,
                VpnNotificationHelper.build(this,
                        new VpnConnectionState.Connecting(profile.getName()))
        );

        try {
            validateProfile(profile);
            ensurePhysicalNetwork();
            NetworkPreferences preferences = repositoryBridge.networkPreferences();
            AppRoutingPreferences appRouting = repositoryBridge.appRoutingPreferences();
            if (!appRouting.isValid()) {
                throw new IllegalStateException(
                        "App-routing invalid: select at least one selected application [APP-ROUTE-001]"
                );
            }
            activeNetworkPreferences = preferences;
            log(LogLevel.INFO,
                    "Red VPN: " + preferences.getIpMode().getLabel() + " · MTU " +
                            preferences.getValidatedMtu() + " · " + preferences.getDnsMode().getLabel(),
                    profile.getId(), "SETTINGS");
            log(LogLevel.INFO, "Iniciando " + profile.getConnectionModeLabel(), profile.getId(), "VPN");
            logConnectionSnapshot(profile);

            ParcelFileDescriptor tun = buildTunInterface(profile, preferences, appRouting);
            if (tun == null) {
                throw new IllegalStateException("Android no pudo establecer la interfaz VPN");
            }
            tunInterface = tun;
            Network network = underlyingNetwork;
            if (network != null) {
                try {
                    setUnderlyingNetworks(new Network[]{network});
                } catch (Throwable ignored) {
                }
            }
            log(LogLevel.INFO,
                    "TUN activo · MTU " + preferences.getValidatedMtu() + " · " +
                            preferences.getIpMode().getLabel() + " · bypass propio aplicado",
                    profile.getId(), "NETWORK");

            synchronized (tunnelLock) {
                tunnelRuntime = tunnelManager.start(profile, tun.getFd(), preferences);
            }
            activeVpnNetwork = awaitAndroidVpnRegistration(profile);
            if (!tunnelManager.isAlive(tunnelRuntime)) {
                throw new IllegalStateException("El transporte se cerró antes de activar la VPN del sistema");
            }
            beginDataPlaneVerification(profile, tunnelRuntime, false, 0);
        } catch (Throwable error) {
            String message = friendlyConnectionError(error, profile);
            cleanupTunnel(true);
            activeProfile = null;
            repositoryBridge.setVpnDesiredConnected(false);
            VpnConnectionState.Error state = new VpnConnectionState.Error(message, profile.getName());
            publishState(state);
            VpnNotificationHelper.update(this, state);
            log(LogLevel.ERROR, message, profile.getId(), "VPN");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void handleDisconnect() {
        intentionalDisconnect = true;
        repositoryBridge.setVpnDesiredConnected(false);
        repositoryBridge.resetVpnRecovery();
        cancelTask(reconnectTask);
        cancelDataPlaneVerification();
        cancelTask(healthTask);
        cancelTask(statsTask);
        String profileId = profileId();

        publishState(VpnConnectionState.Disconnecting.INSTANCE);
        VpnNotificationHelper.update(this, VpnConnectionState.Disconnecting.INSTANCE);
        log(LogLevel.INFO, "Cerrando túnel y sesión de transporte", profileId, "VPN");

        cleanupTunnel(true);
        stopService(new Intent(this, FloatingWindowService.class));
        activeProfile = null;
        sessionConnectedSince = 0L;
        publishTraffic(emptyTraffic());
        publishState(VpnConnectionState.Disconnected.INSTANCE);
        log(LogLevel.SUCCESS, "VPN desconectada", profileId, "VPN");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void handleSystemRestart() {
        String profileId = repositoryBridge.activeProfileId();
        boolean shouldReconnect = repositoryBridge.autoReconnect()
                && repositoryBridge.vpnDesiredConnected();
        if (shouldReconnect && profileId != null && !profileId.trim().isEmpty()) {
            Integer recoveryAttempt = repositoryBridge.claimVpnRecoveryAttempt();
            if (recoveryAttempt == null) {
                String message = "El motor VPN se reinició demasiadas veces; reconexión automática detenida";
                repositoryBridge.setVpnDesiredConnected(false);
                publishState(new VpnConnectionState.Error(message, ""));
                log(LogLevel.ERROR, message + " [CORE-RECOVERY-003]", null, "CORE");
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return;
            }
            log(LogLevel.WARNING,
                    "Restaurando VPN después del reinicio del proceso nativo · intento " +
                            recoveryAttempt + "/3", null, "CORE");
            handleConnect(profileId);
        } else {
            publishState(VpnConnectionState.Disconnected.INSTANCE);
            publishTraffic(emptyTraffic());
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void handleRuntimeQuery(int startId) {
        broadcastCurrentRuntime();
        if (activeProfile != null
                || !(VpnRuntimeStateStore.currentConnectionState()
                instanceof VpnConnectionState.Disconnected)) {
            return;
        }
        if (repositoryBridge.vpnDesiredConnected()) {
            startPreparingForeground("Recuperando motor VPN");
            handleSystemRestart();
        } else {
            stopSelfResult(startId);
        }
    }

    private void triggerReconnect(String reason) {
        if (intentionalDisconnect || activeProfile == null || tunInterface == null || destroyed) {
            return;
        }
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }
        reconnectTask = serviceExecutor.submit(() -> {
            try {
                reconnectLoop(reason);
            } finally {
                reconnecting.set(false);
            }
        });
    }

    private void reconnectLoop(String reason) {
        VpnProfile profile = activeProfile;
        if (profile == null) {
            return;
        }
        cancelDataPlaneVerification();
        cancelTask(healthTask);
        synchronized (tunnelLock) {
            tunnelManager.stop(tunnelRuntime);
            tunnelRuntime = null;
        }

        boolean autoReconnect = repositoryBridge.autoReconnect();
        boolean killSwitch = repositoryBridge.killSwitch();
        if (!autoReconnect) {
            if (killSwitch) {
                String message = "Conexión perdida. Kill Switch mantiene el tráfico bloqueado.";
                VpnConnectionState.Error state = new VpnConnectionState.Error(message, profile.getName());
                publishState(state);
                VpnNotificationHelper.update(this, state);
                log(LogLevel.WARNING, reason + " · " + message, profile.getId(), "NETWORK");
            } else {
                cleanupTunnel(true);
                publishState(new VpnConnectionState.Error("Conexión perdida", profile.getName()));
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
            return;
        }

        NetworkPreferences preferences = repositoryBridge.networkPreferences();
        activeNetworkPreferences = preferences;
        int maxAttempts = preferences.getValidatedReconnectAttempts();
        log(LogLevel.WARNING, reason + " · iniciando reconexión protegida · máximo " + maxAttempts,
                profile.getId(), "NETWORK");

        while (!destroyed && !intentionalDisconnect && tunInterface != null
                && reconnectFailureCount < maxAttempts) {
            int attemptNumber = reconnectFailureCount + 1;
            int delayIndex = Math.max(0, attemptNumber - 1);
            long baseDelay = RECONNECT_DELAYS[Math.min(delayIndex, RECONNECT_DELAYS.length - 1)];
            long waitMs = baseDelay + ((delayIndex * 173L) % 650L);
            VpnConnectionState.Reconnecting state =
                    new VpnConnectionState.Reconnecting(profile.getName(), attemptNumber, waitMs);
            publishState(state);
            VpnNotificationHelper.update(this, state);

            if (!physicalNetworkAvailable) {
                Network replacement = findUsablePhysicalNetwork(null);
                if (replacement != null) {
                    registerUnderlyingNetwork(replacement);
                }
                if (!physicalNetworkAvailable) {
                    log(LogLevel.WARNING,
                            "Esperando que vuelva la red física antes del intento " + attemptNumber,
                            profile.getId(), "NETWORK");
                    return;
                }
            }

            sleep(waitMs);
            if (intentionalDisconnect || tunInterface == null) {
                return;
            }

            try {
                synchronized (tunnelLock) {
                    tunnelManager.stop(tunnelRuntime);
                    tunnelRuntime = null;
                    if (tunInterface == null) {
                        throw new IllegalStateException("TUN no disponible durante reconexión");
                    }
                    tunnelRuntime = tunnelManager.start(profile, tunInterface.getFd(), preferences);
                }
                activeVpnNetwork = awaitAndroidVpnRegistration(profile);
                if (tunnelManager.isAlive(tunnelRuntime)) {
                    beginDataPlaneVerification(profile, tunnelRuntime, true, attemptNumber);
                    return;
                }
                throw new IllegalStateException(
                        "El transporte se cerró antes de validar la reconexión"
                );
            } catch (Throwable error) {
                reconnectFailureCount = attemptNumber;
                log(LogLevel.WARNING,
                        "Intento " + attemptNumber + "/" + maxAttempts + " · " +
                                safeMessage(error, "falló"),
                        profile.getId(), "NETWORK");
            }
        }

        if (!destroyed && !intentionalDisconnect && tunInterface != null) {
            finishReconnectExhausted(profile, maxAttempts, killSwitch);
        }
    }

    private void finishReconnectExhausted(
            VpnProfile profile,
            int maxAttempts,
            boolean killSwitch
    ) {
        cancelDataPlaneVerification();
        cancelTask(healthTask);
        cancelTask(statsTask);
        String exhausted = "Reconnect attempts exhausted (" + maxAttempts + ") [RECONNECT-408]";
        if (killSwitch) {
            VpnConnectionState.Error state = new VpnConnectionState.Error(exhausted, profile.getName());
            publishState(state);
            VpnNotificationHelper.update(this, state);
            log(LogLevel.ERROR, exhausted + " · Kill Switch keeps traffic blocked", profile.getId(), "NETWORK");
        } else {
            cleanupTunnel(true);
            activeProfile = null;
            sessionConnectedSince = 0L;
            repositoryBridge.setVpnDesiredConnected(false);
            publishTraffic(emptyTraffic());
            publishState(new VpnConnectionState.Error(exhausted, profile.getName()));
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    /**
     * Keeps the UI in Connecting/Reconnecting until the active Xray outbound
     * proves bidirectional Internet access. The check reuses the existing core,
     * SOCKS bridge and SSH session; it never establishes a second VPN or SSH
     * login. Completion is posted back to the serialized service executor.
     */
    private void beginDataPlaneVerification(
            VpnProfile profile,
            TunnelRuntime expectedRuntime,
            boolean afterReconnect,
            int reconnectAttempt
    ) {
        cancelDataPlaneVerification();
        long generation = ++dataPlaneVerificationGeneration;
        Network verificationNetwork = activeVpnNetwork;
        if (verificationNetwork == null) {
            failDataPlaneVerification(
                    profile,
                    expectedRuntime,
                    generation,
                    afterReconnect,
                    reconnectAttempt,
                    new IllegalStateException("La red VPN de Android no está disponible")
            );
            return;
        }

        dataPlaneTimeoutTask = scheduler.schedule(
                () -> submit(() -> failDataPlaneVerification(
                        profile,
                        expectedRuntime,
                        generation,
                        afterReconnect,
                        reconnectAttempt,
                        new IllegalStateException(
                                "La validación de la ruta de datos superó " +
                                        DATA_PLANE_VERIFICATION_TIMEOUT_MS / 1_000L + " segundos"
                        )
                )),
                DATA_PLANE_VERIFICATION_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
        );
        dataPlaneVerificationTask = scheduler.submit(() -> {
            try {
                OutboundCheck check = tunnelManager.verifyActiveDataPlane(verificationNetwork);
                submit(() -> completeDataPlaneVerification(
                        profile,
                        expectedRuntime,
                        generation,
                        afterReconnect,
                        reconnectAttempt,
                        check
                ));
            } catch (Throwable error) {
                submit(() -> failDataPlaneVerification(
                        profile,
                        expectedRuntime,
                        generation,
                        afterReconnect,
                        reconnectAttempt,
                        error
                ));
            }
        });
    }

    private void completeDataPlaneVerification(
            VpnProfile profile,
            TunnelRuntime expectedRuntime,
            long generation,
            boolean afterReconnect,
            int reconnectAttempt,
            OutboundCheck check
    ) {
        if (!isCurrentDataPlaneVerification(profile, expectedRuntime, generation)) {
            return;
        }
        Network vpn = activeVpnNetwork;
        if (vpn == null || !isExpectedOwnedVpnNetwork(vpn)) {
            failDataPlaneVerification(
                    profile,
                    expectedRuntime,
                    generation,
                    afterReconnect,
                    reconnectAttempt,
                    new IllegalStateException(
                            "Android retiró la red VPN antes de terminar la validación"
                    )
            );
            return;
        }
        if (!tunnelManager.isAlive(expectedRuntime)) {
            failDataPlaneVerification(
                    profile,
                    expectedRuntime,
                    generation,
                    afterReconnect,
                    reconnectAttempt,
                    new IllegalStateException(
                            "El transporte se cerró durante la validación de la ruta de datos"
                    )
            );
            return;
        }
        if (!claimDataPlaneVerification(profile, expectedRuntime, generation)) {
            return;
        }

        verifiedLatencyMs = Math.max(0L, check.getLatencyMs());
        reconnectFailureCount = 0;
        if (afterReconnect) {
            reconnectCount++;
        }
        repositoryBridge.markLastUsed(profile.getId());
        repositoryBridge.resetVpnRecovery();
        sessionConnectedSince = System.currentTimeMillis();
        VpnConnectionState.Connected connected = connectedState(profile);
        publishState(connected);
        VpnNotificationHelper.update(this, connected);
        log(LogLevel.SUCCESS,
                afterReconnect
                        ? "Ruta de datos restablecida en intento " + reconnectAttempt +
                                " · estado Conectado publicado"
                        : "VPN, TUN y ruta de datos real verificados · estado Conectado publicado",
                profile.getId(), "VPN");
        resetTrafficBaseline(profile);
        startStatsTicker(profile);
        startHealthMonitor(profile);
        maybeStartFloatingWindow();
    }

    private void failDataPlaneVerification(
            VpnProfile profile,
            TunnelRuntime expectedRuntime,
            long generation,
            boolean afterReconnect,
            int reconnectAttempt,
            Throwable error
    ) {
        if (!claimDataPlaneVerification(profile, expectedRuntime, generation)) {
            return;
        }
        String detail = safeMessage(error, "sin respuesta del outbound");
        String message = "La interfaz VPN se creó, pero la ruta de datos no entregó " +
                "una respuesta real: " + detail + " [ROUTE-DATA-204]";

        if (!afterReconnect) {
            cleanupTunnel(true);
            activeProfile = null;
            repositoryBridge.setVpnDesiredConnected(false);
            VpnConnectionState.Error state = new VpnConnectionState.Error(
                    message + " [" + profile.getConnectionModeLabel() + "]",
                    profile.getName()
            );
            publishState(state);
            VpnNotificationHelper.update(this, state);
            log(LogLevel.ERROR, message, profile.getId(), "VPN");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }

        synchronized (tunnelLock) {
            if (tunnelRuntime == expectedRuntime) {
                tunnelManager.stop(tunnelRuntime);
                tunnelRuntime = null;
            }
        }
        reconnectFailureCount = Math.max(reconnectFailureCount, reconnectAttempt);
        int maxAttempts = activeNetworkPreferences != null
                ? activeNetworkPreferences.getValidatedReconnectAttempts()
                : repositoryBridge.networkPreferences().getValidatedReconnectAttempts();
        log(LogLevel.WARNING,
                "Intento " + reconnectAttempt + "/" + maxAttempts +
                        " sin ruta de datos · " + detail,
                profile.getId(), "NETWORK");
        if (repositoryBridge.autoReconnect()
                && reconnectFailureCount < maxAttempts
                && tunInterface != null
                && !intentionalDisconnect) {
            triggerReconnect("La ruta de datos no respondió");
        } else {
            finishReconnectExhausted(profile, maxAttempts, repositoryBridge.killSwitch());
        }
    }

    private boolean isCurrentDataPlaneVerification(
            VpnProfile profile,
            TunnelRuntime expectedRuntime,
            long generation
    ) {
        VpnProfile currentProfile = activeProfile;
        ParcelFileDescriptor tun = tunInterface;
        return !destroyed
                && !intentionalDisconnect
                && generation == dataPlaneVerificationGeneration
                && currentProfile != null
                && currentProfile.getId().equals(profile.getId())
                && tunnelRuntime == expectedRuntime
                && tun != null
                && tun.getFileDescriptor().valid();
    }

    private boolean claimDataPlaneVerification(
            VpnProfile profile,
            TunnelRuntime expectedRuntime,
            long generation
    ) {
        if (!isCurrentDataPlaneVerification(profile, expectedRuntime, generation)) {
            return false;
        }
        dataPlaneVerificationGeneration++;
        Future<?> verification = dataPlaneVerificationTask;
        Future<?> timeout = dataPlaneTimeoutTask;
        dataPlaneVerificationTask = null;
        dataPlaneTimeoutTask = null;
        cancelTask(verification);
        cancelTask(timeout);
        return true;
    }

    private void cancelDataPlaneVerification() {
        dataPlaneVerificationGeneration++;
        Future<?> verification = dataPlaneVerificationTask;
        Future<?> timeout = dataPlaneTimeoutTask;
        dataPlaneVerificationTask = null;
        dataPlaneTimeoutTask = null;
        cancelTask(verification);
        cancelTask(timeout);
    }

    private void startHealthMonitor(VpnProfile profile) {
        cancelTask(healthTask);
        healthTask = scheduler.scheduleWithFixedDelay(() -> {
            if (destroyed || intentionalDisconnect
                    || !(VpnRuntimeStateStore.currentConnectionState()
                    instanceof VpnConnectionState.Connected)) {
                return;
            }
            if (!tunnelManager.isAlive(tunnelRuntime)) {
                log(LogLevel.WARNING, "El transporte dejó de responder [HEALTH-TRANSPORT]",
                        profile.getId(), "CORE");
                triggerReconnect("Fallo detectado en el transporte");
                return;
            }
            Network expectedVpn = activeVpnNetwork;
            if (expectedVpn == null || !isExpectedOwnedVpnNetwork(expectedVpn)) {
                log(LogLevel.WARNING, "Android dejó de registrar la interfaz VPN [HEALTH-VPN]",
                        profile.getId(), "VPN");
                triggerReconnect("La interfaz VPN del sistema dejó de estar activa");
            }
        }, 5L, 5L, TimeUnit.SECONDS);
    }

    private void startStatsTicker(VpnProfile profile) {
        cancelTask(statsTask);
        statsTask = scheduler.scheduleAtFixedRate(() -> {
            if (destroyed || intentionalDisconnect || tunnelRuntime == null) {
                return;
            }
            XrayTrafficDelta traffic = tunnelManager.drainTraffic();
            long received = Math.max(0L, traffic.getReceivedBytes());
            long sent = Math.max(0L, traffic.getSentBytes());
            sessionReceivedBytes += received;
            sessionSentBytes += sent;
            boolean alive = tunnelManager.isAlive(tunnelRuntime);
            publishTraffic(new VpnTrafficStats(
                    sessionReceivedBytes,
                    sessionSentBytes,
                    alive ? received : 0L,
                    alive ? sent : 0L,
                    reconnectCount,
                    verifiedLatencyMs,
                    physicalNetworkType,
                    profile.getConnectionModeLabel()
            ));
        }, 1L, 1L, TimeUnit.SECONDS);
    }

    private void resetTrafficBaseline(VpnProfile profile) {
        tunnelManager.drainTraffic();
        sessionReceivedBytes = 0L;
        sessionSentBytes = 0L;
        publishTraffic(new VpnTrafficStats(
                0L, 0L, 0L, 0L, reconnectCount, verifiedLatencyMs,
                physicalNetworkType, profile.getConnectionModeLabel()
        ));
    }

    private synchronized void cleanupTunnel(boolean closeTun) {
        cancelDataPlaneVerification();
        synchronized (tunnelLock) {
            try {
                tunnelManager.stop(tunnelRuntime);
            } catch (Throwable ignored) {
            }
            tunnelRuntime = null;
        }
        if (closeTun) {
            try {
                if (tunInterface != null) {
                    tunInterface.close();
                }
            } catch (Throwable ignored) {
            }
            tunInterface = null;
            activeVpnNetwork = null;
        }
    }

    private void validateProfile(VpnProfile profile) {
        if (profile.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("El host del servidor es obligatorio");
        }
        if (profile.getPort() < 1 || profile.getPort() > 65_535) {
            throw new IllegalArgumentException("El puerto del servidor es inválido");
        }
        ConnectionMode mode = profile.getSelectedMode();
        if (!mode.getSupported()) {
            throw new IllegalArgumentException(profile.getConnectionModeLabel() + " no está disponible");
        }
        if (mode.isSsh()) {
            if (profile.getUsername().trim().isEmpty()) {
                throw new IllegalArgumentException("El usuario SSH es obligatorio");
            }
            if (profile.getPassword().trim().isEmpty()) {
                throw new IllegalArgumentException("La contraseña SSH es obligatoria");
            }
        }
        if (mode == ConnectionMode.V2RAY && profile.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("V2Ray requiere UUID / User ID");
        }
        if ((mode == ConnectionMode.TROJAN || mode == ConnectionMode.UDP)
                && profile.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("El método seleccionado requiere contraseña/auth");
        }
        if (mode.getRequiresSni() && profile.getSni().trim().isEmpty()) {
            throw new IllegalArgumentException("El método seleccionado requiere SNI");
        }
        if (mode.getRequiresPayload() && profile.getPayload().trim().isEmpty()) {
            throw new IllegalArgumentException("El método seleccionado requiere payload");
        }
        if (mode.getRequiresProxy()) {
            if (profile.getProxy().getHost().trim().isEmpty()
                    || profile.getProxy().getPort() < 1
                    || profile.getProxy().getPort() > 65_535) {
                throw new IllegalArgumentException("El método seleccionado requiere un proxy válido");
            }
        }
    }

    private void ensurePhysicalNetwork() {
        Network network = underlyingNetwork != null ? underlyingNetwork : findUsablePhysicalNetwork(null);
        if (network == null) {
            throw new IllegalStateException("No hay una red móvil o Wi-Fi con acceso a Internet");
        }
        registerUnderlyingNetwork(network);
    }

    private ParcelFileDescriptor buildTunInterface(
            VpnProfile profile,
            NetworkPreferences preferences,
            AppRoutingPreferences appRouting
    ) {
        try {
            Builder builder = new Builder()
                    .setSession(profile.getName())
                    .setConfigureIntent(PendingIntent.getActivity(
                            this,
                            0,
                            new Intent(this, MainActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    ))
                    .setMtu(preferences.getValidatedMtu())
                    .addAddress("10.20.0.2", 30)
                    .addRoute("0.0.0.0", 0)
                    .setBlocking(true);
            Network physicalNetwork = underlyingNetwork;
            if (physicalNetwork != null) {
                builder.setUnderlyingNetworks(new Network[]{physicalNetwork});
            }
            if (preferences.getIpMode().getCapturesIpv6()) {
                builder.addAddress("fd00:20::2", 126);
                builder.addRoute("::", 0);
            }
            for (String address : preferences.dnsServers()) {
                if (preferences.getIpMode().getCapturesIpv6() || !address.contains(":")) {
                    try {
                        builder.addDnsServer(address);
                    } catch (Throwable ignored) {
                    }
                }
            }
            applyAppRouting(builder, appRouting);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false);
            }
            ParcelFileDescriptor established = builder.establish();
            if (established == null || !established.getFileDescriptor().valid()) {
                if (established != null) {
                    try { established.close(); } catch (Throwable ignored) { }
                }
                throw new IllegalStateException(
                        "Android no activó la interfaz VPN; confirma nuevamente el permiso del sistema"
                );
            }
            return established;
        } catch (Throwable error) {
            log(LogLevel.ERROR, "Error creando TUN: " + safeMessage(error, "sin detalle"),
                    profile.getId(), "NETWORK");
            return null;
        }
    }

    /**
     * Builder.establish() creates the TUN descriptor, while ConnectivityManager
     * exposes the system-owned VPN network that drives Android's key indicator
     * and routing state. Connected is not published until both views agree.
     */
    private Network awaitAndroidVpnRegistration(VpnProfile profile) {
        long deadline = SystemClock.elapsedRealtime() + VPN_REGISTRATION_TIMEOUT_MS;
        while (!destroyed && !intentionalDisconnect && SystemClock.elapsedRealtime() < deadline) {
            ParcelFileDescriptor tun = tunInterface;
            if (tun == null || !tun.getFileDescriptor().valid()) {
                throw new IllegalStateException("Android cerró el descriptor TUN antes de registrar la VPN");
            }
            Network vpn = findOwnedVpnNetwork();
            if (vpn != null) {
                log(LogLevel.SUCCESS,
                        "Android confirmó la VPN propia · dirección TUN y ruta predeterminada activas",
                        profile.getId(), "VPN");
                return vpn;
            }
            sleep(50L);
        }
        throw new IllegalStateException(
                "Android no registró la red VPN para esta aplicación; no se publicará un estado Conectado falso"
        );
    }

    @Nullable
    private Network findOwnedVpnNetwork() {
        if (connectivityManager == null) {
            return null;
        }
        for (Network candidate : connectivityManager.getAllNetworks()) {
            if (isExpectedOwnedVpnNetwork(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * TRANSPORT_VPN alone can briefly describe a stale network while Android
     * replaces a TUN. Tie readiness to this app's UID, configured address and
     * default route so a previous VPN network cannot qualify a new session.
     */
    private boolean isExpectedOwnedVpnNetwork(Network candidate) {
        if (candidate == null || connectivityManager == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(candidate);
        if (capabilities == null
                || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && capabilities.getOwnerUid() != Process.myUid()) {
            return false;
        }

        LinkProperties properties = connectivityManager.getLinkProperties(candidate);
        if (properties == null) {
            return false;
        }
        boolean hasExpectedAddress = false;
        for (LinkAddress address : properties.getLinkAddresses()) {
            if (address.getAddress() != null
                    && "10.20.0.2".equals(address.getAddress().getHostAddress())) {
                hasExpectedAddress = true;
                break;
            }
        }
        if (!hasExpectedAddress) {
            return false;
        }
        for (RouteInfo route : properties.getRoutes()) {
            if (route.isDefaultRoute()
                    && route.getDestination() != null
                    && route.getDestination().getAddress() != null
                    && route.getDestination().getAddress().getAddress().length == 4) {
                return true;
            }
        }
        return false;
    }

    private void applyAppRouting(Builder builder, AppRoutingPreferences preferences) throws Exception {
        Set<String> normalized = preferences.getNormalizedPackages();
        List<String> packages = new ArrayList<>();
        for (String packageName : normalized) {
            if (!getPackageName().equals(packageName)) {
                packages.add(packageName);
            }
        }
        AppRoutingMode mode = preferences.getMode();
        if (mode == AppRoutingMode.ALL) {
            builder.addDisallowedApplication(getPackageName());
        } else if (mode == AppRoutingMode.ONLY_SELECTED) {
            if (packages.isEmpty()) {
                throw new IllegalStateException("App-routing invalid: no selected application [APP-ROUTE-001]");
            }
            for (String packageName : packages) {
                builder.addAllowedApplication(packageName);
            }
        } else {
            builder.addDisallowedApplication(getPackageName());
            for (String packageName : packages) {
                builder.addDisallowedApplication(packageName);
            }
        }
    }

    private void registerPhysicalNetworkCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
        } catch (Throwable error) {
            log(LogLevel.WARNING, "No se pudo registrar callback de red física: " + safeMessage(error, ""),
                    null, "NETWORK");
        }
    }

    private void handlePhysicalNetworkLost(Network lost) {
        sleep(300L);
        Network replacement = findUsablePhysicalNetwork(lost);
        if (replacement != null) {
            registerUnderlyingNetwork(replacement);
            log(LogLevel.INFO, "Cambio de red física: " + physicalNetworkType, profileId(), "NETWORK");
            return;
        }
        underlyingNetwork = null;
        physicalNetworkAvailable = false;
        physicalNetworkType = "Sin red";
        try {
            setUnderlyingNetworks(null);
        } catch (Throwable ignored) {
        }
        if (activeProfile != null && tunnelRuntime != null) {
            log(LogLevel.WARNING, "Red física perdida; activando protección de reconexión",
                    profileId(), "NETWORK");
            triggerReconnect("Red física perdida");
        }
    }

    private void registerUnderlyingNetwork(Network network) {
        if (network == null || connectivityManager == null) {
            return;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        if (capabilities == null
                || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            return;
        }
        underlyingNetwork = network;
        physicalNetworkAvailable =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        physicalNetworkType = networkType(capabilities);
        try {
            setUnderlyingNetworks(new Network[]{network});
        } catch (Throwable ignored) {
        }
    }

    private Network findUsablePhysicalNetwork(@Nullable Network excluding) {
        if (connectivityManager == null) {
            return null;
        }
        Network active = connectivityManager.getActiveNetwork();
        if (active != null && !active.equals(excluding) && isUsablePhysicalNetwork(active)) {
            return active;
        }
        Network current = underlyingNetwork;
        if (current != null && !current.equals(excluding) && isUsablePhysicalNetwork(current)) {
            return current;
        }
        for (Network candidate : connectivityManager.getAllNetworks()) {
            if (!candidate.equals(excluding) && isUsablePhysicalNetwork(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isUsablePhysicalNetwork(Network network) {
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
    }

    private static String networkType(NetworkCapabilities capabilities) {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "Wi-Fi";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "Datos móviles";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "Ethernet";
        }
        return "Red física";
    }

    private void startPreparingForeground(String label) {
        VpnConnectionState.Connecting state = new VpnConnectionState.Connecting(label);
        publishState(state);
        startForeground(GhostNexoraApp.NOTIF_ID_VPN, VpnNotificationHelper.build(this, state));
    }

    private void publishState(VpnConnectionState state) {
        VpnRuntimeStateStore.publishConnectionState(state);
        try {
            sendBroadcast(VpnServiceContract.INSTANCE.stateIntent(this, state));
        } catch (Throwable ignored) {
        }
    }

    private void publishTraffic(VpnTrafficStats stats) {
        VpnRuntimeStateStore.publishTrafficStats(stats);
        try {
            sendBroadcast(VpnServiceContract.INSTANCE.trafficIntent(this, stats));
        } catch (Throwable ignored) {
        }
    }

    private void broadcastCurrentRuntime() {
        try {
            sendBroadcast(VpnServiceContract.INSTANCE.stateIntent(
                    this, VpnRuntimeStateStore.currentConnectionState()));
            sendBroadcast(VpnServiceContract.INSTANCE.trafficIntent(
                    this, VpnRuntimeStateStore.currentTrafficStats()));
        } catch (Throwable ignored) {
        }
    }

    private void maybeStartFloatingWindow() {
        try {
            if (repositoryBridge.floatingWindow()
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && Settings.canDrawOverlays(this)) {
                startService(new Intent(this, FloatingWindowService.class));
            }
        } catch (Throwable ignored) {
        }
    }

    private VpnConnectionState.Connected connectedState(VpnProfile profile) {
        return new VpnConnectionState.Connected(
                profile.getName(),
                profile.isLocked() ? "[OCULTO]" : profile.getHost(),
                sessionConnectedSince
        );
    }

    private void logConnectionSnapshot(VpnProfile profile) {
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
        log(LogLevel.INFO,
                Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.SDK_INT + " · " + abi,
                profile.getId(), "SYSTEM");
        log(LogLevel.INFO,
                "Ghost Nexora VPN " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
                profile.getId(), "SYSTEM");
        log(LogLevel.INFO, "Red de salida: " + physicalNetworkType, profile.getId(), "NETWORK");
        if (profile.isLocked()) {
            log(LogLevel.INFO,
                    "Configuración bloqueada · servidor, método y parámetros [OCULTOS]",
                    profile.getId(), "PROTECTED");
        } else {
            log(LogLevel.INFO, "Servidor " + profile.getHost() + ":" + profile.getPort(),
                    profile.getId(), "NETWORK");
        }
    }

    private void log(LogLevel level, String message, @Nullable String profileId, String tag) {
        if (repositoryBridge == null || logExecutor.isShutdown()) {
            return;
        }
        try {
            logExecutor.execute(() -> {
                try {
                    repositoryBridge.log(level, message, profileId, tag);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private String friendlyConnectionError(Throwable error, VpnProfile profile) {
        String detail = safeMessage(error, "Fallo de conexión");
        return detail + " [" + profile.getConnectionModeLabel() + "]";
    }

    private void failStartup(String profileId, @Nullable VpnProfile profile, String message) {
        repositoryBridge.setVpnDesiredConnected(false);
        String name = profile != null ? profile.getName() : "";
        publishState(new VpnConnectionState.Error(message, name));
        log(LogLevel.ERROR, message, profileId, "SECURITY");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private String profileId() {
        VpnProfile profile = activeProfile;
        return profile != null ? profile.getId() : null;
    }

    private static String safeMessage(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                String normalized = message.replace('\n', ' ').trim();
                return normalized.length() <= 220 ? normalized : normalized.substring(0, 220);
            }
            current = current.getCause();
        }
        return fallback;
    }

    private static VpnTrafficStats emptyTraffic() {
        return new VpnTrafficStats(0L, 0L, 0L, 0L, 0, 0L, "--", "--");
    }

    private void submit(Runnable runnable) {
        if (!destroyed && !serviceExecutor.isShutdown()) {
            try {
                serviceExecutor.submit(runnable);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void cancelTask(Future<?> task) {
        if (task != null) {
            task.cancel(true);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
