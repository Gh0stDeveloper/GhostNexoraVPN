# Architecture

## Design goals

Ghost Nexora VPN separates profile storage, transport creation, Android routing, diagnostics, active health verification, and UI state. A transport cannot mark the VPN connected by itself: the authoritative state belongs to `GhostVpnService` after Android establishes the TUN and Xray reports that its native loop is running.

`Connected` means that the selected SSH/Xray transport, local bridge where required, native core, and Android TUN are active. Internet verification is a separate background health signal. It must never block the UI, service teardown, or reconnection state machine.

## Runtime layers

```text
Compose UI — main process
  ├─ Dashboard / Profiles / Import / Settings / Diagnostics
  └─ ViewModels + StateFlow
          │
ProfileRepository
  ├─ Room profiles and logs with multi-instance invalidation
  ├─ DataStore runtime preferences with a multi-process coordinator
  └─ Android Keystore secret protection
          │
Explicit same-application state/traffic IPC
          │
GhostVpnService — private :vpn process
  ├─ physical-network tracking
  ├─ fail-closed VpnService.Builder routes and strict app rules
  ├─ immediate core-ready state publication
  ├─ asynchronous outbound verification
  ├─ native-core ownership and bounded process recovery
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
4. Persist the desired connected state for bounded process recovery.
5. Configure application allow/exclude rules. The VPN package is excluded from full-device/exclusion routing with `addDisallowedApplication(packageName)`; only-selected mode excludes it by omitting it from the allowlist because Android forbids mixing both list types.
6. Establish the Android TUN in fail-closed mode.
7. Start one SSH/Xray runtime against the TUN descriptor.
8. After Xray confirms that the native loop is running, publish `Connected` immediately to the UI process.
9. Launch the initial outbound verification on a dedicated I/O coroutine with a bounded service-level timeout.
10. Start health/statistics monitoring. A failed initial probe is logged and retried; repeated health failures enter the protected reconnection policy.
11. On transport failure, reconnect or close/retain the TUN according to Kill Switch policy.

No synchronous Internet, TLS, SOCKS, or ping probe is permitted on the startup state path.

## Data model

- `VpnProfile`: server, credentials, mode, SNI, SSH TLS policy, payload, proxy, tags, safe HTML note, lock policy, and state.
- `NetworkPreferences`: IP mode, MTU, DNS mode, custom resolvers, reconnect limit.
- `AppRoutingPreferences`: all, only selected, or exclude selected applications.
- `LogEntry`: sanitized timestamped stage event.
- `VpnTrafficStats`: Xray proxy-outbound session counters, rates, and the most recent available latency.

Editable-profile secrets are encrypted before Room persistence. A locked GNX3
profile is stored as one opaque Android Keystore-backed envelope; its ordinary
Room columns contain no server, credential, SNI, proxy, payload, or method
parameters. DataStore contains non-secret operational preferences.

## Import architecture

Import content is processed in this order:

1. GNX3 individual encrypted envelope.
2. GNX2 encrypted backup envelope.
3. Standard Xray JSON outbound document.
4. Ghost Nexora legacy JSON.
5. Protocol links: VMess, VLESS, Trojan, Hysteria2/Hy2, and SSH.

Every successful parse produces a preview and duplicate identity before
storage. Locked GNX3 parameters are resealed immediately and the UI receives
only a masked profile.

## Security invariants

- A normal Dashboard connection never initializes SSH/Xray in the application UI process.
- The VPN package and its SSH/native-core traffic cannot be captured by its own full-device TUN.
- Application-routing failures are not swallowed; TUN startup fails closed when required package rules cannot be applied.
- TUN traffic has no silent direct fallback while outbound verification is pending or failing.
- `Connected` never waits for a remote probe; verification results are independent background health evidence.
- A network probe never retains the `TunnelManager` or `XrayCoreEngine` monitor while performing remote I/O, so disconnect and core shutdown remain available.
- No global TLS trust bypass.
- No empty application allowlist that silently becomes full-device routing.
- No unsanitized secrets in persistent logs.
- No plaintext profile credentials in Room.
- No locked-profile parameters in normal UI/repository flows or diagnostic preflight.
- No executable or network-loaded content in creator HTML notes.

## Extensibility

New protocols should implement:

- profile validation;
- deterministic configuration generation;
- non-destructive diagnostics;
- non-blocking active health verification;
- structured error classification;
- sanitized stage logging;
- unit tests and physical-server interoperability cases.

A protocol is not marked device-verified merely because its configuration compiles, its core process starts, or the UI reaches `Connected`.
