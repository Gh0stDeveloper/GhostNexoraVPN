# Ghost Nexora VPN

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Xray Core](https://img.shields.io/badge/Core-Xray-00A8E8)](https://github.com/XTLS/Xray-core)
[![License](https://img.shields.io/badge/License-MIT-2EA44F)](LICENSE)

Ghost Nexora VPN is a native Android VPN client focused on verified routing, encrypted profile management, secure diagnostics, and modern SSH/Xray transports. The app does not report a connection merely because a core process started: it validates the remote outbound before creating the Android TUN interface and verifies Internet access again through the active tunnel.

Current application version: **1.0.33 (33)**.

## Core capabilities

- Real Android `VpnService` TUN routing with configurable IPv4/IPv6 behavior.
- Physical network tracking across cellular data, Wi-Fi, and Ethernet.
- Preflight server validation before Android routes device traffic into the VPN.
- Active outbound verification after Xray starts.
- Automatic non-destructive connection diagnostics.
- Configurable DNS, TUN MTU, IP mode, and reconnect limit.
- Kill Switch for a previously verified VPN that loses its transport.
- Protected automatic reconnection with outbound revalidation and bounded retries.
- Structured support error codes with corrective actions.
- Local traffic statistics, latency, duration, and reconnect counters.
- Secure, filterable, and exportable diagnostic logs.

## Phase 1 stability milestone

Version 1.0.33 implements the complete first stability phase:

- physical-network, DNS, TCP, TLS/SNI, settings, and outbound diagnostics;
- IPv4-only, IPv4-preferred, and dual-stack modes;
- MTU presets from 1280 to 1500, shared by Android and Xray;
- protected automatic, Cloudflare, Google, and custom DNS;
- bounded reconnection with preflight and active outbound verification;
- stable error codes such as `TLS-004`, `SSH-401`, `ROUTE-204`, and `RECONNECT-408`;
- Android Storage Access Framework export of complete sanitized reports;
- Android Lint, unit-test, Debug/JNI, Release/R8, deprecation, and DEX validation in CI.

Detailed documentation:

- [Phase 1 stability and diagnostics](docs/PHASE-1-STABILITY.md)
- [Post-Phase 1 roadmap](docs/ROADMAP.md)

## Supported transports

### SSH family

- Direct SSH.
- SSH over TLS/SNI.
- SSH with HTTP payload.
- SSH with TLS/SNI and payload.
- SSH through HTTP CONNECT or SOCKS proxy.
- SSH with proxy, payload, and optional TLS.

SSH traffic is transported through `direct-tcpip` channels and a local SOCKS bridge. It supports TCP routing; it does not claim generic UDP tunneling over SSH.

### Xray family

- VLESS.
- VMess.
- Trojan over TLS.
- Hysteria2 over QUIC/TLS for dedicated UDP transport.
- WebSocket, gRPC, XHTTP, HTTPUpgrade, mKCP, raw TCP, TLS, and REALITY parameters where supported by the selected protocol.
- Import of `vless://`, `vmess://`, `trojan://`, `hysteria2://`, and `hy2://` links.

## Security model

### Local profile protection

Sensitive profile fields are encrypted before Room persistence with an AES-256-GCM key stored in Android Keystore. The key is non-exportable. Each encrypted field uses a random nonce and additional authenticated data bound to the profile and field identity.

### GNX2 portable configuration format

New exports use the password-protected `.gnx` format:

1. Internal JSON is compressed with GZIP.
2. A random 256-bit data key is generated for every export.
3. The payload is encrypted with AES-256-GCM.
4. PBKDF2-HMAC-SHA256 with 310,000 iterations and a random salt derives wrapping and authentication keys.
5. A separate AES-256-GCM operation wraps the random data key.
6. HMAC-SHA256 authenticates the complete container.
7. Random nonces and salts are stored with the versioned container.

There is no embedded master password or fixed master key in Kotlin or C++. IVs and nonces are not secrets; the security boundary is provided by random or password-derived keys and authenticated encryption. Plain JSON import remains available only for legacy migration.

### Native and release hardening

- Android NDK/CMake library: `libghostguard.so`.
- Native stack protection, hidden visibility, RELRO/NOW, and dead-code elimination.
- R8 minification and resource shrinking for Release builds.
- Direct JSch random-provider injection without reflective initialization.
- Explicit R8 preservation for JSch cryptographic providers and diagnostic support classes.
- Supported native ABIs: `arm64-v8a`, `armeabi-v7a`, and `x86_64`.

### Log protection

Logs are sanitized before storage, display, copying, or export. The sanitizer redacts passwords, tokens, API keys, Bearer/Basic authorization values, credentials embedded in URIs, private-key blocks, and long opaque secrets.

## Connection diagnostics

The diagnostic engine is available under **Settings > Connection engine**. It does not establish Android VPN routes while testing.

It checks:

1. Physical non-VPN network availability.
2. Server DNS resolution.
3. TCP reachability to the server or configured proxy.
4. TLS/SNI validation where applicable.
5. Current IP, DNS, MTU, and reconnect configuration.
6. Complete temporary SSH or Xray outbound preflight.
7. Real Internet response through the selected outbound.

Each failed stage provides a stable code and a corrective action. Temporary SSH sessions, SOCKS bridges, and Xray test instances are closed after the diagnostic run.

## Android permissions

The app no longer blocks first launch behind a custom permission dashboard. Android's native system UI requests the essential permissions in sequence:

- Notification permission on Android 13 and newer.
- VPN authorization through `VpnService.prepare()`.
- Battery optimization exemption for persistent VPN operation.

Special access is requested contextually instead of being forced during onboarding:

- Display over other apps is used only by the optional floating control.
- Install unknown apps is requested only when installing a verified APK update.
- File import and export use Android's Storage Access Framework without legacy storage permissions.

All permission and special-access screens can also be opened manually from **Settings > Permissions and special access**.

## Update system

Ghost Nexora VPN checks the latest stable GitHub Release at:

`Gh0stDeveloper/GhostNexoraVPN`

The updater:

- performs automatic checks at most once every 24 hours;
- allows unrestricted manual checks from Settings;
- compares explicit remote `versionCode` metadata with `BuildConfig.VERSION_CODE`;
- falls back to semantic version comparison only when versionCode metadata is absent;
- never invents a higher remote version;
- remembers a dismissed release identity so the same update is not shown on every launch;
- ignores debug, unsigned, and unaligned APK assets;
- downloads through a temporary partial file and rejects incomplete transfers;
- verifies SHA-256 when release metadata provides it;
- verifies the APK package name and requires its versionCode to be newer before opening Android's installer.

The CI release body includes `versionName`, `versionCode`, the Xray library tag, commit SHA, and APK SHA-256 so the application can make a deterministic update decision.

## Architecture

```text
app/src/main/java/com/ghostnexora/vpn/
├── data/
│   ├── local/          Room and DataStore
│   ├── model/          Profiles, network preferences, state and logs
│   └── repository/     ProfileRepository
├── diagnostics/        Non-destructive staged connection diagnostics
├── security/           Keystore encryption, GNX2, log sanitizer, SSH known hosts
├── tunnel/             SSH engine, Xray engine, routing and error catalog
├── service/            VPN and optional floating foreground services
├── update/             GitHub release discovery, validation, download and install
├── ui/                 Compose screens, components and theme
├── navigation/         Drawer and navigation graph
└── util/               Protocol parser, permissions and import/export helpers
```

The application uses MVVM, Hilt, StateFlow, Room, DataStore, Jetpack Compose, Material 3, JSch, AndroidLibXrayLite, and Android NDK/CMake.

## Building

Requirements:

- JDK 17.
- Android SDK with API 35.
- Android NDK `27.0.12077973`.
- CMake `3.22.1`.
- Gradle wrapper 8.9.
- Android Gradle Plugin 8.7.3.

AndroidLibXrayLite is downloaded by CI. For a local build, place `libv2ray.aar` in `app/libs/` or run the provided fetch script.

```bash
chmod +x gradlew
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

A production Release requires the signing environment variables used by the workflow:

```text
KEYSTORE_FILE
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

## Continuous integration

GitHub Actions performs the following checks:

- unit tests for network preferences, Xray configuration, updater logic, encryption, and error classification;
- Android Lint with errors treated as build failures;
- Debug APK and JNI compilation;
- Release compilation with R8 and resource shrinking;
- rejection of tracked Kotlin/Android deprecation warnings;
- DEX inspection for JSch providers, the direct random bridge, diagnostics, and error catalog;
- artifact upload for diagnostics, Debug, and R8 validation builds;
- signed Release publication on `main` when the APK hash changes.

The workflow uses Node.js 24-compatible official actions and publishes deterministic version metadata for the in-app updater.

## Runtime validation status

Compilation, unit tests, Android Lint, JNI packaging, R8 processing, encrypted configuration tests, updater version tests, network-setting tests, and diagnostic-class packaging are automated. Real protocol interoperability still depends on matching the remote server's credentials, transport, TLS/REALITY, SNI, Host, path, and authentication configuration.

A core reporting `running` is not treated as proof of connectivity. Ghost Nexora VPN verifies actual outbound Internet access before reporting the VPN as connected.

## Developer and contact

- Developer: **Ghost Developer**
- GitHub: [@Gh0stDeveloper](https://github.com/Gh0stDeveloper)
- Telegram: [@Gh0stDeveloper](https://t.me/Gh0stDeveloper)
- Email: [ghostnexora@gmail.com](mailto:ghostnexora@gmail.com)

## License

This project is distributed under the [MIT License](LICENSE).