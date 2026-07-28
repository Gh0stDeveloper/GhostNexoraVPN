# SSH Transport

## Pipeline

The SSH family is implemented as a staged socket pipeline. Each stage emits sanitized operational events so a failure can be assigned to TCP, proxy, TLS, payload, SSH authentication, local SOCKS, Xray, DNS, or Android routing.

```text
physical network
  → optional HTTP CONNECT / SOCKS5 proxy
  → optional TLS with SNI and hostname verification
  → optional segmented HTTP payload
  → SSH key exchange and authentication
  → loopback SOCKS5 server
  → Xray SOCKS outbound
  → Android TUN
```

## Host verification

JSch uses a persistent known-hosts repository. The first trusted fingerprint is stored locally. A later identity change is rejected and classified as `SSH-409`. Resetting fingerprints should occur only after independently confirming a legitimate server change.

## Random provider

Android builds inject an application-owned `SecureRandom` provider directly into JSch session and packet state. This avoids relying solely on reflective provider loading after R8. CI checks the bridge and provider classes in the Release DEX.

## TLS layer

The TLS socket:

- uses the platform trust store;
- enables HTTPS endpoint identification;
- sends the configured SNI;
- rejects hostname/certificate mismatches;
- does not offer a global trust-all mode.

Certificate pinning and selectable TLS/ALPN policy are roadmap features until their UI and tests are complete.

## Payload layer

The payload is compiled by `PayloadEngine`. It can contain multiple send/delay actions, but the number, size, and total delay are bounded. The engine never writes raw payload contents to persistent logs.

A payload response is accepted only when it represents an allowed HTTP success/tunnel response or when the payload format intentionally exposes an SSH banner. Error responses such as 403 and 407 are surfaced as structured failures.

## Local SOCKS bridge

After authentication, the app starts a loopback-only SOCKS5 server. Each SOCKS CONNECT opens a JSch `direct-tcpip` channel. The listener is not exposed on Wi-Fi or mobile interfaces.

Supported local SOCKS behavior:

- SOCKS5 greeting;
- no-auth local method;
- IPv4, IPv6, and domain destinations;
- TCP CONNECT;
- per-client channel cleanup.

Not claimed:

- SOCKS UDP ASSOCIATE through SSH;
- remote DNS policy independent of the Xray SOCKS client;
- proxy authentication in the current SSH upstream proxy stage;
- production private-key authentication.

## Required physical tests

- password auth on ports 22, 80, 443, and a custom port;
- TLS 1.2 and 1.3 server endpoints;
- HTTP 200 and 101 payload responses;
- split payload and delayed segment behavior;
- partial proxy response and 407 response;
- server close during key exchange and after authentication;
- known-host first trust and changed fingerprint;
- Wi-Fi/mobile handover;
- sleep and process recreation;
- long-running TCP streams.

See `TEST-MATRIX.md` for the evidence format.