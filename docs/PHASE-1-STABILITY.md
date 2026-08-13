# Phase 1 — Runtime Stability and Diagnostics

This document describes the first production-hardening phase implemented in Ghost Nexora VPN 1.0.33 and the system-registration correction completed in 1.0.51.

> Runtime note: non-destructive diagnostics still use the explicit preflight described below. Normal connection startup owns a single native runtime in the private `:vpn` process and contains no loopback probe inbound or automatic remote test requests.

## Goals

Phase 1 focuses on connection correctness before expanding protocol features:

- the application must not report `Connected` before SSH/Xray, the valid Android TUN, and the owned VPN network are active;
- normal VPN startup and health monitoring must not generate remote probe traffic;
- captured traffic must remain fail-closed without a direct fallback;
- real outbound evidence is required for device qualification, even though synthetic traffic is not a UI gate.

## Implemented features

### Automatic connection diagnostics

Settings includes **Run connection diagnostics**. The diagnostic engine is non-destructive: it does not install Android VPN routes and does not interrupt the device's normal Internet connection.

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

### System-confirmed normal startup

The normal Dashboard connection path does not reuse the diagnostic preflight as a synchronous gate. Its sequence is:

1. establish strict application-routing rules and the fail-closed TUN;
2. authenticate SSH and start the local bridge when required;
3. start Xray against the TUN descriptor;
4. require Android to expose an owned `TRANSPORT_VPN` network while the TUN descriptor remains valid;
5. recheck the existing transport/core and publish `Connected`;
6. start passive registration/runtime health and statistics monitoring.

No initial probe, periodic endpoint probe, or latency socket runs in this path. SSH forwarding channels are opened only for actual device traffic; disconnect and core shutdown remain available.

### Application self-bypass

In full-device and exclude-selected modes, `VpnService.Builder.addDisallowedApplication(packageName)` is mandatory. Its failure aborts TUN startup instead of being swallowed. This keeps the application UID, JSch sockets, and native-core management traffic on the physical network and prevents recursive TUN routing.

Only-selected mode cannot combine Android allowed and disallowed lists. The VPN package is therefore excluded by omission from the allowlist.

### Configurable IP mode

The global IP mode can be changed under **Settings > Connection engine**.

Available modes:

- **IPv4 only** — Android does not create an IPv6 TUN address or IPv6 default route.
- **IPv4 preferred** — Android captures IPv4 and IPv6, while DNS resolution prefers IPv4.
- **IPv4 + IPv6** — Android captures both families and Xray can resolve both A and AAAA records.

IPv4-only mode is recommended when a server, mobile provider or transport cannot route IPv6 correctly.

### Configurable MTU

Android and Xray always use the same TUN MTU. Presets:

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

Automatic reconnection uses:

- configurable maximum attempts;
- exponential delays with deterministic jitter;
- physical-network availability checks;
- fresh SSH/Xray runtime creation;
- Android VPN-registration confirmation after a successful restart;
- passive transport/core and VPN-registration checks;
- separate behavior for Kill Switch enabled and disabled.

When the retry limit is reached:

- with Kill Switch enabled, Android keeps the TUN blocked to prevent a direct-network leak;
- with Kill Switch disabled, the TUN is closed and the normal physical connection is restored.

### Structured error codes

Connection failures are classified into stable support codes.

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
| `ROUTE-204` | Routing | Core active but outbound verification failed |
| `TUN-500` | TUN | Android could not create the VPN interface |
| `RECONNECT-408` | Reconnect | Reconnection attempt limit reached |

Every classified error includes a suggested corrective action.

### Complete log export

The Logs screen supports Android's native **Create document** flow. The exported UTF-8 report contains:

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

The Android workflow runs:

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

## Connection acceptance criteria

A normal connection follows this sequence:

1. Validate profile fields.
2. Confirm a physical non-VPN network.
3. Load IP, DNS, MTU, app-routing and reconnect preferences.
4. Persist the desired connected state for bounded process recovery.
5. Apply mandatory self-bypass or the strict only-selected allowlist.
6. Create fail-closed Android TUN routes.
7. Start one SSH/Xray runtime against the TUN descriptor.
8. Confirm the native Xray loop is running.
9. Confirm the TUN descriptor is valid and Android exposes an owned `TRANSPORT_VPN` network.
10. Publish `Connected` to the UI process and start passive health monitoring.

A failure before step 6 does not change the device's routes. A transport or Android VPN-registration failure after step 6 closes the TUN unless Kill Switch protection is intentionally retaining blocked routing during recovery. No normal-session remote probe or silent direct fallback exists.

## Runtime validation still required

CI cannot prove interoperability with every private server. Physical Android testing must cover:

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
- each MTU preset on affected carriers;
- Android VPN-registration failure without a false connected state;
- manual disconnect while real traffic is running;
- all application-routing modes with proof that the VPN package never enters its own TUN.

The diagnostic report is the required artifact when a real-server test fails.
