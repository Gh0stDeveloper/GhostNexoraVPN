# Test Matrix

## Evidence rules

A row becomes **Device verified** only when the following are recorded:

- application commit/version;
- Android device/model/API;
- network type and carrier/provider;
- server software/version;
- complete sanitized profile parameters;
- diagnostic result;
- connection duration;
- upload/download evidence;
- handover/sleep result where applicable;
- sanitized log artifact.

CI success alone is labeled **CI verified**.

## SSH matrix

| Mode | Variant | Expected result | Status |
|---|---|---|---|
| Direct SSH | password, port 22 | authenticated, SOCKS, Internet | Pending |
| Direct SSH | password, custom port | authenticated, SOCKS, Internet | Pending |
| SSH + SSL | TLS 1.2, valid SNI | strict certificate success | Pending |
| SSH + SSL | TLS 1.3, valid SNI | strict certificate success | Pending |
| SSH + SSL | invalid SNI | `TLS-004` before TUN | Pending |
| SSH + Payload | HTTP 200 | SSH continues | Pending |
| SSH + Payload | HTTP 101 | SSH continues | Pending |
| SSH + Payload | 403 | structured payload failure | Pending |
| SSH + Payload | 407 | `PROXY-407` or payload-stage failure | Pending |
| SSH + Payload | split + delay | ordered segments, bounded delay | Pending |
| SSH + Proxy | HTTP CONNECT | tunnel established | Pending |
| SSH + Proxy | SOCKS5 | tunnel established | Pending |
| SSH + Proxy | partial response | deterministic timeout/failure | Pending |
| SSH + SSL + Payload | TLS → payload → SSH | Internet verified | Pending |

## Xray matrix

| Protocol | Transport/security | Status |
|---|---|---|
| VLESS | TCP + TLS | Pending |
| VLESS | WebSocket + TLS | Pending |
| VLESS | gRPC + TLS | Pending |
| VLESS | XHTTP + TLS | Pending |
| VLESS | TCP + REALITY + vision | Pending |
| VMess | TCP | Pending |
| VMess | WebSocket + TLS | Pending |
| VMess | gRPC + TLS | Pending |
| Trojan | TCP + TLS | Pending |
| Trojan | WebSocket + TLS | Pending |
| Trojan | gRPC + TLS | Pending |
| Hysteria2 | QUIC + TLS | Experimental |
| Hysteria2 | obfuscation | Experimental |

## Routing matrix

| Scenario | Expected result | Status |
|---|---|---|
| IPv4 only | no IPv6 route, Internet works | CI verified / device pending |
| Dual stack | IPv4 and IPv6 captured | CI verified / device pending |
| MTU 1280/1360/1400/1450/1500 | matching Android/Xray MTU | CI verified / device pending |
| Automatic DNS | protected resolver configured | CI verified / leak test pending |
| Custom IPv4 DNS | resolver applied | CI verified / device pending |
| All applications | own package excluded | CI verified / device pending |
| Only selected | only allowlisted app uses VPN | CI verified / device pending |
| Empty only-selected list | `APP-ROUTE-001` before TUN | CI verified |
| Missing package | `APP-ROUTE-404` | CI verified |
| Exclude selected | selected app bypasses VPN | CI verified / device pending |
| Kill Switch transport loss | traffic remains blocked | Device pending |

## Recovery matrix

- Wi-Fi → mobile data.
- Mobile data → Wi-Fi.
- Temporary signal loss.
- IP address change.
- DNS outage.
- Remote server close.
- Xray core exit.
- SSH session close.
- screen off for 15/60 minutes.
- process recreation.
- device reboot with reconnect policy.

## Import matrix

- valid/invalid VMess Base64;
- VLESS TLS/REALITY;
- Trojan;
- Hysteria2/Hy2;
- SSH direct/TLS/payload/proxy;
- standard Xray JSON;
- legacy JSON;
- GNX2 correct/wrong password;
- duplicate existing profile;
- duplicate within import;
- malformed/oversized input;
- QR and clipboard.

## Local simulated services

Planned reproducible fixtures:

- HTTP proxy: 200, 403, 407, partial response;
- SOCKS5: no-auth, auth-required, refused CONNECT;
- TLS: valid, expired, wrong hostname, incomplete handshake;
- SSH: valid password, invalid password, incomplete banner, close during key exchange;
- payload endpoint: 101, 200, delayed fragments;
- network impairment: latency, jitter, packet loss, disconnect.

## Release gate

A stable release requires all automated checks green and no unresolved critical device regression. A marketing claim such as “supports VLESS gRPC REALITY” requires at least one recorded device/server test for that exact combination.