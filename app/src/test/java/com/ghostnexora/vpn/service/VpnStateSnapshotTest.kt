package com.ghostnexora.vpn.service

import com.ghostnexora.vpn.data.model.VpnConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnStateSnapshotTest {
    @Test
    fun everyRuntimeStateSurvivesTheProcessBoundary() {
        val states = listOf(
            VpnConnectionState.Disconnected,
            VpnConnectionState.Connecting("Nexora"),
            VpnConnectionState.Reconnecting("Nexora", attempt = 3, nextRetryMs = 5_000L),
            VpnConnectionState.Connected(
                profileName = "Nexora",
                serverIp = "203.0.113.8",
                connectedSince = 1_725_000_000_000L
            ),
            VpnConnectionState.Disconnecting,
            VpnConnectionState.Error("El core se detuvo", "Nexora")
        )

        states.forEach { state ->
            assertEquals(state, VpnStateSnapshot.from(state).toState())
        }
    }

    @Test
    fun malformedRetryValuesAreBoundedBeforeReachingTheUi() {
        val restored = VpnStateSnapshot(
            kind = VpnStateKind.RECONNECTING,
            profileName = "Nexora",
            attempt = -4,
            nextRetryMs = -1L
        ).toState()

        assertTrue(restored is VpnConnectionState.Reconnecting)
        restored as VpnConnectionState.Reconnecting
        assertEquals(1, restored.attempt)
        assertEquals(0L, restored.nextRetryMs)
    }
}
