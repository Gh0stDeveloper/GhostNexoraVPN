package com.ghostnexora.vpn;

import static org.junit.Assert.assertArrayEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.ghostnexora.vpn.security.NativeGuard;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class ProfileProtectionTest {
    @Test
    public void clearsImportedByteBuffersAcrossJniChunks() {
        byte[] profile = new byte[8193];
        Arrays.fill(profile, (byte) 0x6f);

        NativeGuard.INSTANCE.wipe(profile);

        assertArrayEquals(new byte[profile.length], profile);
    }
}
