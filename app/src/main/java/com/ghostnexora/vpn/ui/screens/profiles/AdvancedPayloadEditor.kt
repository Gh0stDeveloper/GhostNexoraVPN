package com.ghostnexora.vpn.ui.screens.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.SurfaceVariant
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.util.PayloadContext
import com.ghostnexora.vpn.util.PayloadEngine
import com.ghostnexora.vpn.util.PayloadTemplate

@Composable
fun AdvancedPayloadEditor(
    payload: String,
    host: String,
    port: Int,
    sni: String,
    proxyHost: String,
    proxyPort: Int,
    onPayloadChange: (String) -> Unit
) {
    val validation = remember(payload) { PayloadEngine.validate(payload) }
    val preview = remember(payload, host, port, sni, proxyHost, proxyPort) {
        runCatching {
            PayloadEngine.compile(
                raw = payload,
                context = PayloadContext(
                    host = host.ifBlank { "example.com" },
                    port = port.coerceIn(1, 65535),
                    sni = sni.ifBlank { host.ifBlank { "example.com" } },
                    proxyHost = proxyHost,
                    proxyPort = proxyPort
                ),
                deterministicSeed = 1L
            )
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant, RoundedCornerShape(16.dp))
            .padding(Dimens.SpaceMD),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
    ) {
        Text("Editor avanzado de payload", color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(
            "Selecciona una plantilla y edítala. [split] divide la escritura; [delay=500] espera entre segmentos con límites de seguridad.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PayloadTemplate.entries.forEach { template ->
                AssistChip(
                    onClick = { onPayloadChange(PayloadEngine.template(template)) },
                    label = { Text(template.label) }
                )
            }
        }

        OutlinedTextField(
            value = payload,
            onValueChange = onPayloadChange,
            label = { Text("Payload HTTP") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            supportingText = {
                Text("[host] [port] [host_port] [sni] [proxy] [proxy_port] [crlf] [lf] [cr] [split] [delay=500] [rotate] [random]")
            }
        )

        validation.errors.forEach { error ->
            Text(error, color = NeonAmber, style = MaterialTheme.typography.bodySmall)
        }
        validation.warnings.forEach { warning ->
            Text(warning, color = NeonAmber, style = MaterialTheme.typography.labelSmall)
        }
        if (validation.isValid && preview != null) {
            Text(
                "Sintaxis válida · ${preview.segmentCount} segmento(s) · ${preview.totalDelayMs} ms de retardo",
                color = NeonGreen,
                style = MaterialTheme.typography.labelSmall
            )
            Text("Vista previa CRLF", color = NeonCyan, style = MaterialTheme.typography.labelMedium)
            Text(
                preview.visiblePreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeonCyan.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
    }
}