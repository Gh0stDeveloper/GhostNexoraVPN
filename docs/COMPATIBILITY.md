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
| Outbound preflight and active HTTP verification | CI verified; device behavior pending |
| JSch Android random provider | CI verified |
| SSH direct/password | Device testing pending |
| SSH + TLS/SNI | Device testing pending |
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
| Application split tunneling | CI verified; OEM/device routing pending |
| Domain/private-network split routing | Not implemented |
| Certificate pinning UI | Not implemented |
| Biometric app lock | Not implemented |

## Promotion criteria

A combination moves from device testing pending to device verified only when `TEST-MATRIX.md` evidence is supplied. A feature moves from experimental to supported after:

- multiple physical devices;
- multiple networks/carriers;
- failure-path tests;
- handover/background tests when relevant;
- no unresolved critical leak or connectivity issue;
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
- usability/accessibility.

The in-app Compatibility screen summarizes this policy for users.