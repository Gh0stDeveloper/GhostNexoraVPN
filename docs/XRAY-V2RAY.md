# Xray, VLESS, and VMess

## Core integration

Ghost Nexora VPN builds an explicit Xray configuration for each connection. Device traffic enters a TUN inbound, DNS traffic is directed to a dedicated DNS outbound, and remaining TCP/UDP traffic is directed to the selected proxy outbound. There is no catch-all direct fallback for TUN traffic.

## VLESS

Supported profile parameters include:

- UUID;
- `encryption`;
- `flow`;
- TCP, WebSocket, gRPC, XHTTP, HTTPUpgrade, and mKCP;
- TLS or REALITY;
- SNI;
- Host/path;
- gRPC service name and authority;
- fingerprint;
- REALITY public key, short ID, and spiderX;
- ALPN.

VLESS `encryption=none` is accepted only with TLS or REALITY in the profile validation path.

## VMess

Supported profile parameters include:

- UUID;
- cipher/security;
- Alter ID for compatibility with older servers;
- transport and TLS fields shared with VLESS.

Mux is disabled by default because deterministic compatibility is preferred over speculative concurrency tuning.

## Import

The importer accepts:

- `vless://`;
- `vmess://` Base64 JSON;
- standard Xray JSON containing compatible outbound objects;
- Ghost Nexora legacy JSON;
- GNX3 individual and GNX2 backup encrypted profiles.

The preview shows server, transport, TLS/REALITY, SNI, Host, path, service name, and warnings before storage.

## TLS and REALITY

TLS uses strict certificate and hostname verification. REALITY parameters are preserved from links and Xray JSON. The app does not synthesize missing REALITY public keys or short IDs.

## DNS and routing

- Android and Xray share IP mode and MTU.
- DNS port 53 from the TUN is intercepted explicitly.
- Protected resolver selection is generated from settings.
- Cloudflare and Google DoH hostnames have static bootstrap addresses.
- IPv4-only mode omits Android IPv6 routes and uses an IPv4 DNS query strategy.
- The application package is excluded from its own full-device TUN so core-management and transport sockets cannot recursively enter the tunnel.

## Startup and health verification

Normal VPN startup has one gated state transition:

1. Android establishes a valid TUN descriptor.
2. `CoreController.startLoop()` reports a running Xray loop and the selected transport remains alive.
3. `ConnectivityManager` exposes an owned `TRANSPORT_VPN` network.
4. Only then does `GhostVpnService` publish `Connected`.

`Libv2ray.measureOutboundDelay` remains available for the explicit non-destructive diagnostic workflow before TUN creation. The normal connection path does not run that preflight automatically and does not call an active outbound probe synchronously inside `TunnelManager.start()`.

The normal connection path also has no post-start remote probe, loopback health-check inbound, or periodic latency socket. Passive health checks observe the existing Xray/transport runtime and Android VPN registration. Real applications generate the traffic used to qualify forwarding.

A running core by itself is not enough for the UI `Connected` state and is not sufficient evidence for **device verified** compatibility. Qualification still requires sustained real traffic.

## Concurrency requirements

- Startup state publication does not run HTTP, TLS, SOCKS, ping, or DNS probes.
- Passive health checks must not open sockets or retain the runtime monitor.
- Explicit Diagnostics remote I/O remains isolated from the normal VPN lifecycle.
- Disconnect and reconnection must remain available while real traffic is active.

## Compatibility status

Configuration generation, import, routing rules, DNS bootstrap, and R8 packaging are CI verified. Exact server combinations remain device testing pending. The physical matrix must include:

- VLESS TCP/TLS;
- VLESS WebSocket/TLS;
- VLESS gRPC/TLS;
- VLESS XHTTP;
- VLESS REALITY/vision;
- VMess TCP, WebSocket, and gRPC;
- IPv4-only and dual-stack networks;
- DNS failure and server-side rejection;
- Wi-Fi/mobile handover;
- delayed/unreachable health endpoints without a frozen UI;
- disconnect while an outbound check is in progress.

## Troubleshooting priorities

When Xray starts but Internet is unavailable, verify in order:

1. UUID and protocol.
2. Server host and port.
3. TLS/REALITY and SNI.
4. Host/path/service name.
5. Transport type.
6. IP mode and MTU.
7. DNS mode.
8. Remote server outbound policy.

Export a sanitized diagnostic report containing the structured error code and stages.
