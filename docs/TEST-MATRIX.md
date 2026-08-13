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
| Direct SSH | password, port 22 | authenticated, SOCKS, core/TUN active, Internet | Pending |
| Direct SSH | password, custom port | authenticated, SOCKS, core/TUN active, Internet | Pending |
| SSH + SSL | TLS 1.2, valid SNI | strict certificate success | Pending |
| SSH + SSL | TLS 1.3, valid SNI | strict certificate success | Pending |
| SSH + SSL | invalid SNI | `TLS-004`; startup TUN closes without publishing Connected | Pending |
| SSH + SSL | custom SNI + trusted chain + different SAN | compatible handshake, SSH continues | Pending |
| SSH + SSL | strict SNI + untrusted chain | `TLS-004`; no compatibility fallback | Pending |
| SSH + SSL | custom SNI + private/self-signed chain | compatible handshake, SSH host-key verification continues | Pending |
| SSH + SSL | custom SNI + expired leaf | `TLS-004`; compatibility still enforces certificate dates | Pending |
| SSH + Payload | HTTP 200 | SSH continues | Pending |
| SSH + Payload | HTTP 101 | SSH continues | Pending |
| SSH + Payload | 403 | structured payload failure | Pending |
| SSH + Payload | 407 | `PROXY-407` or payload-stage failure | Pending |
| SSH + Payload | split + delay | ordered segments, bounded delay | Pending |
| SSH + Proxy | HTTP CONNECT | tunnel established | Pending |
| SSH + Proxy | SOCKS5 | tunnel established | Pending |
| SSH + Proxy | partial response | deterministic timeout/failure | Pending |
| SSH + SSL + Payload | TLS → payload → SSH | one SSH session; real traffic forwarded on demand | Pending |
| SSH + SSL + Payload | custom SNI → HTTP 200 → SSH | HTTP Custom-compatible Internet | Pending |

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

## Startup and registration matrix

| Scenario | Expected result | Status |
|---|---|---|
| Xray starts but Android has no owned VPN network | startup fails; UI never publishes false `Connected` | CI/source review pending |
| Android exposes owned `TRANSPORT_VPN`, but the outbound cannot return data | qualification reports `ROUTE-DATA-204`; UI never publishes false `Connected` | Device pending |
| Exact VPN network returns the single HTTPS qualification response | UI publishes `Connected` and system indicator remains active | Device pending |
| Normal SSH startup | exactly one qualification `direct-tcpip` flow opens inside the existing SSH session; no second login or `1/2` fallback | CI/source review pending |
| Accepted SSH session remains idle | no periodic endpoint probe, latency socket, or extra SSH session is created | CI/source review pending |
| Android removes VPN registration | passive monitor enters protected reconnection | Device pending |
| SSH session closes | passive monitor enters protected reconnection | Device pending |
| Disconnect during real traffic | core/TUN/SSH teardown completes; UI reaches Disconnected | Device pending |
| Reconnect succeeds | `Connected` is republished only after Android registration and one new qualification response | Device pending |
| SSH authentication banner | one complete sanitized rich-text message appears in the log | Device pending |
| Server without authentication banner | no server-message entry is created | CI verified / device pending |
| VPS shell/MOTD | no shell channel is opened and operating-system details are not shown | CI verified |

## Routing matrix

| Scenario | Expected result | Status |
|---|---|---|
| IPv4 only | no IPv6 route, Internet works | CI verified / device pending |
| Dual stack | IPv4 and IPv6 captured | CI verified / device pending |
| MTU 1280/1360/1400/1450/1500 | matching Android/Xray MTU | CI verified / device pending |
| Automatic DNS | protected resolver configured | CI verified / leak test pending |
| Custom IPv4 DNS | resolver applied | CI verified / device pending |
| All applications | own package excluded with mandatory `addDisallowedApplication` | CI/source review pending / device pending |
| Self-bypass application lookup failure | TUN creation fails closed; exception is not swallowed | CI/source review pending |
| Only selected | only allowlisted app uses VPN; own package omitted | CI verified / device pending |
| Empty only-selected list | `APP-ROUTE-001` before TUN | CI verified |
| Missing package | `APP-ROUTE-404` or TUN startup failure | CI verified |
| Exclude selected | own package and selected apps bypass VPN | CI/source review pending / device pending |
| JSch socket during all-app mode | socket uses physical network and never re-enters TUN | Device pending |
| Kill Switch during pending probe | captured traffic remains blocked from direct fallback | Device pending |
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
- Android VPN-registration loss.
- manual disconnect during real traffic.
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
- GNX3 password and app-managed correct-key round trips;
- GNX3 altered header, lock flag, ciphertext, and authentication tag;
- locked GNX3 masked preview, Room envelope, blocked edit/duplicate/re-export/diagnostic;
- HTML note allowlist, active elements/events/URLs, remote CSS, and external contact links;
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
- explicit Diagnostics endpoints: success, refusal, read timeout, indefinite server hold;
- network impairment: latency, jitter, packet loss, disconnect.

## Release gate

A stable release requires all automated checks green and no unresolved critical device regression. A marketing claim such as “supports VLESS gRPC REALITY” requires at least one recorded device/server test for that exact combination.

The startup-state fix additionally requires recorded proof that:

1. the VPN package is excluded from its own TUN;
2. Android displays its VPN indicator before the app publishes `Connected`;
3. startup creates exactly one VPN-bound qualification flow, never a `1/2` fallback pair, and an accepted idle session creates no periodic probe socket;
4. manual disconnect can tear down active real traffic;
5. no captured application traffic falls back directly.
