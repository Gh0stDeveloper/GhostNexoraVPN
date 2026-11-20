# Ghost Nexora VPN

### Android SSH and Xray VPN client

**Project under construction**

Ghost Nexora VPN is a native Android VPN project built with Kotlin, Jetpack Compose, Android VpnService and an SSH/Xray transport stack. I am developing it with an emphasis on secure configuration storage, predictable routing behavior, clear diagnostics and strict connection-state reporting.

## Key engineering areas

- VPN service with isolated runtime and TUN routing.
- SSH connections with supported proxy, TLS/SNI and payload combinations.
- Xray-compatible transport configuration.
- Import and management of encrypted connection profiles.
- Known-host verification, platform TLS validation and protected credentials.
- Android Keystore with AES-256-GCM for sensitive locally stored data.
- Structured diagnostics with sensitive information removed from logs.
- CI checks for Android builds, native components and security regressions.

## Technology

Kotlin · Jetpack Compose · Android SDK · VpnService · Xray · SSH · C++ · GitHub Actions

## Development status

The Android client is still under active development. Code paths, test results and automated builds are not proof that every VPN protocol and recovery scenario works on every device. Connection compatibility must be validated with real servers, networks and devices before a production release.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Connection modes](docs/CONNECTION-MODES.md)
- [Security](docs/SECURITY.md)
- [SSH transport](docs/SSH-TRANSPORT.md)
- [GitHub Actions](https://github.com/Gh0stDeveloper/GhostNexoraVPN/actions)

## Build

Use the Android Gradle project with the required SDK and NDK components. Signing credentials are not stored in this repository.

---

**Ghost Nexora VPN — secure configuration and transparent connection behavior.**
