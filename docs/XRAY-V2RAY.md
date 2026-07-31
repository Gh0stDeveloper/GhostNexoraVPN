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

Normal VPN startup has two independent milestones:

1. **Core ready:** the Android TUN exists and `CoreController.startLoop()` reports a running Xray loop. `GhostVpnService` immediately publishes `Connected`.
2. **Outbound verified:** an I/O coroutine measures the active outbound against independent HTTP 204 endpoints. The result updates latency and logs without blocking the UI or service teardown.

`Libv2ray.measureOutboundDelay` remains available for the explicit non-destructive diagnostic workflow before TUN creation. The normal connection path does not run that preflight automatically and does not call an active outbound probe synchronously inside `TunnelManager.start()`.

A failed first background check does not create a direct fallback. The fail-closed TUN remains active and periodic health checks retry. Repeated failures invoke the configured reconnection and Kill Switch policy.

A running core is enough for the transient UI `Connected` state, but it is not sufficient evidence for **device verified** compatibility. Qualification still requires a successful outbound check plus sustained real traffic.

## Concurrency requirements

- Network probes run on `Dispatchers.IO`.
- Startup state publication does not wait for HTTP, TLS, SOCKS, ping, or DNS probes.
- `XrayCoreEngine.verifyActiveOutbound()` snapshots the active controller under a short monitor and performs remote I/O outside that monitor.
- A slow or stuck check must not prevent `stopLoop()`, disconnect, or reconnection.

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
