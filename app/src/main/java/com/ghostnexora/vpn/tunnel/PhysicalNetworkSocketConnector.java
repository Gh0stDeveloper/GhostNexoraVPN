package com.ghostnexora.vpn.tunnel;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * Creates all Java/JSch transport sockets outside the Android VPN TUN.
 *
 * Protection order is intentionally strict:
 * 1) VpnService.protect(Socket)
 * 2) bind the socket to a NET_CAPABILITY_NOT_VPN Network when available
 * 3) connect only after both routing protections have been applied
 *
 * The owning application UID is also excluded by GhostVpnService.Builder,
 * providing an independent third loop-prevention layer.
 */
public final class PhysicalNetworkSocketConnector {
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 20_000;
    private static final int PER_ADDRESS_TIMEOUT_MS = 8_000;

    private final ConnectivityManager connectivityManager;
    private final Function1<? super String, Unit> onStatus;
    private final Set<Network> knownPhysicalNetworks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean protectionReported = new AtomicBoolean(false);

    private final ConnectivityManager.NetworkCallback physicalNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    knownPhysicalNetworks.add(network);
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    boolean physicalInternet =
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
                    if (physicalInternet) {
                        knownPhysicalNetworks.add(network);
                    } else {
                        knownPhysicalNetworks.remove(network);
                    }
                }

                @Override
                public void onLost(Network network) {
                    knownPhysicalNetworks.remove(network);
                }
            };

    public PhysicalNetworkSocketConnector(Context context, Function1<? super String, Unit> onStatus) {
        Context appContext = context != null ? context.getApplicationContext() : null;
        this.connectivityManager = appContext != null
                ? (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                : null;
        this.onStatus = onStatus != null ? onStatus : ignored -> Unit.INSTANCE;
        registerPhysicalNetworkCallback();
    }

    public PhysicalNetworkSocketConnector(Context context) {
        this(context, ignored -> Unit.INSTANCE);
    }

    public Socket connect(String host, int port) throws IOException {
        return connect(host, port, DEFAULT_CONNECT_TIMEOUT_MS);
    }

    public Socket connect(String host, int port, int timeoutMs) throws IOException {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("El host de transporte no puede estar vacío");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("El puerto de transporte es inválido");
        }

        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        List<Throwable> failures = new ArrayList<>();
        List<Network> networks = physicalNetworks();

        for (int networkIndex = 0; networkIndex < networks.size(); networkIndex++) {
            Network network = networks.get(networkIndex);
            List<InetAddress> addresses = resolve(network, host, failures);
            if (addresses.isEmpty()) {
                continue;
            }

            status("[NETWORK] DNS físico · " + host + " → " + joinAddresses(addresses));
            for (int addressIndex = 0; addressIndex < addresses.size(); addressIndex++) {
                InetAddress address = addresses.get(addressIndex);
                int remainingMs = remainingTimeoutMs(deadlineNanos);
                if (remainingMs <= 0) {
                    break;
                }

                Socket socket = configuredAndProtectedSocket();
                try {
                    network.bindSocket(socket);
                    status("[NETWORK] Intento TCP físico " + (networkIndex + 1) + "." +
                            (addressIndex + 1) + " · " + address.getHostAddress() + ":" + port +
                            " · " + networkLabel(network));
                    long startedAt = System.nanoTime();
                    socket.connect(new InetSocketAddress(address, port), remainingMs);
                    long latencyMs = Math.max(1L,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                    status("[NETWORK] Socket TCP físico conectado · " + address.getHostAddress() +
                            ":" + port + " · " + latencyMs + " ms · " + networkLabel(network));
                    return socket;
                } catch (Throwable error) {
                    failures.add(error);
                    closeQuietly(socket);
                    status("[NETWORK] IP no disponible · " + address.getHostAddress() + ":" + port +
                            " · " + shortMessage(error));
                }
            }
        }

        // Vendor fallback: still fail-closed because protect(Socket) is mandatory
        // even when Android does not expose a bindable NOT_VPN Network instance.
        List<InetAddress> fallbackAddresses = resolve(null, host, failures);
        for (int index = 0; index < fallbackAddresses.size(); index++) {
            InetAddress address = fallbackAddresses.get(index);
            int remainingMs = remainingTimeoutMs(deadlineNanos);
            if (remainingMs <= 0) {
                break;
            }

            Socket socket = configuredAndProtectedSocket();
            try {
                status("[NETWORK] Intento TCP protegido " + (index + 1) + " · " +
                        address.getHostAddress() + ":" + port);
                socket.connect(new InetSocketAddress(address, port), remainingMs);
                status("[NETWORK] Socket TCP conectado con protect(Socket) · " +
                        address.getHostAddress() + ":" + port);
                return socket;
            } catch (Throwable error) {
                failures.add(error);
                closeQuietly(socket);
            }
        }

        Throwable last = failures.isEmpty() ? null : failures.get(failures.size() - 1);
        int attempted = Math.max(1, failures.size());
        throw new IOException(
                "[TCP-ALL-FAILED] No fue posible conectar con ninguna IP de " + host + ":" + port +
                        " tras " + attempted + " intento(s).",
                last
        );
    }

    private void registerPhysicalNetworkCallback() {
        if (connectivityManager == null) {
            return;
        }
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        try {
            connectivityManager.registerNetworkCallback(request, physicalNetworkCallback);
        } catch (Throwable error) {
            status("[NETWORK] Registro de redes físicas no disponible · " + shortMessage(error));
        }
    }

    private List<Network> physicalNetworks() {
        if (connectivityManager == null) {
            return new ArrayList<>();
        }
        List<Network> result = new ArrayList<>();
        Network active = connectivityManager.getActiveNetwork();
        if (active != null && isPhysicalInternetNetwork(active)) {
            result.add(active);
        }
        for (Network network : knownPhysicalNetworks) {
            if (!network.equals(active) && isPhysicalInternetNetwork(network)) {
                result.add(network);
            }
        }
        return result;
    }

    private List<InetAddress> resolve(Network network, String host, List<Throwable> failures) {
        try {
            InetAddress[] raw = network != null ? network.getAllByName(host) : InetAddress.getAllByName(host);
            Map<String, InetAddress> unique = new LinkedHashMap<>();
            for (InetAddress address : raw) {
                unique.put(address.getHostAddress(), address);
            }
            List<InetAddress> result = new ArrayList<>(unique.values());
            result.sort(Comparator.comparingInt(address -> address instanceof Inet4Address ? 0 : 1));
            return result;
        } catch (Throwable error) {
            failures.add(error);
            return new ArrayList<>();
        }
    }

    private int remainingTimeoutMs(long deadlineNanos) {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        return (int) Math.min(remaining, PER_ADDRESS_TIMEOUT_MS);
    }

    private boolean isPhysicalInternetNetwork(Network network) {
        if (connectivityManager == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
    }

    private String networkLabel(Network network) {
        NetworkCapabilities capabilities = connectivityManager != null
                ? connectivityManager.getNetworkCapabilities(network)
                : null;
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "datos móviles";
        }
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "Wi-Fi";
        }
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "Ethernet";
        }
        return "red física";
    }

    private Socket configuredAndProtectedSocket() throws IOException {
        Socket socket = configuredSocket();
        if (!OutboundSocketProtection.protect(socket)) {
            closeQuietly(socket);
            throw new IOException(
                    "[VPN-LOOP-001] Android rechazó VpnService.protect(Socket); " +
                            "el transporte se detuvo para evitar un bucle hacia el TUN"
            );
        }
        if (protectionReported.compareAndSet(false, true)) {
            status("[NETWORK] Sockets de transporte excluidos del TUN con VpnService.protect(Socket)");
        }
        return socket;
    }

    private static Socket configuredSocket() throws IOException {
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setReuseAddress(true);
        return socket;
    }

    private void status(String message) {
        onStatus.invoke(message);
    }

    private static String joinAddresses(List<InetAddress> addresses) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < addresses.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            String value = addresses.get(i).getHostAddress();
            builder.append(value != null ? value : "");
        }
        return builder.toString();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (Throwable ignored) {
        }
    }

    private static String shortMessage(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                String normalized = message.replace('\n', ' ').trim();
                return normalized.length() <= 220 ? normalized : normalized.substring(0, 220);
            }
            current = current.getCause();
        }
        return error.getClass().getSimpleName();
    }
}
