package com.ghostnexora.vpn.tunnel;

import android.content.Context;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import go.Seq;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

/** Java adapter around the bundled AndroidLibXrayLite native core. */
public final class XrayCoreEngine {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Object INITIALIZATION_LOCK = new Object();
    private static final List<String> REQUIRED_ASSETS = Arrays.asList("geoip.dat", "geosite.dat");
    private static final List<String> CONNECTIVITY_TEST_URLS = Arrays.asList(
            "https://cp.cloudflare.com/generate_204",
            "https://www.gstatic.com/generate_204"
    );

    private final Context appContext;
    private final Function1<? super String, Unit> onStatus;
    private CoreController controller;
    private Integer activeHealthCheckPort;

    public XrayCoreEngine(Context context, Function1<? super String, Unit> onStatus) {
        if (context == null) throw new IllegalArgumentException("context == null");
        this.appContext = context.getApplicationContext();
        this.onStatus = onStatus != null ? onStatus : ignored -> Unit.INSTANCE;
    }

    public XrayCoreEngine(Context context) {
        this(context, ignored -> Unit.INSTANCE);
    }

    public synchronized boolean isRunning() {
        CoreController current = controller;
        return current != null && current.getIsRunning();
    }

    public synchronized OutboundCheck verifyOutbound(String config) {
        initializeCore();
        status("[CORE] Verificación nativa sin TUN iniciada");
        return measureAcrossEndpoints(url -> Libv2ray.measureOutboundDelay(config, url));
    }

    public synchronized void start(String config, int tunFd, Integer healthCheckPort) {
        if (tunFd <= 0) throw new IllegalArgumentException("Descriptor TUN inválido");
        if (healthCheckPort != null && (healthCheckPort < 1 || healthCheckPort > 65_535)) {
            throw new IllegalArgumentException("Puerto de comprobación Xray inválido");
        }
        if (isRunning()) throw new IllegalStateException("Xray Core ya está ejecutándose");

        initializeCore();
        status("[CORE] Creando controlador AndroidLibXrayLite");
        CoreController newController = Libv2ray.newCoreController(new CoreCallback());
        controller = newController;
        try {
            status("[TUN] Entregando la interfaz Android al core nativo");
            newController.startLoop(config, tunFd);
            if (!newController.getIsRunning()) throw new IllegalStateException("Xray Core no pudo iniciar el loop TUN");
            activeHealthCheckPort = healthCheckPort;
            status("[CORE] Xray Core activo");
        } catch (Throwable error) {
            controller = null;
            activeHealthCheckPort = null;
            try { newController.stopLoop(); } catch (Throwable ignored) { }
            throw new IllegalStateException(firstMessage(error, "Fallo iniciando Xray Core"), error);
        }
    }

    public void start(String config, int tunFd) {
        start(config, tunFd, null);
    }

    /** Remote I/O happens outside the monitor so stop() remains immediately available. */
    public OutboundCheck verifyActiveOutbound() {
        CoreController activeController;
        Integer healthCheckPort;
        synchronized (this) {
            CoreController current = controller;
            if (current == null || !current.getIsRunning()) {
                throw new IllegalStateException("Xray Core no está activo para validar la salida");
            }
            activeController = current;
            healthCheckPort = activeHealthCheckPort;
        }
        if (healthCheckPort != null) return verifySshSocksOutbound(healthCheckPort);
        status("[NETWORK] Comprobando Internet a través del outbound activo");
        return measureAcrossEndpoints(activeController::measureDelay);
    }

    public synchronized XrayTrafficDelta drainProxyTraffic() {
        CoreController activeController = controller;
        if (activeController == null || !activeController.getIsRunning()) return new XrayTrafficDelta();
        long received = 0L;
        long sent = 0L;
        try { received = Math.max(0L, activeController.queryStats("proxy", "downlink")); } catch (Throwable ignored) { }
        try { sent = Math.max(0L, activeController.queryStats("proxy", "uplink")); } catch (Throwable ignored) { }
        return new XrayTrafficDelta(received, sent);
    }

    public synchronized void stop() {
        CoreController activeController = controller;
        if (activeController == null) return;
        controller = null;
        activeHealthCheckPort = null;
        try { if (activeController.getIsRunning()) activeController.stopLoop(); } catch (Throwable ignored) { }
    }

    public String version() {
        try { return Libv2ray.checkVersionX(); } catch (Throwable ignored) { return "desconocida"; }
    }

    private OutboundCheck verifySshSocksOutbound(int healthCheckPort) {
        Throwable lastError = null;
        status("[NETWORK] Comprobando ruta Xray → SOCKS → direct-tcpip SSH");
        for (int index = 0; index < Socks5OutboundProbe.targets.size(); index++) {
            SocksProbeTarget target = Socks5OutboundProbe.targets.get(index);
            try {
                status("[SOCKS] Prueba real " + (index + 1) + "/" + Socks5OutboundProbe.targets.size() + " · TLS remoto por SSH");
                SocksProbeResult result = Socks5OutboundProbe.measure(healthCheckPort, target);
                status("[SOCKS] Ruta bidireccional verificada · " + result.getTlsProtocol() + " · " +
                        result.getCipherSuite() + " · " + result.getLatencyMs() + " ms");
                return new OutboundCheck(result.getLatencyMs(), target.getEndpoint());
            } catch (Throwable error) {
                lastError = error;
                status("[SOCKS] WARN · prueba " + (index + 1) + " falló · " + truncate(firstMessage(error, error.getClass().getSimpleName()), 180));
            }
        }
        throw new IllegalStateException(
                "La ruta Xray → SOCKS → SSH no completó el handshake TLS remoto: " + truncate(firstMessage(lastError, "sin detalle"), 180),
                lastError
        );
    }

    private OutboundCheck measureAcrossEndpoints(EndpointMeasure measure) {
        Throwable lastError = null;
        for (int index = 0; index < CONNECTIVITY_TEST_URLS.size(); index++) {
            String endpoint = CONNECTIVITY_TEST_URLS.get(index);
            try {
                status("[NETWORK] Prueba de salida " + (index + 1) + "/" + CONNECTIVITY_TEST_URLS.size());
                long latency = measure.measure(endpoint);
                if (latency >= 0L) {
                    status("[NETWORK] Salida verificada · " + latency + " ms");
                    return new OutboundCheck(latency, endpoint);
                }
                lastError = new IllegalStateException("La prueba devolvió latencia inválida: " + latency);
            } catch (Throwable error) {
                lastError = error;
                status("[NETWORK] WARN · prueba " + (index + 1) + " falló · " + truncate(firstMessage(error, error.getClass().getSimpleName()), 160));
            }
        }
        String detail = truncate(firstMessage(lastError, ""), 180);
        throw new IllegalStateException(
                "El servidor o la configuración no entregan acceso a Internet" + (!detail.isEmpty() ? ": " + detail : ""),
                lastError
        );
    }

    private void initializeCore() {
        if (INITIALIZED.get()) return;
        synchronized (INITIALIZATION_LOCK) {
            if (INITIALIZED.get()) return;
            try {
                status("[CORE] Inicializando entorno nativo una sola vez");
                Seq.setContext(appContext);
                File assetsDir = new File(appContext.getFilesDir(), "xray-assets");
                if (!assetsDir.exists() && !assetsDir.mkdirs()) throw new IOException("No se pudo crear el directorio de recursos Xray");
                prepareEmbeddedGeoData(assetsDir);
                for (String name : REQUIRED_ASSETS) {
                    File asset = new File(assetsDir, name);
                    if (!asset.isFile() || asset.length() <= 0L) throw new IllegalStateException("Falta recurso Xray: " + name);
                }
                status("[CORE] Recursos geoip/geosite verificados");
                Libv2ray.initCoreEnv(assetsDir.getAbsolutePath(), deviceKey());
                INITIALIZED.set(true);
                status("[CORE] Entorno nativo inicializado");
            } catch (Throwable error) {
                INITIALIZED.set(false);
                if (error instanceof RuntimeException) throw (RuntimeException) error;
                throw new IllegalStateException(firstMessage(error, "Fallo inicializando Xray"), error);
            }
        }
    }

    private void prepareEmbeddedGeoData(File assetDirectory) throws IOException {
        for (String fileName : REQUIRED_ASSETS) {
            File destination = new File(assetDirectory, fileName);
            if (destination.isFile() && destination.length() > 0L) continue;
            File staged = File.createTempFile(fileName + "-", ".tmp", assetDirectory);
            try {
                try (java.io.InputStream input = appContext.getAssets().open(fileName);
                     FileOutputStream output = new FileOutputStream(staged)) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) output.write(buffer, 0, read);
                    }
                    output.flush();
                }
                if (staged.length() <= 0L) throw new IOException("Embedded " + fileName + " is empty");
                try {
                    Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                if (staged.exists()) staged.delete();
            }
        }
    }

    private String deviceKey() throws Exception {
        String androidId = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.trim().isEmpty()) androidId = appContext.getPackageName();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (appContext.getPackageName() + ':' + androidId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(32);
        for (int i = 0; i < 16; i++) result.append(String.format(Locale.US, "%02x", digest[i] & 0xFF));
        Arrays.fill(digest, (byte) 0);
        return result.toString();
    }

    private void status(String message) { onStatus.invoke(message); }

    private static String firstMessage(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) return message.replace('\n', ' ').trim();
            current = current.getCause();
        }
        return fallback;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        String normalized = value.replace('\n', ' ').trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    @FunctionalInterface
    private interface EndpointMeasure {
        long measure(String url) throws Exception;
    }

    private final class CoreCallback implements CoreCallbackHandler {
        @Override public long startup() { status("[CORE] Señal nativa de inicio recibida"); return 0L; }
        @Override public long shutdown() { status("[CORE] Señal nativa de cierre recibida"); return 0L; }
        @Override public long onEmitStatus(long code, String message) {
            if (message != null && !message.trim().isEmpty()) status("[CORE] Evento nativo " + code + " · " + message);
            return 0L;
        }
    }
}
