# Connection Modes

This document describes implemented behavior. It does not certify interoperability with every server implementation. The in-app Compatibility screen is the concise status source; `TEST-MATRIX.md` records required physical tests.

## SSH modes

| Mode | Transport chain | Current status |
|---|---|---|
| Direct SSH | TCP → SSH → local SOCKS → Xray TUN | Device testing pending |
| SSH + SSL | TCP → TLS/SNI (strict or custom-compatible) → SSH → SOCKS → TUN | Device testing pending |
| SSH + Payload | TCP → segmented HTTP payload → SSH → SOCKS → TUN | Device testing pending |
| SSH + SSL + Payload | TCP → TLS/SNI (strict or custom-compatible) → payload → SSH → SOCKS → TUN | Device testing pending |
| SSH + Proxy | HTTP CONNECT or SOCKS5 → SSH → SOCKS → TUN | Device testing pending |
| SSH + Payload + Proxy | proxy → payload → SSH → SOCKS → TUN | Device testing pending |
| SSH + Payload + Proxy + SSL | proxy → TLS/SNI → payload → SSH → SOCKS → TUN | Device testing pending |

SSH uses password authentication in the current production path. Private-key authentication, proxy credentials, and alternate payload/TLS ordering remain roadmap items until implemented and tested.

The HTTP Custom-compatible policy applies only to SSH modes that use TLS. It
sends an arbitrary valid DNS SNI and skips only SNI/SAN hostname matching. It
does not install a trust-all certificate manager. Payload-only SSH has no TLS
certificate stage and is unaffected by that selector.

The local SSH bridge supports SOCKS5 CONNECT and carries TCP through JSch `direct-tcpip` channels. It does not claim generic UDP tunneling over SSH.

## VLESS and VMess

Implemented configuration fields include:

- UUID/User ID;
- VLESS encryption and flow;
- VMess cipher and compatibility Alter ID;
- TCP, WebSocket, gRPC, XHTTP, HTTPUpgrade, and mKCP;
- TLS and REALITY parameters;
- SNI, Host, path, authority, service name, ALPN, fingerprint, public key, short ID, and spiderX.

Configuration generation and routing are unit-tested. Real-server combinations remain device testing pending.

## Trojan

Implemented:

- password authentication;
- strict TLS and SNI;
- TCP, WebSocket, gRPC, XHTTP, and HTTPUpgrade stream parameters where supported by Xray;
- protected DNS and explicit TUN routing.

## Hysteria2

Implemented through the bundled Xray runtime:

- QUIC/UDP transport;
- TLS/SNI;
- auth;
- obfuscation and obfuscation password;
- optional bandwidth and port-hopping parameters imported from links.

Hysteria2 remains experimental until loss, handover, sleep, IPv4/IPv6, and carrier-specific tests are recorded.

## Connection acceptance

A mode is reported connected only after:

1. profile validation;
2. physical network validation;
3. private VPN-process startup;
4. fail-closed TUN establishment;
5. one core/SSH startup;
6. active outbound HTTP verification;
7. connected-state publication and health-monitor startup.

DNS configuration and TUN routes are deterministic, but full per-protocol DNS-query inspection remains part of the extended physical test matrix.

## Status terminology

- **CI verified:** source, tests, Lint, APK packaging, R8, and required DEX classes pass.
- **Device testing pending:** implementation exists, but the exact transport combination lacks recorded physical-server evidence.
- **Experimental:** implementation exists but requires broader network-condition validation.
- **Not implemented:** documentation only; the app must not present it as available.
