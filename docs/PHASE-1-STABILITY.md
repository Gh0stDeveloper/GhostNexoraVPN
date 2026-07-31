# Phase 1 — Runtime Stability and Diagnostics

This document describes the first production-hardening phase implemented in Ghost Nexora VPN 1.0.33.

> Runtime note for 1.0.38: non-destructive diagnostics still use the preflight described below. Normal connection startup now owns a single native runtime in the private `:vpn` process, establishes a fail-closed TUN, and verifies the active outbound before publishing `Connected`.

## Goals

Phase 1 focuses on connection correctness before expanding protocol features. The application must not report a successful VPN merely because a process started or Android created a TUN interface. A connection is considered usable only after the selected SSH/Xray outbound delivers real Internet access.

## Implemented features

### Automatic connection diagnostics

Settings now includes **Run connection diagnostics**. The diagnostic engine is non-destructive: it does not install Android VPN routes and does not interrupt the device's normal Internet connection.

The engine executes these stages:

1. Physical network availability.
2. Server DNS resolution.
3. TCP reachability to the server or configured proxy.
4. TLS/SNI certificate validation when the profile uses a direct TLS transport.
5. Current IP, DNS and MTU configuration validation.
6. Complete SSH or Xray outbound preflight.
7. Real Internet response through the remote outbound.

The final preflight creates only the temporary transport required for testing. SSH sessions, local SOCKS bridges and Xray test instances are closed after the check.

Each result includes:

- stage identifier;
- passed, failed or skipped state;
- latency where available;
- stable error code;
- corrective action.

### Configurable IP mode

The global IP mode can be changed under **Settings > Connection engine**.

Available modes:

- **IPv4 only** — Android does not create an IPv6 TUN address or IPv6 default route.
- **IPv4 preferred** — Android captures IPv4 and IPv6, while DNS resolution prefers IPv4.
- **IPv4 + IPv6** — Android captures both families and Xray can resolve both A and AAAA records.

IPv4-only mode is recommended when a server, mobile provider or transport cannot route IPv6 correctly.

### Configurable MTU

Android and Xray now always use the same TUN MTU. Presets:

- 1280
- 1360
- 1400
- 1450
- 1500

The default is 1400. Lower values can improve compatibility on mobile networks, QUIC, nested TLS or transports with additional framing overhead. A value that is too high can cause websites to stall even though the VPN appears connected.

### Configurable DNS

Available DNS modes:

- Automatic protected DNS.
- Cloudflare.
- Google.
- Custom resolver addresses.

Custom resolvers accept literal IPv4 or IPv6 addresses. Resolver values are applied to both the Android TUN and Xray configuration. Xray intercepts TCP and UDP port 53 from the TUN and routes it through its dedicated DNS outbound.

The Xray configuration retains static bootstrap mappings for Cloudflare and Google DoH hostnames to avoid recursive DNS resolution.

### Hardened reconnection

Automatic reconnection now uses:

- configurable maximum attempts;
- exponential delays with deterministic jitter;
- physical-network availability checks;
- a fresh active-outbound verification after rebuilding the transport;
- post-start outbound verification;
- two consecutive failed health checks before declaring an Internet outage;
- separate behavior for Kill Switch enabled and disabled.

When the retry limit is reached:

- with Kill Switch enabled, Android keeps the TUN blocked to prevent a direct-network leak;
- with Kill Switch disabled, the TUN is closed and the normal physical connection is restored.

### Structured error codes

Connection failures are no longer shown only as Java exceptions. Errors are classified into stable support codes.

Examples:

| Code | Stage | Meaning |
|---|---|---|
| `NET-001` | Network | No usable physical network |
| `DNS-001` | DNS | Server hostname cannot be resolved |
| `TCP-002` | TCP | Remote port rejected the connection |
| `PROXY-407` | Proxy | Proxy authentication required |
| `TLS-004` | TLS | Certificate or SNI validation failed |
| `SSH-401` | SSH | SSH authentication failed |
| `SSH-409` | SSH | SSH host identity changed |
| `SSH-500` | SSH | SSH runtime initialization failed |
| `XRAY-UUID` | Xray | Invalid VLESS or VMess UUID |
| `ROUTE-204` | Routing | Core started but outbound has no Internet |
| `TUN-500` | TUN | Android could not create the VPN interface |
| `RECONNECT-408` | Reconnect | Reconnection attempt limit reached |

Every classified error includes a suggested corrective action.

### Complete log export

The Logs screen now supports Android's native **Create document** flow. The exported UTF-8 report contains:

- application version and version code;
- device manufacturer and model;
- Android API level;
- supported ABIs;
- package name;
- all currently filtered log entries;
- stage tags and error codes;
- a statement confirming that stored secrets were sanitized.

Passwords, authorization headers, tokens and sensitive payload data continue to pass through the log sanitizer before storage and export.

### CI coverage

The Android workflow now runs:

1. Unit tests.
2. Android Lint.
3. Debug APK compilation with JNI.
4. Release APK compilation with R8 and resource shrinking.
5. Deprecation warning gate.
6. Release DEX inspection.

R8 validation requires these runtime classes to survive minification:

- JSch cryptographic providers;
- `AndroidRandomBridge`;
- `AndroidSecureRandomProvider`;
- `ConnectionDiagnosticsEngine`;
- `ConnectionErrorCatalog`.

New tests cover network preference validation and stable error classification. Existing tests continue to cover JSch initialization, Xray configuration and update handling.

## Connection acceptance criteria

A normal connection follows this sequence:

1. Validate profile fields.
2. Confirm a physical non-VPN network.
3. Load IP, DNS, MTU and reconnect preferences.
4. Persist the desired connected state for bounded process recovery.
5. Create fail-closed Android TUN routes.
6. Start one SSH/Xray runtime against the TUN descriptor.
7. Verify active outbound Internet.
8. Publish the connected state to the UI process.
9. Start health monitoring.

A failure before step 5 does not change the device's routes. A failure after step 5 closes the TUN unless Kill Switch protection is intentionally keeping traffic blocked during recovery.

## Runtime validation still required

CI cannot prove interoperability with every private server. The following must be tested on physical Android devices and real servers:

- SSH direct and password authentication;
- SSH + SSL with a valid SNI certificate;
- payload and proxy combinations;
- VLESS TCP, WebSocket, gRPC, XHTTP, TLS and REALITY;
- VMess compatibility profiles;
- Trojan TLS;
- Hysteria2/QUIC;
- Wi-Fi to mobile-data handover;
- sleep and process recreation;
- IPv4-only and dual-stack mobile networks;
- each MTU preset on affected carriers.

The diagnostic report is the required artifact when a real-server test fails.
