# Trojan and Hysteria2

## Trojan

Trojan profiles use password authentication and a TLS-protected Xray outbound.

Implemented fields:

- server and port;
- password;
- SNI;
- ALPN;
- fingerprint;
- TCP, WebSocket, gRPC, XHTTP, and HTTPUpgrade stream settings;
- Host, path, authority, and service name.

The application requires TLS and does not expose a trust-all option. Import is supported from `trojan://`, standard Xray JSON, Ghost Nexora JSON, GNX3, and GNX2.

## Hysteria2

Hysteria2 is represented by the UDP mode and uses Xray's Hysteria/QUIC transport.

Implemented/imported fields:

- auth/password;
- server and port;
- TLS/SNI;
- ALPN;
- obfuscation method;
- obfuscation password;
- optional bandwidth hints;
- optional port range/hopping parameters when supplied by links.

The bundled Xray 26.5 schema stores authentication and UDP idle timeout in
`hysteriaSettings`. Salamander obfuscation and QUIC tuning are emitted in
`streamSettings.finalmask`: the `udp` mask contains the Salamander password,
while `quicParams` contains Brutal bandwidth and `udpHop` ports/interval.
Standard Xray JSON import reads the same structure and retains a legacy
fallback for older exported objects.

Supported link schemes:

- `hysteria2://`
- `hy2://`

## Network behavior

Hysteria2 is more sensitive to:

- UDP blocking by carriers or Wi-Fi networks;
- packet loss and high jitter;
- path MTU;
- network handover;
- NAT rebinding;
- background restrictions.

Start physical testing with MTU 1280 or 1360 when QUIC stalls, then compare 1400. The diagnostic result must record carrier/network type, IP mode, MTU, DNS mode, latency, and failure stage.

## Runtime state and verification

For Trojan and Hysteria2, the Dashboard publishes `Connected` only when the Android TUN is valid, Xray is active, Android exposes the application's exact owned `TRANSPORT_VPN` network, and one bounded HTTPS flow returns through the selected outbound. No outbound check runs periodically afterward. The passive health monitor applies the configured reconnection/Kill Switch policy if the runtime or Android VPN registration disappears.

The UI state is not interoperability certification. Device verification still requires a successful outbound check, real upload/download traffic, leak testing, and sustained operation under the relevant network conditions.

## Status

- Trojan configuration/import/routing and non-blocking startup: CI verified; physical interoperability pending.
- Hysteria2 configuration/import/routing and non-blocking startup: CI verified.
- Hysteria2 stability across carriers, loss, handover, and sleep: experimental until documented.

## Required tests

### Trojan

- TCP/TLS with valid certificate;
- invalid SNI;
- WebSocket/TLS;
- gRPC/TLS;
- server authentication rejection;
- IPv4-only and dual stack;
- Android VPN registration failure without false `Connected` state;
- disconnect while real traffic is active.

### Hysteria2

- direct QUIC on mobile data and Wi-Fi;
- UDP-blocked network;
- obfuscation success/failure;
- 1%, 5%, and 10% simulated packet loss;
- Wi-Fi to mobile handover;
- screen-off/background session;
- MTU 1280, 1360, and 1400;
- IPv6-capable and IPv4-only servers;
- Android VPN registration loss while the fail-closed TUN remains active.

A successful core start alone is insufficient for both the UI `Connected` state and a device-verified support claim.
