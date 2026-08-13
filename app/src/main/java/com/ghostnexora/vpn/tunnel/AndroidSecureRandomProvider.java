package com.ghostnexora.vpn.tunnel;

import com.jcraft.jsch.Random;

import java.security.SecureRandom;
import java.util.Arrays;

/** Android-safe JSch random provider with no reflective dependency. */
public final class AndroidSecureRandomProvider implements Random {
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void fill(byte[] buffer, int start, int length) {
        if (buffer == null || start < 0 || length < 0 || start + length > buffer.length) {
            throw new IllegalArgumentException("Invalid random buffer range");
        }
        if (length == 0) {
            return;
        }
        byte[] generated = new byte[length];
        try {
            secureRandom.nextBytes(generated);
            System.arraycopy(generated, 0, buffer, start, length);
        } finally {
            Arrays.fill(generated, (byte) 0);
        }
    }
}
