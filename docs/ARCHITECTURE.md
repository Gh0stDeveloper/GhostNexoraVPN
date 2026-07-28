# Architecture

## Design goals

Ghost Nexora VPN separates profile storage, transport creation, Android routing, diagnostics, and UI state so a transport cannot mark the VPN connected by itself. The authoritative connected state belongs to `GhostVpnService` after remote and active outbound verification.

## Runtime layers

```text
Compose UI
  ├─ Dashboard / Profiles / Import / Settings / Diagnostics
  └─ ViewModels + StateFlow
          │
ProfileRepository
  ├─ Room profiles and logs
  ├─ DataStore runtime preferences
  └─ Android Keystore secret protection
          │
GhostVpnService
  ├─ physical-network tracking
  ├─ preflight acceptance gate
  ├─ VpnService.Builder routes and app rules
  ├─ health monitoring and reconnection
  └─ session statistics
          │
TunnelManager
  ├─ SshTunnelEngine → local SOCKS bridge
  ├─ XrayCoreEngine
  └─ StableXrayConfigFactory
          │
Remote SSH / VLESS / VMess / Trojan / Hysteria2 server
```

## Connection state machine

1. Validate the selected profile.
2. Confirm a non-VPN physical network.
3. Load IP, DNS, MTU, reconnect, and application-routing preferences.
4. Run outbound preflight without creating Android default routes.
5. Configure application allow/exclude rules.
6. Establish the Android TUN.
7. Start SSH/Xray against the TUN descriptor.
8. Verify active outbound Internet.
9. Publish `Connected` and start health/statistics jobs.
10. On failure, reconnect or close/retain the TUN according to Kill Switch policy.

## Data model

- `VpnProfile`: server, credentials, mode, SNI, SSH TLS policy, payload, proxy, tags, notes, state.
- `NetworkPreferences`: IP mode, MTU, DNS mode, custom resolvers, reconnect limit.
- `AppRoutingPreferences`: all, only selected, or exclude selected applications.
- `LogEntry`: sanitized timestamped stage event.
- `VpnTrafficStats`: Xray proxy-outbound session counters and current rates.

Sensitive `VpnProfile` fields are encrypted before Room persistence. DataStore contains non-secret operational preferences.

## Import architecture

Import content is processed in this order:

1. GNX2 encrypted envelope.
2. Standard Xray JSON outbound document.
3. Ghost Nexora legacy JSON.
4. Protocol links: VMess, VLESS, Trojan, Hysteria2/Hy2, and SSH.

Every successful parse produces ordinary `VpnProfile` instances, technical summaries, and duplicate fingerprints before storage.

## Security invariants

- No Android TUN before remote preflight succeeds.
- No connected state before active outbound verification succeeds.
- No global TLS trust bypass.
- No silent direct fallback for TUN traffic.
- No empty application allowlist that silently becomes full-device routing.
- No unsanitized secrets in persistent logs.
- No plaintext profile credentials in Room.

## Extensibility

New protocols should implement:

- profile validation;
- deterministic configuration generation;
- non-destructive preflight;
- active health verification;
- structured error classification;
- sanitized stage logging;
- unit tests and physical-server interoperability cases.

A protocol is not marked device-verified merely because its configuration compiles or its core process starts.
