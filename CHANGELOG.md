# Changelog

All notable changes to Ghost Nexora VPN are documented here. The project follows semantic versioning for public releases, while draft PR builds may contain a newer internal `versionCode` before release.

## Unreleased

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

### Security

- Payload size, action count, individual delay, and total delay are bounded.
- Duplicate detection includes credentials and transport-security parameters without writing them to logs.
- The VPN application remains excluded from full-device and exclusion-list modes to prevent routing loops.

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