package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshCompatibilityModesTest {
    @Test
    fun sslDirectUsesTlsWithoutPayload() {
        assertTrue(ConnectionMode.SSL_SNI.usesTls)
        assertFalse(ConnectionMode.SSL_SNI.requiresPayload)
    }

    @Test
    fun sslPayloadUsesBothTlsAndPayloadStages() {
        assertTrue(ConnectionMode.SSH_PAYLOAD_SSL.usesTls)
        assertTrue(ConnectionMode.SSH_PAYLOAD_SSL.requiresPayload)
    }

    @Test
    fun payloadOnlyNeverEntersTlsStage() {
        assertFalse(ConnectionMode.SSH_PAYLOAD.usesTls)
        assertTrue(ConnectionMode.SSH_PAYLOAD.requiresPayload)
    }
}
