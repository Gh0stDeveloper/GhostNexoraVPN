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

## Exportación de perfiles GNX4

Cada exportación individual o por lote produce un archivo binario `.gnx`. La capa
exterior XChaCha20-Poly1305 se ejecuta en C++ y protege el encabezado. La clave
interna es fija entre compilaciones de distribución y el nonce es nuevo por
archivo. La contraseña del usuario y la protección ligada a la instalación
siguen usando sus capas internas. La importación conserva los formatos anteriores.

Para los APK con firma persistente, configure el secreto `GNX4_EXPORT_KEY` en
GitHub Actions. Debe contener 32 bytes aleatorios codificados en 64 dígitos
hexadecimales. Conserve el mismo valor entre versiones para mantener la lectura
de las exportaciones existentes. En una compilación firmada local, ejecute
`python3 scripts/generate-gnx4-key-shares.py DIRECTORIO_PRIVADO` con ese secreto
en el entorno y pase el directorio al build mediante `GNX4_KEY_SOURCE_DIR`.
Las compilaciones de desarrollo usan fragmentos públicos para las pruebas.

La fragmentación XOR y las secciones nativas dificultan la inspección superficial
del APK. El cliente distribuido puede reconstruir la clave. Use la contraseña
opcional cuando necesite que el archivo dependa también de un secreto del usuario.
