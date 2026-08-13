package com.ghostnexora.vpn.service;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
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
import com.ghostnexora.vpn.tunnel.TunnelManager;
import com.ghostnexora.vpn.tunnel.TunnelRuntime;
import com.ghostnexora.vpn.tunnel.XrayTrafficDelta;

import java.net.InetSocketAddress;
import java.net.Socket;
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

    private static final long INITIAL_VERIFICATION_TIMEOUT_MS = 30_000L;
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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3, runnable -> {
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
    private volatile boolean physicalNetworkAvailable;
    private volatile String physicalNetworkType = "Sin red";
    private volatile boolean intentionalDisconnect;
    private volatile boolean destroyed;
    private volatile long verifiedLatencyMs;
    private volatile long sessionConnectedSince;
    private volatile long sessionReceivedBytes;
    private volatile long sessionSentBytes;
    private volatile int reconnectCount;

    private Future<?> startupVerificationTask;
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
            log(LogLevel.INFO, status, profileId(), statusTag(status));
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
        cancelTask(startupVerificationTask);
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
        cancelTask(startupVerificationTask);
        cancelTask(healthTask);
        cancelTask(statsTask);
        cleanupTunnel(true);
        activeProfile = profile;
        reconnectCount = 0;
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
            repositoryBridge.markLastUsed(profile.getId());
            sessionConnectedSince = System.currentTimeMillis();
            VpnConnectionState.Connected connected = connectedState(profile);
            publishState(connected);
            VpnNotificationHelper.update(this, connected);
            log(LogLevel.SUCCESS,
                    "Core y TUN activos · estado Conectado publicado · verificación en segundo plano",
                    profile.getId(), "VPN");
            repositoryBridge.resetVpnRecovery();
            resetTrafficBaseline(profile);
            startStatsTicker(profile);
            startInitialOutboundVerification(profile);
            startHealthMonitor(profile);
            maybeStartFloatingWindow();
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
        cancelTask(startupVerificationTask);
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
        cancelTask(startupVerificationTask);
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

        for (int attempt = 0;
             !destroyed && !intentionalDisconnect && tunInterface != null && attempt < maxAttempts;
             attempt++) {
            long baseDelay = RECONNECT_DELAYS[Math.min(attempt, RECONNECT_DELAYS.length - 1)];
            long waitMs = baseDelay + ((attempt * 173L) % 650L);
            VpnConnectionState.Reconnecting state =
                    new VpnConnectionState.Reconnecting(profile.getName(), attempt + 1, waitMs);
            publishState(state);
            VpnNotificationHelper.update(this, state);

            if (!physicalNetworkAvailable) {
                sleep(1_000L);
                Network replacement = findUsablePhysicalNetwork(null);
                if (replacement != null) {
                    registerUnderlyingNetwork(replacement);
                }
                attempt--;
                continue;
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
                if (tunnelManager.isAlive(tunnelRuntime)) {
                    reconnectCount++;
                    sessionConnectedSince = System.currentTimeMillis();
                    VpnConnectionState.Connected connected = connectedState(profile);
                    publishState(connected);
                    VpnNotificationHelper.update(this, connected);
                    log(LogLevel.SUCCESS,
                            "Core y TUN restablecidos en intento " + (attempt + 1) +
                                    " · validación en segundo plano",
                            profile.getId(), "NETWORK");
                    startInitialOutboundVerification(profile);
                    startHealthMonitor(profile);
                    return;
                }
            } catch (Throwable error) {
                log(LogLevel.WARNING,
                        "Intento " + (attempt + 1) + "/" + maxAttempts + " · " + safeMessage(error, "falló"),
                        profile.getId(), "NETWORK");
            }
        }

        String exhausted = "Reconnect attempts exhausted (" + maxAttempts + ") [RECONNECT-408]";
        if (killSwitch) {
            VpnConnectionState.Error state = new VpnConnectionState.Error(exhausted, profile.getName());
            publishState(state);
            VpnNotificationHelper.update(this, state);
            log(LogLevel.ERROR, exhausted + " · Kill Switch keeps traffic blocked", profile.getId(), "NETWORK");
        } else {
            cleanupTunnel(true);
            publishState(new VpnConnectionState.Error(exhausted, profile.getName()));
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void startInitialOutboundVerification(VpnProfile profile) {
        cancelTask(startupVerificationTask);
        TunnelRuntime expectedRuntime = tunnelRuntime;
        if (expectedRuntime == null) {
            return;
        }
        startupVerificationTask = scheduler.submit(() -> {
            long started = System.nanoTime();
            log(LogLevel.INFO, "Comprobación inicial de Internet iniciada en segundo plano",
                    profile.getId(), "NETWORK");
            try {
                OutboundCheck check = tunnelManager.verifyActive();
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                        > INITIAL_VERIFICATION_TIMEOUT_MS) {
                    log(LogLevel.WARNING,
                            "La comprobación inicial excedió 30s; el monitor continuará reintentando",
                            profile.getId(), "NETWORK");
                    return;
                }
                if (intentionalDisconnect || activeProfile == null
                        || !activeProfile.getId().equals(profile.getId())
                        || tunnelRuntime != expectedRuntime) {
                    return;
                }
                verifiedLatencyMs = Math.max(0L, check.getLatencyMs());
                log(LogLevel.SUCCESS,
                        "Salida a Internet verificada en segundo plano · " + verifiedLatencyMs + " ms",
                        profile.getId(), "NETWORK");
            } catch (Throwable error) {
                log(LogLevel.WARNING,
                        "Túnel activo, pero la comprobación inicial falló · " + safeMessage(error, "sin detalle"),
                        profile.getId(), "NETWORK");
            }
        });
    }

    private void startHealthMonitor(VpnProfile profile) {
        cancelTask(healthTask);
        final int[] ticks = {0};
        final int[] failures = {0};
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
            ticks[0]++;
            if (ticks[0] % 3 != 0 || isTaskRunning(startupVerificationTask)) {
                return;
            }
            try {
                tunnelManager.verifyActive();
                failures[0] = 0;
            } catch (Throwable error) {
                failures[0]++;
                log(LogLevel.WARNING,
                        "Comprobación de Internet fallida " + failures[0] + "/2 [HEALTH-OUTBOUND]",
                        profile.getId(), "CORE");
                if (failures[0] >= 2) {
                    triggerReconnect("Salida de Internet perdida en dos comprobaciones consecutivas");
                }
            }
        }, 5L, 5L, TimeUnit.SECONDS);
    }

    private void startStatsTicker(VpnProfile profile) {
        cancelTask(statsTask);
        final int[] tick = {0};
        statsTask = scheduler.scheduleAtFixedRate(() -> {
            if (destroyed || intentionalDisconnect || tunnelRuntime == null) {
                return;
            }
            XrayTrafficDelta traffic = tunnelManager.drainTraffic();
            long received = Math.max(0L, traffic.getReceivedBytes());
            long sent = Math.max(0L, traffic.getSentBytes());
            sessionReceivedBytes += received;
            sessionSentBytes += sent;
            tick[0]++;
            long latency = tick[0] % 10 == 0 && physicalNetworkAvailable
                    ? measureTcpLatency(profile.getHost(), profile.getPort())
                    : verifiedLatencyMs;
            if (latency > 0L) {
                verifiedLatencyMs = latency;
            }
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
        cancelTask(startupVerificationTask);
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
                    .setMtu(preferences.getValidatedMtu())
                    .addAddress("10.20.0.2", 30)
                    .addRoute("0.0.0.0", 0)
                    .setBlocking(true);
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
            return builder.establish();
        } catch (Throwable error) {
            log(LogLevel.ERROR, "Error creando TUN: " + safeMessage(error, "sin detalle"),
                    profile.getId(), "NETWORK");
            return null;
        }
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

    private long measureTcpLatency(String host, int port) {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            if (!protect(socket)) {
                throw new IllegalStateException("Android rechazó protect(Socket) para la medición TCP");
            }
            Network network = underlyingNetwork;
            if (network != null) {
                network.bindSocket(socket);
            }
            socket.connect(new InetSocketAddress(host, port), 2_000);
            return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        } catch (Throwable ignored) {
            return 0L;
        }
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

    private static String statusTag(String status) {
        if (status == null) {
            return "CORE";
        }
        int start = status.indexOf('[');
        int end = status.indexOf(']');
        if (start == 0 && end > 1) {
            return status.substring(1, end).toUpperCase();
        }
        return "CORE";
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

    private static boolean isTaskRunning(Future<?> task) {
        return task != null && !task.isDone() && !task.isCancelled();
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
