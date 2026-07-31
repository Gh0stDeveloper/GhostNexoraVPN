# Compatibility Status

Ghost Nexora VPN uses evidence-based compatibility labels.

## Labels

### CI verified

The feature has automated evidence for source compilation, unit tests where applicable, Android Lint, Debug/JNI, Release/R8, and required packaged runtime classes.

### Device testing pending

The implementation exists and CI passes, but no reproducible physical Android + real server report has been recorded for the exact combination.

### Experimental

The implementation exists, but stability under relevant network conditions is not sufficiently characterized.

### Not implemented

The feature appears only in the roadmap/documentation and must not be shown as operational in the UI.

## Current matrix

| Feature | Status |
|---|---|
| Android TUN IPv4/IP modes/MTU/DNS generation | CI verified |
| Fail-closed TUN startup with mandatory application self-bypass | CI verified by Build Android #501; device behavior pending |
| Immediate core-ready `Connected` state and asynchronous outbound verification | CI verified by Build Android #501; device behavior pending |
| Non-blocking disconnect while a health probe is active | CI/source verified; physical regression pending |
| JSch Android random provider | CI verified |
| SSH direct/password | Device testing pending |
| SSH + TLS/SNI strict and HTTP Custom-compatible | CI verified; real-server device testing pending |
| Segmented payload engine | CI verified; remote response matrix pending |
| HTTP CONNECT/SOCKS5 upstream proxy without auth | Device testing pending |
| Authenticated upstream proxy | Not implemented as a production claim |
| SSH private keys | Not implemented as a production claim |
| VLESS/VMess configuration/import | CI verified; combinations pending |
| Trojan configuration/import | CI verified; combinations pending |
| Hysteria2 configuration/import | Experimental |
| Standard Xray JSON import | CI verified |
| SSH URI import | CI verified |
| GNX2 encrypted import/export | CI verified |
| GNX3 individual import/export and lock policy | CI verified |
| Safe HTML/CSS creator notes | CI verified |
| Application split tunneling | CI verified; OEM/device routing pending |
| Domain/private-network split routing | Not implemented |
| Certificate pinning UI | Not implemented |
| Biometric app lock | Not implemented |

## Connection-state interpretation

The Dashboard `Connected` state means the selected transport, Xray native loop, and Android TUN are active. The initial Internet/TLS/SOCKS probe runs afterward in the background. This avoids a UI deadlock and does not weaken captured-traffic routing: the TUN has no silent direct fallback while verification is pending or failing.

A feature is not promoted to device verified merely because the UI says `Connected`. The evidence record must include successful real traffic, health verification, leak checks, and teardown/recovery behavior.

## Promotion criteria

A combination moves from device testing pending to device verified only when `TEST-MATRIX.md` evidence is supplied. A feature moves from experimental to supported after:

- multiple physical devices;
- multiple networks/carriers;
- failure-path tests;
- handover/background tests when relevant;
- no unresolved critical leak or connectivity issue;
- no startup freeze when health endpoints are delayed or unreachable;
- no self-routing of the VPN package or JSch transport;
- documentation and troubleshooting updates.

## Product comparison

The project may target feature parity or stronger diagnostics/security than HTTP Injector or HTTP Custom, but it should not claim superiority without a documented benchmark covering:

- protocol interoperability;
- connection success rate;
- handover recovery;
- leak behavior;
- battery usage;
- latency/throughput;
- configuration compatibility;
- security defaults;
- startup responsiveness and cancellation;
- usability/accessibility.

The in-app Compatibility screen summarizes this policy for users.
