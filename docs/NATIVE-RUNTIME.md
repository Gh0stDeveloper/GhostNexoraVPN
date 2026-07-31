# Native VPN runtime

Ghost Nexora VPN uses one native packet-processing plane. The Android TUN file
descriptor is consumed by the TUN implementation bundled in
AndroidLibXrayLite; the app must not attach a second `hev-tun2socks` process to
the same descriptor.

## Executable routing graph

```text
Android VpnService
  └─ TUN file descriptor
       └─ AndroidLibXrayLite / Xray native TUN
            ├─ SSH modes
            │    └─ loopback SOCKS5
            │         └─ JSch direct-tcpip
            │              └─ optional proxy → optional TLS/SNI → optional payload → SSH
            ├─ VLESS / VMess
            │    └─ Xray native outbound
            ├─ Trojan
            │    └─ Xray Trojan/TLS outbound
            └─ Hysteria2
                 └─ Xray Hysteria2/QUIC outbound
```

This preserves one owner for every packet read from the TUN. Adding a separate
Tun2Socks process without replacing the current Xray TUN implementation would
create competing readers and an invalid data plane.

## Outbound loop protection

Java/Kotlin transport sockets use three independent controls:

1. `VpnService.protect(Socket)` is mandatory before an SSH or upstream-proxy
   socket connects. Failure is fail-closed and reported as `VPN-LOOP-001`.
2. Android `Network.bindSocket` binds the socket to a physical network with
   `NET_CAPABILITY_NOT_VPN` when the platform exposes one.
3. The application package is excluded from its own `VpnService.Builder`
   routing rules, covering the private `:vpn` process and native core.

`setUnderlyingNetworks` also informs Android which physical network underlies
the VPN. Native Xray sockets are created inside the core and are not exposed as
Java `Socket` instances; they rely on the application-UID exclusion and the
selected underlying network.

## Protocol capability matrix

| Mode | TUN adapter | Protocol core | TCP | UDP | Current limitation |
|---|---|---|---:|---:|---|
| SSH direct / TLS / payload / proxy combinations | Xray native TUN | JSch + local SOCKS5/direct-tcpip | Yes | No | BadVPN UDP Gateway is not packaged |
| VLESS / VMess | Xray native TUN | Xray | Yes | Yes | Exact transports depend on the bundled core |
| Trojan | Xray native TUN | Xray Trojan/TLS | Yes | Yes | Requires a valid server profile and TLS configuration |
| Hysteria2 | Xray native TUN | Xray Hysteria2/QUIC | Yes | Yes | Hysteria v1 and a separate Hysteria binary are not packaged |

The UI and runtime logs must not claim generic UDP-over-SSH, BadVPN, Hysteria
v1, a standalone `libhysteria.so`, or a standalone `libhev-tun2socks.so` unless
the corresponding verified binaries, lifecycle management, server contract,
ABI packaging, tests, and leak controls are added in a later release.

## Adding another native core

A future core must provide all of the following before being enabled:

- deterministic configuration generation;
- ABI packages for `arm64-v8a`, `armeabi-v7a`, and `x86_64`;
- a single, explicit owner of the TUN descriptor;
- outbound loop exclusion;
- bounded startup, shutdown, and recovery;
- sanitized logs and structured failures;
- TCP, UDP, DNS, IPv4/IPv6, leak, battery, and real-server interoperability
  tests.
