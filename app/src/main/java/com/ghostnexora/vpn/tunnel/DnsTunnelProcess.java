package com.ghostnexora.vpn.tunnel;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class DnsTunnelProcess implements AutoCloseable {
    private static final String READY = "GNX_DNSTT_PORT=";

    private final Process process;
    final int port;

    private DnsTunnelProcess(Process process, int port) {
        this.process = process;
        this.port = port;
    }

    static DnsTunnelProcess start(Context context, DnsTunnelProfile profile) {
        Process process;
        try {
            process = new ProcessBuilder(executable(context), "-" + profile.getTransport(),
                    profile.getResolver(), "-pubkey", profile.getPublicKey(), "-utls", "none",
                    profile.getDomain(), "127.0.0.1:0")
                    .redirectErrorStream(true).start();
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo iniciar el motor DNSTT", error);
        }

        CompletableFuture<Integer> localPort = new CompletableFuture<>();
        Thread reader = new Thread(() -> readPort(process, localPort), "dnstt-output");
        reader.setDaemon(true);
        reader.start();

        try {
            return new DnsTunnelProcess(process, localPort.get(8, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ignored) {
            // Engine details may contain private configuration identifiers.
        }
        process.destroyForcibly();
        throw new IllegalStateException("DNSTT no pudo abrir su conexión local");
    }

    private static void readPort(Process process, CompletableFuture<Integer> localPort) {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.startsWith(READY)) {
                    int port = Integer.parseInt(line.substring(READY.length()));
                    if (port > 0 && port <= 65535) localPort.complete(port);
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        } finally {
            localPort.completeExceptionally(new IOException("DNSTT exited before opening its listener"));
        }
    }

    static boolean probeUdp(Context context, DnsTunnelProfile profile) {
        Process probe = null;
        try {
            probe = new ProcessBuilder(executable(context), "--resolver-check",
                    profile.getResolver(), profile.getDomain())
                    .redirectOutput(new File("/dev/null"))
                    .redirectError(new File("/dev/null"))
                    .start();
            return probe.waitFor(3, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (IOException ignored) {
            return false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (probe != null && probe.isAlive()) probe.destroyForcibly();
        }
    }

    private static String executable(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libgnxdnstt.so").getAbsolutePath();
    }

    boolean isAlive() { return process.isAlive(); }

    @Override
    public void close() { process.destroyForcibly(); }
}
