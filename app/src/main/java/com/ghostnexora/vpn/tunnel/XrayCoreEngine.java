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
import java.util.Base64;
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
    static final List<String> CONNECTIVITY_TEST_URLS = Arrays.asList(
            "https://cp.cloudflare.com/generate_204",
            "https://www.gstatic.com/generate_204"
    );

    private final Context appContext;
    private final Function1<? super String, Unit> onStatus;
    private volatile CoreController controller;

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

    public OutboundCheck verifyActiveOutbound() throws Exception {
        return verifyActiveOutbound(CONNECTIVITY_TEST_URLS.get(0));
    }

    OutboundCheck verifyActiveOutbound(String endpoint) throws Exception {
        CoreController current = controller;
        if (current == null || !current.getIsRunning()) {
            throw new IllegalStateException("Xray Core no está activo para validar la salida");
        }
        // MeasureDelay dials through this instance's dispatcher. The proxy is
        // the first/default outbound, so neither the Android UID exclusion nor
        // a new core instance can send this request over the physical network directly.
        long latency = current.measureDelay(endpoint);
        if (latency < 0 || current != controller || !current.getIsRunning()) {
            throw new IllegalStateException("El motor cambió o la salida no respondió durante la validación");
        }
        return new OutboundCheck(Math.max(1L, latency), endpoint);
    }

    public synchronized void start(String config, int tunFd) {
        if (tunFd <= 0) throw new IllegalArgumentException("Descriptor TUN inválido");
        if (isRunning()) throw new IllegalStateException("Xray Core ya está ejecutándose");

        initializeCore();
        status("[CORE] Creando controlador AndroidLibXrayLite");
        CoreController newController = Libv2ray.newCoreController(new CoreCallback());
        controller = newController;
        try {
            status("[TUN] Entregando la interfaz Android al core nativo");
            newController.startLoop(config, tunFd);
            if (!newController.getIsRunning()) throw new IllegalStateException("Xray Core no pudo iniciar el loop TUN");
            status("[CORE] Xray Core activo");
        } catch (Throwable error) {
            controller = null;
            try { newController.stopLoop(); } catch (Throwable ignored) { }
            throw new IllegalStateException(firstMessage(error, "Fallo iniciando Xray Core"), error);
        }
    }

    public synchronized XrayTrafficDelta drainProxyTraffic() {
        CoreController activeController = controller;
        if (activeController == null || !activeController.getIsRunning()) return new XrayTrafficDelta();
        try {
            return XrayTrafficDelta.fromOutboundStats(activeController.queryAllOutboundTrafficStats());
        } catch (RuntimeException ignored) {
            return new XrayTrafficDelta();
        }
    }

    public synchronized void stop() {
        CoreController activeController = controller;
        if (activeController == null) return;
        controller = null;
        try { if (activeController.getIsRunning()) activeController.stopLoop(); } catch (Throwable ignored) { }
    }

    public String version() {
        try { return Libv2ray.checkVersionX(); } catch (Throwable ignored) { return "desconocida"; }
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
        try {
            // Xray decodes xray.xudp.basekey as unpadded Base64URL and requires 32 bytes.
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } finally {
            Arrays.fill(digest, (byte) 0);
        }
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
