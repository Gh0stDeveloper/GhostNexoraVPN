# Ghost Nexora VPN Roadmap

This roadmap lists planned work after the Phase 1 stability milestone. Items are not considered supported until implementation, CI validation and physical-device interoperability tests are complete.

## Phase 2 — Configuration compatibility

### Importers

- `vless://`
- `vmess://`
- `trojan://`
- `hysteria2://` and `hy2://`
- `ssh://`
- Xray JSON
- clipboard detection
- QR import preview
- duplicate detection

### Protocol-specific profile editors

Replace the generic form with fields specific to VLESS, VMess, Trojan, Hysteria2 and SSH transports.

Planned fields include:

- network transport;
- TLS or REALITY;
- Host and path;
- gRPC service name;
- ALPN;
- fingerprint;
- public key and short ID;
- flow and encryption;
- proxy authentication.

### SSH identity support

- RSA, ECDSA and Ed25519 private keys;
- OpenSSH key format;
- encrypted keys with passphrase;
- Android Keystore-backed local storage;
- explicit password/key authentication selection.

## Phase 3 — Advanced injector functionality

### Payload engine

- visual GET, CONNECT, POST, HEAD and WebSocket templates;
- CRLF-aware editor;
- payload variables;
- split writes;
- configurable delays;
- payload repetition;
- response parser for HTTP 101, 200, 403 and 407;
- history, favorites and reusable templates.

### Profile management

- folders and labels;
- profile duplication;
- last successful latency;
- consecutive failure counter;
- batch diagnostics;
- fastest-profile selection;
- encrypted backup and restore.

### Split tunneling

- all applications through VPN;
- only selected applications;
- excluded applications;
- private-network bypass;
- domain and CIDR rules;
- TCP/UDP-specific routing.

## Phase 4 — Product completion

- session speed chart;
- public IP before and after connection;
- DNS leak test;
- biometric or PIN application lock;
- `FLAG_SECURE` for sensitive screens;
- signed update verification against the installed signer;
- SBOM and dependency vulnerability scanning;
- localization audit;
- accessibility audit;
- complete user manual and troubleshooting catalog;
- release qualification matrix for supported servers and Android versions.

## Immediate validation backlog

Before the current draft PR is merged:

1. Install version 1.0.33 on the device that reproduced the JSch error.
2. Run Connection diagnostics for the same SSH + SSL profile.
3. Test the connection and export the diagnostic report.
4. Repeat with the V2Ray profile that previously removed Internet access.
5. Test IPv4 only at MTU 1400.
6. Test IPv4 preferred at MTU 1360 if pages stall.
7. Test mobile-data to Wi-Fi and Wi-Fi to mobile-data handover.
8. Confirm behavior with Kill Switch both enabled and disabled.
9. Verify log export and secret redaction.
10. Record each verified protocol/transport combination in a release test matrix.

## Release policy

A transport should be labeled **verified** only when:

- unit and configuration tests pass;
- Debug and Release/R8 builds pass;
- the required runtime classes survive minification;
- a real server accepts authentication;
- DNS resolution works through the tunnel;
- outbound HTTP traffic succeeds;
- reconnection works after a network interruption;
- no unintended direct traffic leak is observed.