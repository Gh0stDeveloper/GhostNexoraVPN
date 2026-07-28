# DNS and Routing

## Android TUN

The VPN interface captures IPv4 by default. IPv6 address and default route are added only when the selected IP mode captures IPv6. The same MTU is passed to Android and Xray.

## IP modes

| Mode | Android routes | Xray DNS strategy |
|---|---|---|
| IPv4 only | IPv4 default route | IPv4 only |
| IPv4 preferred | IPv4 + IPv6 routes | prefers IPv4 |
| IPv4 + IPv6 | IPv4 + IPv6 routes | both families |

IPv4-only mode prevents silent IPv6 blackholes when the remote server cannot carry IPv6.

## DNS modes

- Automatic protected DNS.
- Cloudflare.
- Google.
- Custom literal IPv4/IPv6 resolvers.

Android receives the selected resolver addresses. Xray receives the same policy and an explicit rule for TUN TCP/UDP port 53. Cloudflare and Google DoH names have static bootstrap addresses to avoid recursive resolution.

System DNS, DoT, detailed cache policy, and privacy-domain logging controls remain future work unless they are visible in the current Settings screen.

## DNS leak policy

The intended invariant is that captured application DNS is handled by the VPN routing policy. Validation includes:

- no direct catch-all rule for TUN traffic;
- explicit DNS outbound;
- self-exclusion to avoid core loops;
- application allow/exclude rules set before TUN establishment.

Physical leak testing is still required on OEM Android variants, especially with private DNS, IPv6, captive portals, and vendor VPN optimizations.

## Split tunneling

Application routing modes:

1. **All applications** — all compatible applications enter the VPN; Ghost Nexora VPN is excluded.
2. **Only selected** — Android `addAllowedApplication()` is used for installed selected packages.
3. **Exclude selected** — Android `addDisallowedApplication()` is used for the app itself and selected packages.

An empty only-selected list is rejected with `APP-ROUTE-001`. If no selected packages remain installed, TUN creation is rejected with `APP-ROUTE-404`.

Application rules cannot be changed on an established Android TUN. Settings apply on the next connection.

## Local-network bypass

Generic local/private-address bypass, domain rules, and per-protocol rules are roadmap items. They require explicit routing rules and tests to avoid accidental DNS or traffic leaks. They must not be approximated by silently routing unmatched traffic directly.

## MTU

Presets: 1280, 1360, 1400, 1450, and 1500. Default: 1400.

Automatic path-MTU diagnosis remains roadmap work. A future implementation should test multiple sizes through the actual outbound and report evidence before changing the stored value.

## Routing acceptance tests

- application self-exclusion;
- only-selected allowlist;
- exclusion list;
- missing selected package;
- IPv4-only browsing;
- IPv6-capable browsing;
- DNS resolution inside the tunnel;
- private DNS enabled/disabled;
- Wi-Fi/mobile handover;
- Kill Switch with failed outbound;
- no direct TUN fallback in Xray rules.