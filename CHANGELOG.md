# Changelog

All notable changes to Ghost Nexora VPN are documented here. The project follows semantic versioning for public releases, while draft PR builds may contain a newer internal `versionCode` before release.

## 1.0.40

### Fixed

- Fixed the remaining SSH SOCKS half-close race. End-of-input from Xray now closes only the client-to-SSH uplink and keeps the direct-tcpip channel alive until the remote response finishes, preventing the response-side `io: read/write on closed pipe`.
- Replaced the SSH readiness dependency on AndroidLibXrayLite's in-memory `CoreController.measureDelay()` pipe with a loopback-only Xray SOCKS probe. The probe performs a real remote TLS handshake through `Xray → SOCKS → direct-tcpip SSH` before publishing `Connected`.
- SSH bridge profiles now use `AsIs` routing so destination names are resolved through the SSH server instead of leaking or stalling on the physical Android network.
- Protected TCP DNS is explicitly detoured through the selected SSH SOCKS outbound.

### Changed

- Added separate, sanitized SOCKS lifecycle events for channel open, uplink, downlink, remote TLS verification, warnings, and explicit user-requested disconnects.
- Bumped the application to `1.0.40 (40)`.

## 1.0.39

### Fixed

- Fixed the local SSH SOCKS bridge deadlock that produced `io: read/write on closed pipe` after TLS/SNI and SSH authentication had already succeeded. Every JSch channel block is now flushed while the connection is active instead of only after the SOCKS client closes.
- Bound the local bridge explicitly to IPv4 `127.0.0.1`, matching the Xray SOCKS endpoint on Android runtimes that otherwise select IPv6 loopback.
- Classified post-authentication failures as SSH/SOCKS forwarding failures for SSH profiles. SSH + SSL diagnostics no longer suggest unrelated V2Ray/Trojan UUID, path, or service-name fields.
- Added bridge regression coverage that requires every copied chunk to be flushed before end-of-input.

### Changed

- The first successfully forwarded block now emits a sanitized `SOCKS` stage event, while direct-tcpip channel-open failures retain their concrete SSH cause in the connection log.

## 1.0.38

### Added

- Added a complete real-time connection timeline for physical network, TCP, proxy, TLS/SNI, payload, SSH, SOCKS, Xray, TUN, DNS, routing, and active Internet verification stages.
- Added explicit same-application runtime state and traffic updates between the private VPN process and the dashboard.
- Added bounded automatic recovery when Android recreates the native VPN process.

### Fixed

- Removed the duplicate SSH/Xray preflight from `DashboardViewModel`; the UI process no longer initializes or tears down the native core immediately before the service starts it again.
- Moved `GhostVpnService` and its floating control into the private `:vpn` process so a fatal native abort cannot close the application interface.
- Initialized AndroidLibXrayLite once per VPN process and copied `geoip.dat`/`geosite.dat` atomically into the exact directory passed to the native runtime.
- Kept Room logs and DataStore recovery state coherent across the UI and VPN processes.
- Preserved connection-stage order when multiple log events share the same millisecond timestamp.
- Restored the platform `VpnService` binder callback so Android can revoke and close the tunnel through the supported system interface.

### Changed

- A normal connection now establishes a fail-closed TUN inside the isolated service, starts one SSH/Xray runtime, and publishes `Connected` only after the active outbound delivers real Internet.
- Connection-stage messages remain sanitized before storage, display, copy, or export.

## 1.0.37

### Added

- Added a per-profile `Compatible con HTTP Custom` policy for SSH + SSL and SSH + SSL + Payload.
- The compatible policy sends the configured SNI without requiring an SNI/SAN match while retaining platform certificate-chain validation and persistent SSH host-key verification.
- SSH links and GNX2/legacy JSON now preserve the selected TLS verification policy.

### Fixed

- Preserved JSch key-exchange, cipher, MAC, and authentication providers loaded through class names so Release/R8 can no longer remove `DHEC256`, `DHGEX256`, `AES256CTR`, `HMACSHA512`, or password authentication.
- Added an early JSch runtime check and Release DEX gates for the algorithms used by the tested HTTP Custom server.
- Payload-only SSH now stops waiting as soon as a direct SSH banner is received instead of delaying until the HTTP-header timeout.
- Connection errors now include the complete bounded cause chain.

### Changed

- SSH + SSL remains `TCP → TLS → SSH`.
- SSH + SSL + Payload remains `TCP → TLS → payload → SSH`, matching the successful HTTP Custom log supplied during interoperability testing.
- Existing profiles migrate safely with TLS strict as their default; compatibility must be enabled explicitly.

## 1.0.36

### Fixed

- Fixed the Release/R8 startup crash in the navigation drawer. The drawer model is now computed only after all `Screen` singletons finish class initialization, preventing a null dashboard entry.

### Changed

- Added a regression test that reproduces the application launch order and verifies every drawer route.
- Release validation now rejects any R8 mapping that reintroduces a static `Screen.drawerItems` backing list.

## 1.0.35

### Added

- Application split tunneling with three modes: all applications, only selected applications, and exclude selected applications.
- Installed-application selector with search, package visibility declaration, and fail-closed validation for empty allowlists.
- Advanced segmented payload engine with templates, validated variables, `[split]`, bounded `[delay=N]`, `[rotate]`, and `[random]`.
- Exact CRLF preview and payload syntax validation before a profile can be saved.
- `ssh://` import.
- Standard Xray JSON outbound import for VLESS, VMess, Trojan, and Hysteria2.
- Technical import preview showing protocol, server, transport, security, SNI, Host, path, service name, proxy, and warnings.
- Duplicate-safe import using a security-relevant SHA-256 profile fingerprint.
- Compatibility matrix screen that distinguishes CI verification, device testing pending, and experimental support.
- Profile manager search, filters, favorites, duplication, and richer transport metadata.
- Unit tests for payload compilation, import parsers, duplicate detection, and split-tunneling preference validation.

### Changed

- SSH payload transmission now executes an explicit sequence of send and delay actions instead of writing one unvalidated string.
- Android TUN application rules are created before `Builder.establish()` and cannot silently fall back from an empty allowlist to all applications.
- JSON Xray parsing runs before legacy Ghost Nexora JSON parsing.
- Connection errors now include application-routing codes `APP-ROUTE-001` and `APP-ROUTE-404`.
- Hysteria2 obfuscation, bandwidth, and port hopping now use Xray 26.5's canonical `finalmask` structure.
- Session traffic counters now read the Xray proxy outbound instead of counting every byte used by the Android process.
- Coroutines Android and test artifacts are pinned together at 1.9.0 for the Kotlin 2.0/KSP metadata level.

### Fixed

- Dashboard preflight now uses the selected IP, DNS, and MTU preferences.
- The VPN foreground notification starts synchronously before profile/database work.
- Only-selected application routing now rejects the entire stale list when any selected package is unavailable.
- The SSH strategy now recognizes the payload + proxy + TLS mode.
- Dependabot no longer proposes an isolated Coroutines update that cannot compile with the current Kotlin toolchain.

### Security

- Payload size, action count, individual delay, and total delay are bounded.
- Duplicate detection includes credentials and transport-security parameters without writing them to logs.
- The VPN application remains excluded from full-device and exclusion-list modes to prevent routing loops.
- A partially stale only-selected allowlist cannot silently widen or change the intended application scope.

## 1.0.33

### Added

- Non-destructive connection diagnostics.
- IPv4-only, IPv4-preferred, and dual-stack modes.
- Configurable protected DNS and synchronized Android/Xray MTU.
- Structured connection error catalog.
- Bounded protected reconnection.
- Sanitized full diagnostic export.
- Android Lint and Release DEX verification in CI.

### Fixed

- JSch random-provider initialization on Android/R8.
- False-positive connected state when Xray started without usable Internet.
- Android TUN activation before remote outbound verification.
- Invalid manifest request for `BIND_VPN_SERVICE`.
- Legacy external-storage permissions.

## 1.0.30–1.0.32

- Stabilized physical-network tracking and Xray outbound checks.
- Added direct JSch random-provider injection and DEX validation.
- Added deterministic TUN-to-proxy routing and protected DNS interception.

## Earlier development builds

Earlier builds established the Android Compose interface, profile storage, SSH/Xray transport foundation, GNX encrypted configuration format, updater, logs, floating control, and native hardening. These builds were development milestones and are not treated as protocol interoperability certification.
