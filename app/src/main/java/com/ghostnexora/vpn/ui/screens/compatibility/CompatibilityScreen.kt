@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.SurfaceVariant
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary

enum class CompatibilityStatus(val label: String) {
    CI_VERIFIED("Verificado por CI"),
    DEVICE_PENDING("Prueba física pendiente"),
    EXPERIMENTAL("Experimental"),
    NOT_AVAILABLE("Sin implementar")
}

data class CompatibilityEntry(
    val family: String,
    val mode: String,
    val transports: String,
    val status: CompatibilityStatus,
    val note: String
)

private val compatibilityEntries = listOf(
    CompatibilityEntry("SSH", "Conexión directa", "Password · TCP", CompatibilityStatus.DEVICE_PENDING, "Motor JSch y puente SOCKS por TCP."),
    CompatibilityEntry("SSH", "SSH + SSL", "TLS 1.2/1.3 · SNI estricto/custom", CompatibilityStatus.DEVICE_PENDING, "Permite política estricta o compatibilidad TLS para SSH por perfil."),
    CompatibilityEntry("SSH", "SSH + Payload", "HTTP 200/101 · payload", CompatibilityStatus.DEVICE_PENDING, "Acepta respuesta HTTP válida o banner SSH directo."),
    CompatibilityEntry("SSH", "SSH + SSL + Payload", "TLS → payload → SSH", CompatibilityStatus.DEVICE_PENDING, "TLS con SNI configurable, seguido del payload y la sesión SSH."),
    CompatibilityEntry("SSH", "SSH + Proxy", "HTTP CONNECT · SOCKS5", CompatibilityStatus.DEVICE_PENDING, "Proxy sin autenticación implementado; autenticación queda en roadmap."),
    CompatibilityEntry("Xray", "VLESS", "TCP · WS · gRPC · XHTTP · HTTPUpgrade · mKCP", CompatibilityStatus.DEVICE_PENDING, "Admite configuraciones TLS y REALITY."),
    CompatibilityEntry("Xray", "VMess", "TCP · WS · gRPC · TLS", CompatibilityStatus.DEVICE_PENDING, "Transportes TCP, WebSocket y gRPC."),
    CompatibilityEntry("Xray", "Trojan", "TCP/TLS · WS · gRPC", CompatibilityStatus.DEVICE_PENDING, "TLS estricto y SNI obligatorio."),
    CompatibilityEntry("Xray", "Shadowsocks", "AEAD · AEAD-2022 · ss://", CompatibilityStatus.DEVICE_PENDING, "TCP y UDP sin plugins externos."),
    CompatibilityEntry("Xray", "Proxy HTTPS", "HTTP CONNECT sobre TLS", CompatibilityStatus.DEVICE_PENDING, "TCP con validación del certificado del proxy."),
    CompatibilityEntry("Xray", "SOCKS5 + TLS", "SOCKS5 sobre TLS", CompatibilityStatus.DEVICE_PENDING, "TCP con validación del certificado del proxy."),
    CompatibilityEntry("Xray", "Hysteria2", "QUIC · UDP · TLS · obfs", CompatibilityStatus.EXPERIMENTAL, "Transporte QUIC con TLS y ofuscación configurable."),
    CompatibilityEntry("DNS", "Fast DNS / Slow DNS", "Túnel mediante DNS", CompatibilityStatus.NOT_AVAILABLE, "Se necesita confirmar el protocolo y contar con un perfil y servidor compatibles."),
    CompatibilityEntry("Core", "TUN Android", "IPv4 · IPv4 preferido · dual stack", CompatibilityStatus.CI_VERIFIED, "MTU, DNS, rutas y clases runtime se verifican en Debug y Release/R8."),
    CompatibilityEntry("Producto", "Split tunneling", "Todas · solo seleccionadas · excluir", CompatibilityStatus.CI_VERIFIED, "Las reglas se aplican con VpnService.Builder y se validan antes del TUN.")
)

@Composable
fun CompatibilityScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Compatibilidad", fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
        ) {
            items(compatibilityEntries) { entry ->
                GhostCard(backgroundColor = SurfaceVariant, borderColor = BorderSubtle) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
                        ) {
                            Icon(
                                imageVector = if (entry.status == CompatibilityStatus.NOT_AVAILABLE) Icons.Filled.Info else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                            Column(Modifier.weight(1f)) {
                                Text(entry.mode, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(entry.family, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                            }
                            if (entry.status == CompatibilityStatus.NOT_AVAILABLE) {
                                Text(entry.status.label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(entry.transports, color = NeonCyan, style = MaterialTheme.typography.bodySmall)
                        Text(entry.note, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
