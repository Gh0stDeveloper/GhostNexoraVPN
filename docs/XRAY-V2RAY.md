# Xray, VLESS, and VMess

## Core integration

Ghost Nexora VPN builds an explicit Xray configuration for each connection. Device traffic enters a TUN inbound, DNS traffic is directed to a dedicated DNS outbound, and remaining TCP/UDP traffic is directed to the selected proxy outbound. There is no catch-all direct fallback for TUN traffic.

## VLESS

Supported profile parameters:

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

Supported profile parameters:

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

## Health verification

`Libv2ray.measureOutboundDelay` is used before the Android TUN and again after core startup. Two independent HTTP 204 endpoints are attempted. A running core without a valid outbound is not considered connected.

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
- Wi-Fi/mobile handover.

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
