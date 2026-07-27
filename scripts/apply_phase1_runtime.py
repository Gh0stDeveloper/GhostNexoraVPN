from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content, encoding="utf-8")


def replace_once(content: str, old: str, new: str, label: str) -> str:
    if old not in content:
        raise RuntimeError(f"Missing patch anchor: {label}")
    return content.replace(old, new, 1)


# ---------------------------------------------------------------------------
# StableXrayConfigFactory
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/ghostnexora/vpn/tunnel/StableXrayConfigFactory.kt"
s = read(path)
s = replace_once(
    s,
    "import com.ghostnexora.vpn.data.model.ConnectionMode\nimport com.ghostnexora.vpn.data.model.VpnProfile",
    "import com.ghostnexora.vpn.data.model.ConnectionMode\nimport com.ghostnexora.vpn.data.model.DnsMode\nimport com.ghostnexora.vpn.data.model.NetworkPreferences\nimport com.ghostnexora.vpn.data.model.VpnProfile",
    "xray imports",
)
s = replace_once(s, "const val TUN_MTU = 1400", "const val TUN_MTU = NetworkPreferences.DEFAULT_MTU", "default MTU")
s = replace_once(
    s,
    "fun build(profile: VpnProfile, sshSocksPort: Int? = null): String {",
    "fun build(\n        profile: VpnProfile,\n        sshSocksPort: Int? = null,\n        preferences: NetworkPreferences = NetworkPreferences()\n    ): String {",
    "build signature",
)
s = s.replace("v2rayOutbound(profile, options)", "v2rayOutbound(profile, options, preferences)")
s = s.replace("trojanOutbound(profile, options)", "trojanOutbound(profile, options, preferences)")
s = s.replace("hysteria2Outbound(profile, options)", "hysteria2Outbound(profile, options, preferences)")
s = s.replace('.put("dns", protectedDns())', '.put("dns", protectedDns(preferences))')
s = s.replace('.put("inbounds", JSONArray().put(tunInbound()))', '.put("inbounds", JSONArray().put(tunInbound(preferences)))')
s = s.replace('.put(dnsOutbound())', '.put(dnsOutbound(preferences))')
s = replace_once(
    s,
    "fun summary(profile: VpnProfile): String {",
    "fun summary(profile: VpnProfile, preferences: NetworkPreferences = NetworkPreferences()): String {",
    "summary signature",
)
s = s.replace('return "${profile.connectionModeLabel} · $network · $security · MTU $TUN_MTU"', 'return "${profile.connectionModeLabel} · $network · $security · ${preferences.ipMode.label} · MTU ${preferences.validatedMtu}"')
s = s.replace("private fun tunInbound(): JSONObject", "private fun tunInbound(preferences: NetworkPreferences): JSONObject")
s = s.replace('.put("MTU", TUN_MTU)', '.put("MTU", preferences.validatedMtu)', 1)
old_dns = '''    private fun protectedDns(): JSONObject = JSONObject()
        .put("queryStrategy", "UseIPv4")
        .put("disableCache", false)
        .put(
            "hosts",
            JSONObject()
                .put("cloudflare-dns.com", JSONArray(listOf("1.1.1.1", "1.0.0.1")))
                .put("dns.google", JSONArray(listOf("8.8.8.8", "8.8.4.4")))
        )
        .put(
            "servers",
            JSONArray()
                .put(
                    JSONObject()
                        .put("address", "https://cloudflare-dns.com/dns-query")
                        .put("queryStrategy", "UseIPv4")
                        .put("skipFallback", false)
                )
                .put(
                    JSONObject()
                        .put("address", "https://dns.google/dns-query")
                        .put("queryStrategy", "UseIPv4")
                        .put("skipFallback", false)
                )
        )

    private fun dnsOutbound(): JSONObject = JSONObject()
        .put("tag", "dns-out")
        .put("protocol", "dns")
        .put(
            "settings",
            JSONObject()
                .put("network", "tcp")
                .put("address", "1.1.1.1")
                .put("port", 53)
                .put("userLevel", 8)
        )
'''
new_dns = '''    private fun protectedDns(preferences: NetworkPreferences): JSONObject {
        val strategy = preferences.ipMode.xrayQueryStrategy
        val servers = JSONArray()
        when (preferences.dnsMode) {
            DnsMode.AUTOMATIC -> {
                servers.put(dohServer("https://cloudflare-dns.com/dns-query", strategy))
                servers.put(dohServer("https://dns.google/dns-query", strategy))
            }
            DnsMode.CLOUDFLARE -> servers.put(dohServer("https://cloudflare-dns.com/dns-query", strategy))
            DnsMode.GOOGLE -> servers.put(dohServer("https://dns.google/dns-query", strategy))
            DnsMode.CUSTOM -> preferences.dnsServers().forEach { address ->
                servers.put(JSONObject().put("address", address).put("queryStrategy", strategy))
            }
        }
        return JSONObject()
            .put("queryStrategy", strategy)
            .put("disableCache", false)
            .put(
                "hosts",
                JSONObject()
                    .put("cloudflare-dns.com", JSONArray(listOf("1.1.1.1", "1.0.0.1")))
                    .put("dns.google", JSONArray(listOf("8.8.8.8", "8.8.4.4")))
            )
            .put("servers", servers)
    }

    private fun dohServer(address: String, strategy: String): JSONObject = JSONObject()
        .put("address", address)
        .put("queryStrategy", strategy)
        .put("skipFallback", false)

    private fun dnsOutbound(preferences: NetworkPreferences): JSONObject = JSONObject()
        .put("tag", "dns-out")
        .put("protocol", "dns")
        .put(
            "settings",
            JSONObject()
                .put("network", "tcp")
                .put("address", preferences.dnsServers().first())
                .put("port", 53)
                .put("userLevel", 8)
        )
'''
s = replace_once(s, old_dns, new_dns, "DNS factory")
s = s.replace(
    "private fun v2rayOutbound(profile: VpnProfile, options: Map<String, String>): JSONObject",
    "private fun v2rayOutbound(profile: VpnProfile, options: Map<String, String>, preferences: NetworkPreferences): JSONObject",
)
s = s.replace(
    "private fun trojanOutbound(profile: VpnProfile, options: Map<String, String>): JSONObject",
    "private fun trojanOutbound(profile: VpnProfile, options: Map<String, String>, preferences: NetworkPreferences): JSONObject",
)
s = s.replace(
    "private fun hysteria2Outbound(profile: VpnProfile, options: Map<String, String>): JSONObject",
    "private fun hysteria2Outbound(profile: VpnProfile, options: Map<String, String>, preferences: NetworkPreferences): JSONObject",
)
s = s.replace("streamSettings(profile, options))", "streamSettings(profile, options, preferences))")
s = s.replace("streamSettings(profile, options, forceTls = true))", "streamSettings(profile, options, preferences, forceTls = true))")
s = replace_once(
    s,
    "        options: Map<String, String>,\n        forceTls: Boolean = false",
    "        options: Map<String, String>,\n        preferences: NetworkPreferences,\n        forceTls: Boolean = false",
    "stream settings signature",
)
s = s.replace('"kcp" -> stream.put("kcpSettings", JSONObject().put("mtu", TUN_MTU))', '"kcp" -> stream.put("kcpSettings", JSONObject().put("mtu", preferences.validatedMtu))')
write(path, s)

# ---------------------------------------------------------------------------
# GhostVpnService
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/ghostnexora/vpn/service/GhostVpnService.kt"
s = read(path)
s = replace_once(
    s,
    "import com.ghostnexora.vpn.data.model.LogLevel\nimport com.ghostnexora.vpn.data.model.VpnConnectionState",
    "import com.ghostnexora.vpn.data.model.LogLevel\nimport com.ghostnexora.vpn.data.model.NetworkPreferences\nimport com.ghostnexora.vpn.data.model.VpnConnectionState",
    "service network import",
)
s = replace_once(
    s,
    "import com.ghostnexora.vpn.tunnel.StableXrayConfigFactory\nimport com.ghostnexora.vpn.tunnel.TunnelManager",
    "import com.ghostnexora.vpn.tunnel.ConnectionErrorCatalog\nimport com.ghostnexora.vpn.tunnel.StableXrayConfigFactory\nimport com.ghostnexora.vpn.tunnel.TunnelManager",
    "service error import",
)
s = replace_once(
    s,
    "    private var lastTx = 0L\n",
    "    private var lastTx = 0L\n    private var activeNetworkPreferences = NetworkPreferences()\n",
    "active preferences field",
)
s = replace_once(
    s,
    "            ensurePhysicalNetwork()\n            logSafe(LogLevel.INFO, \"Iniciando ${profile.connectionModeLabel}\", profile.id, \"VPN\")",
    "            ensurePhysicalNetwork()\n            val preferences = repository.networkPreferences.first()\n            activeNetworkPreferences = preferences\n            logSafe(\n                LogLevel.INFO,\n                \"Red VPN: ${preferences.ipMode.label} · MTU ${preferences.validatedMtu} · ${preferences.dnsMode.label}\",\n                profile.id,\n                \"SETTINGS\"\n            )\n            logSafe(LogLevel.INFO, \"Iniciando ${profile.connectionModeLabel}\", profile.id, \"VPN\")",
    "load preferences",
)
s = s.replace("val preflight = tunnelManager.verify(profile)", "val preflight = tunnelManager.verify(profile, preferences)", 1)
s = s.replace("val tun = buildTunInterface(profile)", "val tun = buildTunInterface(profile, preferences)", 1)
s = s.replace(
    'logSafe(LogLevel.INFO, "TUN activo · MTU ${StableXrayConfigFactory.TUN_MTU} · IPv4/IPv6 · rutas completas", profile.id, "NETWORK")',
    'logSafe(LogLevel.INFO, "TUN activo · MTU ${preferences.validatedMtu} · ${preferences.ipMode.label} · rutas completas", profile.id, "NETWORK")',
    1,
)
s = s.replace("tunnelRuntime = tunnelManager.start(profile, tun.fd)", "tunnelRuntime = tunnelManager.start(profile, tun.fd, preferences)", 1)
old_reconnect = '''        logSafe(LogLevel.WARNING, "$reason · iniciando reconexión protegida", profile.id, "NETWORK")
        var attempt = 0
        while (serviceScope.isActive && !intentionalDisconnect && tunInterface != null) {
            val waitMs = RECONNECT_DELAYS[attempt.coerceAtMost(RECONNECT_DELAYS.lastIndex)]
            val state = VpnConnectionState.Reconnecting(profile.name, attempt + 1, waitMs)
            updateState(state)
            updateNotification(state)

            if (!physicalNetworkAvailable) {
                delay(1_000L)
                findUsablePhysicalNetwork()?.let(::registerUnderlyingNetwork)
                continue
            }

            delay(waitMs)
            if (intentionalDisconnect || tunInterface == null) return

            val result = runCatching {
                connectionMutex.withLock {
                    tunnelManager.stop(tunnelRuntime)
                    tunnelRuntime = null
                    val tun = tunInterface ?: error("TUN no disponible durante reconexión")
                    tunnelRuntime = tunnelManager.start(profile, tun.fd)
                }
            }

            if (result.isSuccess && tunnelManager.isAlive(tunnelRuntime)) {
                reconnectCount += 1
                val connected = connectedState(profile)
                updateState(connected)
                updateNotification(connected)
                logSafe(
                    LogLevel.SUCCESS,
                    "Reconexión verificada en intento ${attempt + 1}",
                    profile.id,
                    "NETWORK"
                )
                _trafficStats.value = _trafficStats.value.copy(reconnectCount = reconnectCount)
                startHealthMonitor(profile)
                return
            }

            val error = result.exceptionOrNull()
            logSafe(
                LogLevel.WARNING,
                "Intento ${attempt + 1} fallido: ${error?.message?.take(160).orEmpty()}",
                profile.id,
                "NETWORK"
            )
            attempt += 1
        }
'''
new_reconnect = '''        val preferences = repository.networkPreferences.first()
        activeNetworkPreferences = preferences
        val maxAttempts = preferences.validatedReconnectAttempts
        logSafe(LogLevel.WARNING, "$reason · iniciando reconexión protegida · máximo $maxAttempts", profile.id, "NETWORK")
        var attempt = 0
        while (serviceScope.isActive && !intentionalDisconnect && tunInterface != null && attempt < maxAttempts) {
            val baseDelay = RECONNECT_DELAYS[attempt.coerceAtMost(RECONNECT_DELAYS.lastIndex)]
            val waitMs = baseDelay + ((attempt * 173L) % 650L)
            val state = VpnConnectionState.Reconnecting(profile.name, attempt + 1, waitMs)
            updateState(state)
            updateNotification(state)

            if (!physicalNetworkAvailable) {
                delay(1_000L)
                findUsablePhysicalNetwork()?.let(::registerUnderlyingNetwork)
                continue
            }

            delay(waitMs)
            if (intentionalDisconnect || tunInterface == null) return

            val result = runCatching {
                tunnelManager.verify(profile, preferences)
                connectionMutex.withLock {
                    tunnelManager.stop(tunnelRuntime)
                    tunnelRuntime = null
                    val tun = tunInterface ?: error("TUN no disponible durante reconexión")
                    tunnelRuntime = tunnelManager.start(profile, tun.fd, preferences)
                }
            }

            if (result.isSuccess && tunnelManager.isAlive(tunnelRuntime)) {
                reconnectCount += 1
                val connected = connectedState(profile)
                updateState(connected)
                updateNotification(connected)
                logSafe(
                    LogLevel.SUCCESS,
                    "Reconexión y salida a Internet verificadas en intento ${attempt + 1}",
                    profile.id,
                    "NETWORK"
                )
                _trafficStats.value = _trafficStats.value.copy(reconnectCount = reconnectCount)
                startHealthMonitor(profile)
                return
            }

            val error = result.exceptionOrNull() ?: IllegalStateException("Transport not alive")
            val failure = ConnectionErrorCatalog.classify(error, profile)
            logSafe(LogLevel.WARNING, "Intento ${attempt + 1}/$maxAttempts · ${failure.logMessage()}", profile.id, failure.stage)
            attempt += 1
        }

        val exhausted = "Reconnect attempts exhausted ($maxAttempts) [RECONNECT-408]"
        if (killSwitch) {
            updateState(VpnConnectionState.Error(exhausted, profile.name))
            updateNotification(VpnConnectionState.Error(exhausted, profile.name))
            logSafe(LogLevel.ERROR, "$exhausted · Kill Switch keeps traffic blocked", profile.id, "NETWORK")
        } else {
            cleanupTunnel(closeTun = true)
            updateState(VpnConnectionState.Error(exhausted, profile.name))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
'''
s = replace_once(s, old_reconnect, new_reconnect, "reconnect loop")
old_health = '''    private fun startHealthMonitor(profile: VpnProfile) {
        healthJob?.cancel()
        healthJob = serviceScope.launch {
            var ticks = 0
            while (isActive && !intentionalDisconnect) {
                delay(3_000L)
                if (connectionState.value !is VpnConnectionState.Connected) continue

                if (!tunnelManager.isAlive(tunnelRuntime)) {
                    logSafe(LogLevel.WARNING, "El transporte dejó de responder", profile.id, "CORE")
                    triggerReconnect("Fallo detectado en el transporte")
                    return@launch
                }

                ticks += 1
                if (ticks % 5 == 0) {
                    val internetCheck = runCatching { tunnelManager.verifyActive() }
                    if (internetCheck.isFailure) {
                        logSafe(
                            LogLevel.WARNING,
                            "El core sigue activo pero el servidor ya no entrega Internet",
                            profile.id,
                            "CORE"
                        )
                        triggerReconnect("Salida de Internet perdida")
                        return@launch
                    }
                }
            }
        }
    }
'''
new_health = '''    private fun startHealthMonitor(profile: VpnProfile) {
        healthJob?.cancel()
        healthJob = serviceScope.launch {
            var ticks = 0
            var consecutiveOutboundFailures = 0
            while (isActive && !intentionalDisconnect) {
                delay(5_000L)
                if (connectionState.value !is VpnConnectionState.Connected) continue

                if (!tunnelManager.isAlive(tunnelRuntime)) {
                    logSafe(LogLevel.WARNING, "El transporte dejó de responder [HEALTH-TRANSPORT]", profile.id, "CORE")
                    triggerReconnect("Fallo detectado en el transporte")
                    return@launch
                }

                ticks += 1
                if (ticks % 3 == 0) {
                    val internetCheck = runCatching { tunnelManager.verifyActive() }
                    if (internetCheck.isSuccess) {
                        consecutiveOutboundFailures = 0
                    } else {
                        consecutiveOutboundFailures += 1
                        logSafe(
                            LogLevel.WARNING,
                            "Comprobación de Internet fallida $consecutiveOutboundFailures/2 [HEALTH-OUTBOUND]",
                            profile.id,
                            "CORE"
                        )
                        if (consecutiveOutboundFailures >= 2) {
                            triggerReconnect("Salida de Internet perdida en dos comprobaciones consecutivas")
                            return@launch
                        }
                    }
                }
            }
        }
    }
'''
s = replace_once(s, old_health, new_health, "health monitor")
old_tun = '''    private fun buildTunInterface(profile: VpnProfile): ParcelFileDescriptor? = try {
        val builder = Builder()
            .setSession(profile.name)
            .setMtu(StableXrayConfigFactory.TUN_MTU)
            .addAddress("10.20.0.2", 30)
            .addAddress("fd00:20::2", 126)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("2606:4700:4700::1111")
            .setBlocking(true)
        runCatching { builder.addDisallowedApplication(packageName) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        builder.establish()
    } catch (error: Throwable) {
'''
new_tun = '''    private fun buildTunInterface(
        profile: VpnProfile,
        preferences: NetworkPreferences
    ): ParcelFileDescriptor? = try {
        val builder = Builder()
            .setSession(profile.name)
            .setMtu(preferences.validatedMtu)
            .addAddress("10.20.0.2", 30)
            .addRoute("0.0.0.0", 0)
            .setBlocking(true)
        if (preferences.ipMode.capturesIpv6) {
            builder.addAddress("fd00:20::2", 126)
            builder.addRoute("::", 0)
        }
        preferences.dnsServers().forEach { address ->
            if (preferences.ipMode.capturesIpv6 || !address.contains(':')) {
                runCatching { builder.addDnsServer(address) }
            }
        }
        runCatching { builder.addDisallowedApplication(packageName) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        builder.establish()
    } catch (error: Throwable) {
'''
s = replace_once(s, old_tun, new_tun, "TUN builder")
old_error = '''    private fun friendlyConnectionError(error: Throwable, profile: VpnProfile): String {
        val raw = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.takeIf(String::isNotBlank) }
            .joinToString(" · ")
            .take(260)
        val lower = raw.lowercase()
        val base = when {
            lower.contains("auth fail") || lower.contains("autenticación ssh") ->
                "Autenticación SSH fallida. Verifica usuario y contraseña."
            lower.contains("hostkey") || lower.contains("host key") ->
                "La identidad SSH del servidor cambió. Conexión bloqueada por seguridad."
            lower.contains("certificate") || lower.contains("certificado") || lower.contains("trust anchor") ->
                "TLS rechazó el certificado o el SNI del servidor."
            lower.contains("no entregan acceso") || lower.contains("no pudo entregar") || lower.contains("generate_204") ->
                "El perfil inició el core, pero el servidor no pudo entregar acceso a Internet. Revisa UUID/credenciales, SNI, Host, path y transporte."
            lower.contains("timeout") || lower.contains("timed out") || lower.contains("deadline exceeded") ->
                "El servidor no respondió a la prueba de Internet dentro del tiempo permitido."
            lower.contains("libv2ray") || lower.contains("xray core") || lower.contains("go_seq") ->
                "No se pudo iniciar Xray Core."
            else -> raw.ifBlank { error.javaClass.simpleName.ifBlank { "Error desconocido" } }
        }
        return "$base [${profile.connectionModeLabel}]"
    }
'''
new_error = '''    private fun friendlyConnectionError(error: Throwable, profile: VpnProfile): String {
        val failure = ConnectionErrorCatalog.classify(error, profile)
        serviceScope.launch { logSafe(LogLevel.ERROR, failure.logMessage(), profile.id, failure.stage) }
        return "${failure.userMessage()} [${profile.connectionModeLabel}]"
    }
'''
s = replace_once(s, old_error, new_error, "error catalog")
write(path, s)

# ---------------------------------------------------------------------------
# Settings UI additions
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/ghostnexora/vpn/ui/screens/settings/SettingsScreen.kt"
s = read(path)
s = replace_once(
    s,
    "import androidx.compose.material3.MaterialTheme\nimport androidx.compose.material3.OutlinedButton",
    "import androidx.compose.material3.CircularProgressIndicator\nimport androidx.compose.material3.MaterialTheme\nimport androidx.compose.material3.OutlinedButton\nimport androidx.compose.material3.OutlinedTextField",
    "settings material imports",
)
s = replace_once(
    s,
    "import com.ghostnexora.vpn.BuildConfig\n",
    "import com.ghostnexora.vpn.BuildConfig\nimport com.ghostnexora.vpn.data.model.DnsMode\nimport com.ghostnexora.vpn.data.model.IpMode\nimport com.ghostnexora.vpn.data.model.NetworkPreferences\nimport com.ghostnexora.vpn.diagnostics.DiagnosticStatus\n",
    "settings model imports",
)
s = replace_once(
    s,
    "    var showClearHosts by remember { mutableStateOf(false) }\n",
    "    var showClearHosts by remember { mutableStateOf(false) }\n    var showIpMode by remember { mutableStateOf(false) }\n    var showMtu by remember { mutableStateOf(false) }\n    var showDnsMode by remember { mutableStateOf(false) }\n    var showCustomDns by remember { mutableStateOf(false) }\n    var showReconnectLimit by remember { mutableStateOf(false) }\n    var customDnsPrimary by remember { mutableStateOf(state.networkPreferences.customDnsPrimary) }\n    var customDnsSecondary by remember { mutableStateOf(state.networkPreferences.customDnsSecondary) }\n",
    "settings dialog state",
)
anchor = '''            SettingsSection("Permissions and special access") {'''
insert = '''            SettingsSection("Connection engine") {
                ListSetting("IP mode", state.networkPreferences.ipMode.label) { showIpMode = true }
                ListSetting("TUN MTU", state.networkPreferences.validatedMtu.toString()) { showMtu = true }
                ListSetting("DNS", state.networkPreferences.dnsMode.label) { showDnsMode = true }
                if (state.networkPreferences.dnsMode == DnsMode.CUSTOM) {
                    InfoRow("Primary DNS", state.networkPreferences.customDnsPrimary)
                    InfoRow("Secondary DNS", state.networkPreferences.customDnsSecondary.ifBlank { "Not set" })
                }
                ListSetting(
                    "Reconnect attempts",
                    state.networkPreferences.validatedReconnectAttempts.toString()
                ) { showReconnectLimit = true }
                Text(
                    "IPv6 routes are created only when the selected IP mode enables them. Android and Xray always share the same MTU and DNS configuration.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM)
                )
                GhostButton(
                    text = if (state.diagnosticRunning) "Running diagnostics…" else "Run connection diagnostics",
                    onClick = viewModel::runDiagnostics,
                    enabled = !state.diagnosticRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM),
                    containerColor = NeonCyan,
                    contentColor = TextOnAccent
                )
                if (state.diagnosticRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Dimens.SpaceMD),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator(color = NeonCyan) }
                }
                state.diagnosticSteps.forEach { step ->
                    val color = when (step.status) {
                        DiagnosticStatus.PASSED -> NeonGreen
                        DiagnosticStatus.FAILED -> Color.Red
                        DiagnosticStatus.SKIPPED -> TextSecondary
                        DiagnosticStatus.RUNNING -> NeonCyan
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceMD, vertical = 6.dp)) {
                        Text("${step.id} · ${step.label}", color = color, fontWeight = FontWeight.SemiBold)
                        Text(step.detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        step.errorCode?.let { Text("Code: $it", color = color, style = MaterialTheme.typography.labelSmall) }
                        step.solution?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                state.diagnosticReport?.let { report ->
                    Text(
                        if (report.successful) "Diagnostic result: PASSED" else "Diagnostic result: FAILED",
                        color = if (report.successful) NeonGreen else Color.Red,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(Dimens.SpaceMD)
                    )
                    TextButton(onClick = viewModel::clearDiagnosticReport) { Text("Clear diagnostic result") }
                }
            }

'''
s = replace_once(s, anchor, insert + anchor, "settings connection section")
dialogs = '''
    if (showIpMode) {
        AlertDialog(
            onDismissRequest = { showIpMode = false },
            title = { Text("IP mode") },
            text = { Column { IpMode.entries.forEach { mode -> TextButton(onClick = { viewModel.setIpMode(mode); showIpMode = false }) { Text(mode.label) } } } },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showIpMode = false }) { Text("Close") } }
        )
    }

    if (showMtu) {
        AlertDialog(
            onDismissRequest = { showMtu = false },
            title = { Text("TUN MTU") },
            text = { Column { NetworkPreferences.MTU_PRESETS.forEach { value -> TextButton(onClick = { viewModel.setTunMtu(value); showMtu = false }) { Text(value.toString()) } } } },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMtu = false }) { Text("Close") } }
        )
    }

    if (showDnsMode) {
        AlertDialog(
            onDismissRequest = { showDnsMode = false },
            title = { Text("DNS mode") },
            text = { Column { DnsMode.entries.forEach { mode -> TextButton(onClick = { if (mode == DnsMode.CUSTOM) { customDnsPrimary = state.networkPreferences.customDnsPrimary; customDnsSecondary = state.networkPreferences.customDnsSecondary; showCustomDns = true } else viewModel.setDnsMode(mode); showDnsMode = false }) { Text(mode.label) } } } },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDnsMode = false }) { Text("Close") } }
        )
    }

    if (showCustomDns) {
        AlertDialog(
            onDismissRequest = { showCustomDns = false },
            title = { Text("Custom DNS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = customDnsPrimary, onValueChange = { customDnsPrimary = it }, label = { Text("Primary resolver") }, singleLine = true)
                    OutlinedTextField(value = customDnsSecondary, onValueChange = { customDnsSecondary = it }, label = { Text("Secondary resolver") }, singleLine = true)
                    Text("Use literal IPv4 or IPv6 resolver addresses.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.setCustomDns(customDnsPrimary, customDnsSecondary); showCustomDns = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showCustomDns = false }) { Text("Cancel") } }
        )
    }

    if (showReconnectLimit) {
        AlertDialog(
            onDismissRequest = { showReconnectLimit = false },
            title = { Text("Reconnect attempts") },
            text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(3, 5, 8, 12).forEach { value -> AssistChip(onClick = { viewModel.setReconnectMaxAttempts(value); showReconnectLimit = false }, label = { Text(value.toString()) }) } } },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showReconnectLimit = false }) { Text("Close") } }
        )
    }
'''
s = replace_once(s, "\n}\n\n@Composable\nprivate fun PermissionAccessRow", dialogs + "\n}\n\n@Composable\nprivate fun PermissionAccessRow", "settings dialogs")
write(path, s)

# ---------------------------------------------------------------------------
# Logs UI CreateDocument export
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/ghostnexora/vpn/ui/screens/logs/LogsScreen.kt"
s = read(path)
s = replace_once(
    s,
    "import androidx.compose.foundation.ExperimentalFoundationApi\n",
    "import androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\nimport androidx.compose.foundation.ExperimentalFoundationApi\n",
    "logs activity imports",
)
s = replace_once(
    s,
    "import androidx.compose.material.icons.filled.ContentCopy\n",
    "import androidx.compose.material.icons.filled.ContentCopy\nimport androidx.compose.material.icons.filled.SaveAlt\n",
    "logs save icon",
)
s = replace_once(
    s,
    "    val scope = rememberCoroutineScope()\n",
    "    val scope = rememberCoroutineScope()\n    val exportLauncher = rememberLauncherForActivityResult(\n        ActivityResultContracts.CreateDocument(\"text/plain\")\n    ) { uri -> uri?.let { viewModel.exportLogs(it, logs) } }\n",
    "logs export launcher",
)
s = replace_once(
    s,
    '''                    IconButton(onClick = { copyText(viewModel.exportLogsAsText(logs), "Logs copiados") }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar registros")
                    }
''',
    '''                    IconButton(onClick = { copyText(viewModel.exportLogsAsText(logs), "Logs copiados") }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar registros")
                    }
                    IconButton(onClick = { exportLauncher.launch(LogsViewModel.suggestedFileName()) }) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = "Exportar diagnóstico")
                    }
''',
    "logs export action",
)
write(path, s)

# ---------------------------------------------------------------------------
# Version bump
# ---------------------------------------------------------------------------
path = "app/build.gradle.kts"
s = read(path)
s = s.replace("versionCode = 32", "versionCode = 33", 1)
s = s.replace('versionName = "1.0.32"', 'versionName = "1.0.33"', 1)
write(path, s)

print("Phase 1 runtime integration applied")
