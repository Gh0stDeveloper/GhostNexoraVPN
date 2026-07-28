# Ghost Nexora VPN
<div align="center">

![Ghost Nexora VPN](https://img.shields.io/badge/Ghost%20Nexora-VPN%20Manager-00E5FF?style=for-the-badge&logo=android&logoColor=white)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Xray Core](https://img.shields.io/badge/Core-Xray-00A8E8)](https://github.com/XTLS/Xray-core)
[![License](https://img.shields.io/badge/License-MIT-2EA44F)](LICENSE)

Ghost Nexora VPN is a native Android SSH/Xray VPN client focused on verified routing, strict security defaults, encrypted profile management, actionable diagnostics, and transparent compatibility status.

A process running is not considered a successful VPN. The app verifies the remote outbound before creating Android default routes and verifies Internet access again after the active tunnel starts.

Current development version: **1.0.37 (37)**.

> The project targets a professional feature set comparable to injector-style Android VPN clients. It does not claim to be better than HTTP Injector or HTTP Custom until protocol, recovery, leak, performance, battery, and usability benchmarks are recorded.

## Current product capabilities

### Stability and routing

- Android `VpnService` TUN routing.
- Physical network tracking for cellular, Wi-Fi, and Ethernet.
- Remote outbound preflight before TUN activation.
- Active outbound verification after startup.
- IPv4-only, IPv4-preferred, and dual-stack modes.
- MTU presets from 1280 to 1500 shared by Android and Xray.
- Automatic protected, Cloudflare, Google, and custom DNS.
- Kill Switch and bounded protected reconnection.
- Tunnel-only live traffic counters read from the Xray proxy outbound.
- Structured support error codes and corrective actions.
- Application split tunneling: all, only selected, or exclude selected apps.
- Fail-closed validation for empty or stale application allowlists.

### Diagnostics and logs

- Non-destructive staged diagnostics for physical network, DNS, TCP, TLS/SNI, settings, transport preflight, and real Internet response.
- Single sanitized timeline-style log console.
- Debug, Info, Warning, Error, and Success levels.
- Search, filtering, copy, clear, and complete UTF-8 diagnostic export.
- Device/version/ABI metadata and structured error codes in reports.
- In-app compatibility matrix distinguishing CI verified, device testing pending, and experimental features.

### SSH and payloads

- Direct SSH.
- SSH over strict TLS/SNI or per-profile HTTP Custom-compatible SNI.
- SSH with HTTP payload.
- SSH with TLS and payload.
- SSH through HTTP CONNECT or SOCKS5 proxy.
- Proxy/payload/TLS combinations represented by explicit modes.
- Loopback SOCKS bridge using JSch `direct-tcpip` channels.
- Advanced payload templates: CONNECT, GET, POST, HEAD, and WebSocket Upgrade.
- Variables, exact CRLF preview, split writes, bounded delays, rotation, and random token generation.
- Payload syntax validation before profile storage and before socket transmission.

SSH currently carries TCP through the local SOCKS bridge. Generic UDP-over-SSH, production private-key authentication, and authenticated upstream proxies remain documented roadmap items.

### Xray family

- VLESS and VMess.
- Trojan over TLS.
- Hysteria2/QUIC transport.
- TCP, WebSocket, gRPC, XHTTP, HTTPUpgrade, mKCP, TLS, and REALITY parameters where supported by Xray and the selected protocol.
- Explicit TUN-to-proxy and DNS routing with no silent direct fallback.
- Import of standard Xray outbound JSON.

Configuration generation and packaging are automated. Exact real-server combinations remain subject to the physical test matrix.

### Import, export, and profiles

- `vmess://`
- `vless://`
- `trojan://`
- `hysteria2://` and `hy2://`
- `ssh://` Ghost Nexora import convention
- standard Xray outbound JSON
- QR, clipboard, and Android file picker
- GNX2 password-protected encrypted format
- legacy JSON migration
- technical preview before import
- duplicate-safe merge and replace using security-relevant fingerprints
- profile search, filters, favorites, duplication, tags, and notes

### Security

- AES-256-GCM profile protection with Android Keystore.
- GNX2 authenticated encryption with random keys, nonces, and salts.
- Strict TLS by default, with an explicit per-profile SSH SNI compatibility mode; no global trust-all mode.
- Persistent SSH known-host verification.
- Direct Android `SecureRandom` injection into JSch.
- Sanitization before log storage, display, copy, and export.
- `FLAG_SECURE` on create/edit profile and import/export screens.
- R8/resource shrinking and native hardening.
- Scoped package visibility for the split-tunneling selector.
- Manifest, token-pattern, native ABI, DEX, Lint, Debug, and Release checks in CI.
- Weekly Gradle and GitHub Actions dependency monitoring.

## Documentation

| Document | Scope |
|---|---|
| [Architecture](docs/ARCHITECTURE.md) | runtime layers, state machine, invariants |
| [Connection modes](docs/CONNECTION-MODES.md) | implemented chains and status |
| [SSH transport](docs/SSH-TRANSPORT.md) | TLS, payload, JSch, SOCKS pipeline |
| [Payload engine](docs/PAYLOAD-ENGINE.md) | variables, controls, limits, examples |
| [Xray/V2Ray](docs/XRAY-V2RAY.md) | VLESS, VMess, TLS, REALITY, routing |
| [Trojan and Hysteria2](docs/TROJAN-HYSTERIA2.md) | parameters, limitations, tests |
| [DNS and routing](docs/DNS-AND-ROUTING.md) | IP modes, DNS, MTU, application rules |
| [Split tunneling](docs/SPLIT-TUNNELING.md) | allow/exclude behavior and security |
| [Security architecture](docs/SECURITY.md) | threat model and controls |
| [Configuration formats](docs/CONFIG-FORMAT.md) | GNX2, links, JSON, fingerprints |
| [Import and export](docs/IMPORT-EXPORT.md) | preview, merge, replace, limits |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | error codes and corrective actions |
| [Compatibility](docs/COMPATIBILITY.md) | evidence labels and current status |
| [Test matrix](docs/TEST-MATRIX.md) | required real-device/server evidence |
| [Build and release](docs/BUILD-AND-RELEASE.md) | toolchain, CI, signing, release gate |
| [Privacy](docs/PRIVACY.md) | local data, network behavior, permissions |
| [Phase 1](docs/PHASE-1-STABILITY.md) | stability milestone |
| [Roadmap](docs/ROADMAP.md) | completed and planned phases |
| [Changelog](CHANGELOG.md) | version history |
| [Security policy](SECURITY.md) | private vulnerability reporting |

## Connection acceptance criteria

A normal connection must complete:

1. Profile validation.
2. Physical non-VPN network detection.
3. Network and application-routing settings validation.
4. Remote SSH/Xray outbound preflight.
5. Android TUN creation.
6. SSH/Xray startup against the TUN descriptor.
7. Active outbound Internet verification.
8. Connected-state publication.
9. Health and session-statistics monitoring.

A failure before TUN creation does not change device default routes. A failure after TUN creation closes the interface unless Kill Switch intentionally retains blocked routing during recovery.

## GNX2 encrypted configuration

GNX2 exports:

1. serialize and compress profile data;
2. generate a random 256-bit data key;
3. encrypt with AES-256-GCM;
4. derive wrapping/authentication keys with PBKDF2-HMAC-SHA256 and a random salt;
5. wrap the data key with a separate authenticated operation;
6. authenticate the versioned container with HMAC-SHA256.

There is no embedded master password or fixed export key. Client-side protection increases resistance but cannot make actively used server details impossible to recover on a device fully controlled by an attacker.

## Android permissions

Essential native prompts:

- notification permission on Android 13+;
- VPN authorization through `VpnService.prepare()`;
- optional battery-optimization exemption.

Contextual special access:

- overlay only for floating controls;
- package installation only for verified updates;
- boot receiver only for configured reconnect behavior.

Import/export uses Android's Storage Access Framework. Broad legacy storage and unrestricted package-list permissions are not requested.

## Update system

The updater:

- checks the latest stable GitHub Release;
- rate-limits automatic checks;
- allows manual checks;
- compares version code and version name;
- remembers dismissed release identity;
- ignores debug/unsigned/unaligned assets;
- uses a temporary partial download;
- validates package name and newer version;
- verifies SHA-256 when release metadata supplies it.

Production release metadata now includes the APK SHA-256 and signing-certificate SHA-256. Enforcing an expected signer digest inside the updater remains a release-hardening roadmap item until the production signing identity is fixed.

## Architecture

```text
app/src/main/java/com/ghostnexora/vpn/
├── data/          Room, DataStore, profiles and routing preferences
├── diagnostics/   staged non-destructive diagnostics
├── security/      Keystore, GNX2, sanitizer and SSH known hosts
├── tunnel/        SSH, payloads, Xray, configuration and errors
├── service/       Android VPN and floating foreground services
├── update/        release discovery, verification and installation
├── ui/            Compose screens and compatibility/routing tools
├── navigation/    drawer and route graph
└── util/          import parsers, fingerprints and helpers
```

## Build

Requirements:

- JDK 17
- Android SDK 35
- NDK `27.0.12077973`
- CMake `3.22.1`
- Gradle wrapper 8.9
- Android Gradle Plugin 8.7.3

```bash
chmod +x gradlew
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

AndroidLibXrayLite is pinned and downloaded by CI. Local builds must place the expected AAR in `app/libs/`.

## CI validation

The workflow checks:

- manifest security policy;
- high-confidence token patterns;
- unit tests;
- dependency inventory;
- Android Lint;
- Debug/JNI build;
- required native ABIs;
- Release/R8 and resource shrinking;
- tracked deprecation warnings;
- required JSch, diagnostics, payload, parser, and split-routing classes in Release DEX;
- artifact upload;
- signed release metadata on `main`.

## Runtime validation status

Automated checks prove build and configuration properties, not universal server interoperability. The Compatibility screen and `docs/TEST-MATRIX.md` are authoritative about what still requires a real Android device and remote server.

## Developer and contact

- Developer: **Ghost Developer**
- GitHub: [@Gh0stDeveloper](https://github.com/Gh0stDeveloper)
- Telegram: [@Gh0stDeveloper](https://t.me/Gh0stDeveloper)
- Email: [ghostnexora@gmail.com](mailto:ghostnexora@gmail.com)

## License

This project is distributed under the [MIT License](LICENSE).
