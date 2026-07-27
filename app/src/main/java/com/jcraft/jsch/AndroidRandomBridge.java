package com.jcraft.jsch;

/**
 * Installs a directly referenced Random implementation into JSch Session.
 *
 * Session.random is package-private in JSch 0.2.25. Setting it here prevents
 * Session.connect() from resolving the configured provider with Class.forName,
 * which is the exact Android runtime path that produced ClassNotFoundException.
 */
public final class AndroidRandomBridge {
    private AndroidRandomBridge() {
    }

    public static void install(Random provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider == null");
        }
        Session.random = provider;
        Packet.setRandom(provider);
    }

    public static boolean isInstalled() {
        return Session.random != null;
    }
}
