package com.ghostnexora.vpn.tunnel;

import com.jcraft.jsch.AndroidRandomBridge;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Android-safe JSch bootstrap implemented in Java. */
public final class JschRuntime {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final List<String> ESSENTIAL_PROVIDER_KEYS = Collections.unmodifiableList(Arrays.asList(
            "ecdh-sha2-nistp256",
            "diffie-hellman-group-exchange-sha256",
            "aes256-ctr",
            "hmac-sha2-512",
            "userauth.password",
            "userauth.keyboard-interactive"
    ));

    private JschRuntime() {
    }

    public static void install() {
        install(ignored -> Unit.INSTANCE);
    }

    public static void install(Function1<? super String, Unit> onStatus) {
        Function1<? super String, Unit> callback = onStatus != null ? onStatus : ignored -> Unit.INSTANCE;
        if (INSTALLED.compareAndSet(false, true)) {
            AndroidSecureRandomProvider provider = new AndroidSecureRandomProvider();
            byte[] probe = new byte[32];
            provider.fill(probe, 0, probe.length);
            boolean initialized = false;
            for (byte value : probe) {
                if (value != 0) {
                    initialized = true;
                    break;
                }
            }
            Arrays.fill(probe, (byte) 0);
            if (!initialized) {
                throw new IllegalStateException("Android SecureRandom provider did not initialize");
            }

            JSch.setConfig("random", AndroidSecureRandomProvider.class.getName());
            AndroidRandomBridge.install(provider);
            if (!AndroidRandomBridge.isInstalled()) {
                throw new IllegalStateException("JSch random bridge did not install");
            }
        }

        verifyEssentialProviders();
        JSch.setLogger(new SanitizedJschLogger(callback));
        callback.invoke("[SSH] JSch " + JSch.VERSION + " listo · algoritmos esenciales verificados");
    }

    public static String configuredRandomProvider() {
        return JSch.getConfig("random");
    }

    public static boolean isDirectProviderInstalled() {
        return AndroidRandomBridge.isInstalled();
    }

    public static void verifyEssentialProviders() {
        ClassLoader loader = JschRuntime.class.getClassLoader();
        for (String algorithm : ESSENTIAL_PROVIDER_KEYS) {
            String className = JSch.getConfig(algorithm);
            if (className == null || className.trim().isEmpty()) {
                throw new IllegalStateException("JSch no registró el algoritmo " + algorithm);
            }
            try {
                Class.forName(className, false, loader);
            } catch (Throwable error) {
                throw new IllegalStateException(
                        "Proveedor JSch ausente para " + algorithm + ": " + className,
                        error
                );
            }
        }
    }

    public static List<String> getEssentialProviderKeys() {
        return ESSENTIAL_PROVIDER_KEYS;
    }

    private static final class SanitizedJschLogger implements Logger {
        private static final Pattern SECRET = Pattern.compile(
                "(?i)(password|passphrase|authorization)\\s*[:=]\\s*\\S+"
        );
        private final Function1<? super String, Unit> onStatus;

        private SanitizedJschLogger(Function1<? super String, Unit> onStatus) {
            this.onStatus = onStatus;
        }

        @Override
        public boolean isEnabled(int level) {
            return level >= Logger.INFO;
        }

        @Override
        public void log(int level, String message) {
            if (message == null) {
                return;
            }
            String clean = SECRET.matcher(message).replaceAll("$1=<redacted>")
                    .replace('\n', ' ')
                    .trim();
            if (clean.isEmpty()) {
                return;
            }
            if (clean.length() > 240) {
                clean = clean.substring(0, 240);
            }
            String label;
            if (level == Logger.ERROR) {
                label = "ERROR";
            } else if (level == Logger.WARN) {
                label = "WARN";
            } else {
                label = "INFO";
            }
            onStatus.invoke("[SSH] " + label + " · " + clean);
        }
    }
}
