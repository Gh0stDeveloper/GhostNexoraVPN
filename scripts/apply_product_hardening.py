from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"Expected block not found in {path}: {old[:100]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app/src/main/java/com/ghostnexora/vpn/tunnel/SshTunnelEngine.kt",
    "import com.ghostnexora.vpn.data.model.VpnProfile\nimport com.jcraft.jsch.ChannelDirectTCPIP",
    "import com.ghostnexora.vpn.data.model.VpnProfile\n"
    "import com.ghostnexora.vpn.util.PayloadAction\n"
    "import com.ghostnexora.vpn.util.PayloadContext\n"
    "import com.ghostnexora.vpn.util.PayloadEngine\n"
    "import com.jcraft.jsch.ChannelDirectTCPIP",
)

replace_once(
    "app/src/main/java/com/ghostnexora/vpn/tunnel/SshTunnelEngine.kt",
    '''        val payload = renderPayload(profile.payload, host, port, profile.sni.ifBlank { host })
        require(payload.isNotBlank()) { "El payload no puede estar vacío" }

        socket.getOutputStream().apply {
            write(payload.toByteArray(Charsets.UTF_8))
            flush()
        }

        val input = PushbackInputStream(socket.getInputStream(), MAX_HANDSHAKE_BYTES)
        if (!looksLikeHttpPayload(payload)) return input
''',
    '''        val plan = PayloadEngine.compile(
            raw = profile.payload,
            context = PayloadContext(
                host = host,
                port = port,
                sni = profile.sni.ifBlank { host },
                proxyHost = profile.proxy.host,
                proxyPort = profile.proxy.port
            )
        )
        val output = socket.getOutputStream()
        plan.actions.forEach { action ->
            when (action) {
                is PayloadAction.Send -> {
                    output.write(action.text.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
                is PayloadAction.Delay -> Thread.sleep(action.millis)
            }
        }

        val input = PushbackInputStream(socket.getInputStream(), MAX_HANDSHAKE_BYTES)
        if (!looksLikeHttpPayload(plan.rendered)) return input
''',
)

replace_once(
    "app/src/main/java/com/ghostnexora/vpn/tunnel/SshTunnelEngine.kt",
    '''    private fun renderPayload(raw: String, host: String, port: Int, sni: String): String = raw
        .replace("[host_port]", "$host:$port", ignoreCase = true)
        .replace("[host]", host, ignoreCase = true)
        .replace("[port]", port.toString(), ignoreCase = true)
        .replace("[sni]", sni, ignoreCase = true)
        .replace("[crlf]", "\\r\\n", ignoreCase = true)
        .replace("[lf]", "\\n", ignoreCase = true)
        .replace("[cr]", "\\r", ignoreCase = true)

''',
    "",
)

replace_once(
    "app/src/main/java/com/ghostnexora/vpn/service/GhostVpnService.kt",
    "import com.ghostnexora.vpn.data.model.ConnectionMode\n",
    "import com.ghostnexora.vpn.data.model.AppRoutingMode\n"
    "import com.ghostnexora.vpn.data.model.AppRoutingPreferences\n"
    "import com.ghostnexora.vpn.data.model.ConnectionMode\n",
)

replace_once(
    "app/src/main/java/com/ghostnexora/vpn/service/GhostVpnService.kt",
    '''            val preferences = repository.networkPreferences.first()
            activeNetworkPreferences = preferences
            logSafe(
                LogLevel.INFO,
                "Red VPN: ${preferences.ipMode.label} · MTU ${preferences.validatedMtu} · ${preferences.dnsMode.label}",
                profile.id,
                "SETTINGS"
            )
''',
    '''            val preferences = repository.networkPreferences.first()
            val appRouting = repository.appRoutingPreferences.first()
            require(appRouting.isValid) {
                "App-routing invalid: select at least one selected application [APP-ROUTE-001]"
            }
            activeNetworkPreferences = preferences
            logSafe(
                LogLevel.INFO,
                "Red VPN: ${preferences.ipMode.label} · MTU ${preferences.validatedMtu} · ${preferences.dnsMode.label}",
                profile.id,
                "SETTINGS"
            )
            logSafe(
                LogLevel.INFO,
                "Aplicaciones: ${appRouting.mode.label} · ${appRouting.normalizedPackages.size} regla(s)",
                profile.id,
                "APP_ROUTING"
            )
''',
)

replace_once(
    "app/src/main/java/com/ghostnexora/vpn/service/GhostVpnService.kt",
    "            val tun = buildTunInterface(profile, preferences) ?: error(\"Android no pudo establecer la interfaz VPN\")",
    "            val tun = buildTunInterface(profile, preferences, appRouting) ?: error(\"Android no pudo establecer la interfaz VPN\")",
)

replace_once(
    "app/src/main/java/com/ghostnexora/vpn/service/GhostVpnService.kt",
    '''    private fun buildTunInterface(
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
        serviceScope.launch {
            logSafe(LogLevel.ERROR, "Error creando TUN: ${error.message}", profile.id, "NETWORK")
        }
        null
    }
''',
    '''    private fun buildTunInterface(
        profile: VpnProfile,
        preferences: NetworkPreferences,
        appRouting: AppRoutingPreferences
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
        applyAppRouting(builder, appRouting)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        builder.establish()
    } catch (error: Throwable) {
        serviceScope.launch {
            logSafe(LogLevel.ERROR, "Error creando TUN: ${error.message}", profile.id, "NETWORK")
        }
        null
    }

    private fun applyAppRouting(builder: Builder, preferences: AppRoutingPreferences) {
        val packages = preferences.normalizedPackages.filterNot { it == packageName }
        when (preferences.mode) {
            AppRoutingMode.ALL -> {
                runCatching { builder.addDisallowedApplication(packageName) }
            }
            AppRoutingMode.ONLY_SELECTED -> {
                require(packages.isNotEmpty()) {
                    "App-routing invalid: no selected application [APP-ROUTE-001]"
                }
                val applied = packages.count { selectedPackage ->
                    runCatching {
                        builder.addAllowedApplication(selectedPackage)
                        true
                    }.getOrDefault(false)
                }
                require(applied > 0) {
                    "App-routing invalid: selected application packages were not found [APP-ROUTE-404]"
                }
            }
            AppRoutingMode.EXCLUDE_SELECTED -> {
                runCatching { builder.addDisallowedApplication(packageName) }
                packages.forEach { selectedPackage ->
                    runCatching { builder.addDisallowedApplication(selectedPackage) }
                }
            }
        }
    }
''',
)

replace_once(
    "app/src/main/java/com/ghostnexora/vpn/ui/MainActivity.kt",
    '''    route == Screen.CreateProfile.route -> "New Profile"
    route.startsWith("edit_profile") -> "Edit Profile"
    route == Screen.Import.route -> "Import Profiles"
''',
    '''    route == Screen.CreateProfile.route -> "New Profile"
    route.startsWith("edit_profile") -> "Edit Profile"
    route == Screen.AppRouting.route -> "Application Routing"
    route == Screen.Compatibility.route -> "Compatibility"
    route == Screen.Import.route -> "Import Profiles"
''',
)

replace_once(
    "app/src/main/java/com/ghostnexora/vpn/util/JsonManager.kt",
    '''    private fun parseImportText(rawText: String): ImportResult {
        if (rawText.isBlank()) return ImportResult.Error("El archivo está vacío")
        parseJson(rawText)?.let { return it }

        val protocolProfiles = ProtocolLinkParser.parseText(rawText)
''',
    '''    private fun parseImportText(rawText: String): ImportResult {
        if (rawText.isBlank()) return ImportResult.Error("El archivo está vacío")
        val trimmed = rawText.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val xrayProfiles = ProtocolLinkParser.parseXrayJson(trimmed)
            if (xrayProfiles.isNotEmpty()) {
                return ImportResult.Success(xrayProfiles, "Configuración JSON Xray")
            }
            parseJson(trimmed)?.let { return it }
        }

        val protocolProfiles = ProtocolLinkParser.parseText(rawText)
''',
)

replace_once(
    "app/src/main/java/com/ghostnexora/vpn/util/JsonManager.kt",
    '''                rawText.contains("hysteria2://", true) || rawText.contains("hy2://", true) -> "Enlaces Hysteria2"
                else -> "Enlaces compatibles"
''',
    '''                rawText.contains("hysteria2://", true) || rawText.contains("hy2://", true) -> "Enlaces Hysteria2"
                rawText.contains("ssh://", true) -> "Enlaces SSH"
                else -> "Enlaces compatibles"
''',
)

print("Product hardening patches applied")
