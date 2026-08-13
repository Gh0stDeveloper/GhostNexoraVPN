# Changelog

All notable changes to Ghost Nexora VPN are documented here. The project follows semantic versioning for public releases, while draft PR builds may contain a newer internal `versionCode` before release.

## 1.0.51

### Fixed

- `Connected` is now published only after the TUN descriptor is valid, Xray/transport remain alive, and Android exposes an owned `TRANSPORT_VPN` network. If Android does not register the VPN, startup fails instead of showing a false connected state without the system VPN indicator.
- SSH authentication banners are forwarded to the connection log. The client also opens a short-lived shell channel on the same authenticated SSH session to capture the server MOTD without creating another TCP/TLS/SSH connection.
- The Android VPN configuration now includes its activity intent and selected physical underlying network before `Builder.establish()`.

### Changed

- Removed the automatic `1/2` Cloudflare/Google startup probe, periodic outbound probes, loopback health-check inbound, and ten-second TCP latency sockets from normal VPN sessions. Real application traffic is now the only traffic that opens SSH `direct-tcpip` forwarding channels.
- Health monitoring is passive: it checks the existing SSH/Xray runtime and Android's owned VPN registration without contacting remote test endpoints.
- Bumped the application to `1.0.51 (51)`.

## 1.0.50

### Fixed

- Native Xray startup callbacks no longer perform blocking Room/DataStore writes on AndroidLibXrayLite's callback thread.
- Core, SSH, SOCKS and TUN events are persisted through a dedicated FIFO writer, allowing `startLoop()` to return after `Started successfully, running` and the VPN service to publish `Connected`.
- Disconnect remains available while queued diagnostic events finish writing independently of the VPN lifecycle thread.

### Changed

- Bumped the application to `1.0.50 (50)`.

## 1.0.49

### Fixed

- `SSH + SSL/SNI` compatibility now accepts private, self-signed, or incomplete certificate chains instead of failing with `Trust anchor for certification path not found`.
- The configured SSH host remains the physical TCP endpoint and SSH identity, while the configured SNI remains an independent TLS `ClientHello` name.
- Compatibility remains scoped to profiles that explicitly enable the HTTP Injector/Custom policy; strict TLS still uses Android's trust store and hostname verification.
- Long creator notes on the main dashboard now keep their complete sanitized HTML document inside a dedicated touch-scroll surface instead of having gestures intercepted by the surrounding page.

### Security

- Compatibility requires a non-empty, currently valid X.509 leaf certificate and keeps persistent SSH host-key verification as the final server identity check.
- The interface and technical summary now disclose that the compatibility policy does not authenticate the TLS certificate authority or SNI/SAN relationship.
- Creator-note scrolling cooperates with the parent Compose page, keeps its vertical scrollbar visible, and hands scrolling back to the dashboard at the document edges.

### Changed

- Bumped the application to `1.0.49 (49)`.

## 1.0.43

### Fixed

- SSH, SSH + SSL/SNI, payload and proxy transports now resolve the configured transport host through Android physical networks marked `NOT_VPN`.
- Every outgoing SSH/proxy socket is bound to the selected physical network before connecting, adding a second routing guarantee beyond the mandatory application self-bypass.
- The transport now tries every resolved A/AAAA address within a bounded global timeout instead of failing permanently on Android's first selected IP.
- The configured SSH host remains the TCP destination while the configured SNI remains an independent TLS `ClientHello` name, matching injector-style profiles where both domains differ.

### Changed

- IPv4 addresses are attempted before IPv6 for the current IPv4-preferred behavior, while IPv6 remains available as a fallback.
- Connection logs now show physical DNS results, per-IP attempts, selected physical transport and the independent TLS SNI stage.
- Bumped the application to `1.0.43 (43)`.

## 1.0.42

### Fixed

- Replaced Android-ICU-incompatible CSS regular expressions that could throw `PatternSyntaxException` and `ExceptionInInitializerError` when opening creator notes.
- Added defensive HTML fallback rendering so malformed note content cannot close the application.
- Improved the TCP refusal explanation without treating a different SNI as an invalid SSH + SSL profile.

### Changed

- Expanded safe creator-note formatting while keeping JavaScript, events, forms, frames, remote resources, files and WebView network access disabled.
- Added a CI version policy requiring both `versionCode` and `versionName` to increase for every pull request.
- Bumped the application to `1.0.42 (42)`.

## 1.0.41

### Added

- Added GNX3 individual configuration export directly from each editable profile.
- Added creator-controlled locking that hides and disables editing, duplication, diagnostics, and re-export of server, SSH, TLS/SNI, proxy, payload, and method parameters.
- Added optional creator passwords and a passwordless app-managed compatibility mode for GNX3 files.
- Added creator notes with safe HTML/CSS formatting, tables, and external contact links.
- Added GNX3 import from file, clipboard, and QR text envelopes with a masked preview for locked profiles.

### Security

- GNX3 uses a random data key, AES-256-GCM payload encryption, a separate authenticated key-wrapping operation, HMAC-SHA256 container authentication, random salts/nonces, and PBKDF2-HMAC-SHA256 for creator passwords.
- Locked profiles are immediately resealed as a single opaque AES-GCM envelope backed by a non-exportable Android Keystore key; their network fields remain empty in Room and normal UI flows.
- Locked-profile transport logs redact server, credentials, SNI, payload, proxy, method, and sensitive tunnel stages.
- HTML notes are allowlist-sanitized and rendered with JavaScript, storage, files, content access, network resources, frames, forms, and mixed content disabled.
- Added debugger/instrumentation signals before locked-profile decryption, native buffer wiping, broader `FLAG_SECURE` coverage, and Release/R8 survival gates.

### Changed

- Room schema migrated from 3 to 4 without altering existing editable profiles.
- Bumped the application to `1.0.41 (41)`.
- Documented the security boundary accurately: password protection is stronger for offline sharing, while no Android client can make actively used plaintext or an app-managed compatibility secret unrecoverable on a fully controlled device.

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
