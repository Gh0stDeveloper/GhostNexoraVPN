# Ghost Nexora VPN Roadmap

This roadmap separates implemented work from planned work. An implemented item is not labeled interoperable until CI and physical-device/server tests are complete.

## Completed foundation

### Phase 1 — Runtime stability

- non-destructive connection diagnostics;
- isolated fail-closed TUN startup;
- active outbound verification before connected-state publication;
- IPv4-only, IPv4-preferred, and dual-stack modes;
- synchronized Android/Xray MTU;
- protected DNS modes;
- bounded reconnection and Kill Switch behavior;
- structured error catalog;
- sanitized full diagnostic export;
- unit, Lint, Debug/JNI, Release/R8, and DEX validation.

### Phase 2A — Import compatibility

Implemented and awaiting final CI/device qualification:

- `vless://`;
- `vmess://`;
- `trojan://`;
- `hysteria2://` and `hy2://`;
- `ssh://` convention;
- standard Xray outbound JSON;
- clipboard and QR import;
- technical preview;
- duplicate detection and duplicate-safe merge/replace.

### Phase 3A — Injector and profile foundation

Implemented and awaiting final CI/device qualification:

- visual CONNECT, GET, POST, HEAD, and WebSocket templates;
- CRLF-aware preview;
- validated payload variables;
- split writes and bounded delays;
- payload syntax validation before save;
- profile search, filters, favorites, and duplication;
- all/only-selected/excluded application split tunneling;
- in-app evidence-based compatibility matrix.

## Phase 2B — Protocol-specific editors

Replace remaining generic Xray option text with typed fields for:

- VLESS flow, encryption, TLS/REALITY, public key, short ID, fingerprint, spiderX;
- VMess cipher and compatibility Alter ID;
- Trojan SNI, ALPN, transport, Host, path, and service name;
- Hysteria2 obfuscation, bandwidth hints, port hopping, and QUIC options;
- reusable transport subforms for TCP, WebSocket, gRPC, XHTTP, HTTPUpgrade, and mKCP.

## Phase 2C — SSH identity and proxy authentication

- RSA, ECDSA, and Ed25519 private keys;
- modern OpenSSH key format;
- encrypted keys with passphrase;
- Keystore-backed local key storage;
- password/key authentication selector;
- HTTP proxy user/password;
- SOCKS5 user/password;
- remote proxy DNS policy;
- explicit 407 diagnostic tests;
- certificate viewer and opt-in SHA-256 pinning;
- selectable TLS 1.2/1.3 and ALPN.

## Phase 3B — Advanced injector functionality

- payload repetition with strict bounds;
- response parser and UI for 101, 200, 403, and 407;
- payload history, favorites, and named reusable templates;
- payload import/export;
- alternative TLS/payload ordering only after server fixtures exist;
- batch diagnostics;
- fastest-profile selection with comparable test conditions.

## Phase 3C — Profile and routing management

- folders and custom ordering;
- last successful latency and timestamp;
- consecutive failure count;
- encrypted full backup/restore;
- GNX expiration, minimum version, creator metadata, integrity signature, and optional device policy;
- private-network/CIDR bypass;
- domain include/exclude rules;
- per-profile app groups;
- TCP/UDP-specific routing.

## Phase 4 — Product completion

- lightweight session speed chart;
- public IP before/after;
- DNS leak test;
- biometric or PIN application lock;
- `FLAG_SECURE` on sensitive screens;
- signer-certificate verification for updates;
- SBOM, secret scanning, and dependency vulnerability scanning;
- localization, accessibility, and small-screen audits;
- clear/AMOLED/dark themes;
- release qualification matrix for supported servers and Android versions;
- public server administration guide;
- final privacy policy and store disclosures.

## Post-merge device qualification backlog

For the 1.0.40 device qualification cycle:

1. Install the newest validation APK on the device that reproduced the JSch error.
2. Run diagnostics for the same SSH + SSL profile.
3. Test segmented payloads against controlled 200/101/403/407 fixtures.
4. Repeat with the V2Ray profile that previously removed Internet access.
5. Test IPv4 only at MTU 1400, then 1360 if pages stall.
6. Test all three application-routing modes.
7. Test mobile-data/Wi-Fi handover.
8. Confirm Kill Switch enabled and disabled behavior.
9. Verify import preview and duplicate-safe merge.
10. Record every successful exact combination in `TEST-MATRIX.md`.

## Release policy

A transport is labeled **device verified** only when:

- unit/configuration tests pass;
- Lint, Debug/JNI, and Release/R8 pass;
- runtime classes survive minification;
- a real server accepts authentication;
- DNS and TCP/HTTP traffic work through the tunnel;
- upload and download are observed;
- reconnection works after an interruption;
- no unintended direct traffic leak is observed;
- the evidence record identifies device, network, server, and application commit.
