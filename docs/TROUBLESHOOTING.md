# Troubleshooting

Always begin with **Settings > Connection engine > Run connection diagnostics**. Export the sanitized Logs report when requesting support.

## No physical network — `NET-001`

- Confirm mobile data or Wi-Fi works without the VPN.
- Disable another active VPN.
- Check captive-portal login.
- Reopen the application after Android network changes.

## DNS failure — `DNS-001`

- Verify the server hostname.
- Try Automatic protected DNS, then Cloudflare or Google.
- Use IPv4-only mode when the carrier advertises unusable IPv6.
- Confirm private DNS settings are not blocking the resolver.

## Port rejected — `TCP-002`

- Verify host and port.
- Confirm the remote service listens on that port.
- Test whether the carrier blocks the port.
- Verify the configured proxy endpoint when proxy mode is selected.

## Proxy auth required — `PROXY-407`

The upstream proxy requested credentials. Current production proxy-auth support may be incomplete depending on the mode. Use a no-auth proxy or wait for the documented authenticated-proxy implementation; do not place proxy credentials inside logs.

## TLS/SNI rejected — `TLS-004`

- Use the exact SNI supplied by the server administrator.
- Confirm the certificate includes that name.
- Check device date/time.
- Do not disable hostname verification.
- For private certificates, use future explicit pinning rather than trust-all.

## SSH authentication — `SSH-401`

- Verify username/password.
- Confirm password authentication is enabled server-side.
- Check account expiration or connection limits.
- Do not confuse proxy credentials with SSH credentials.

## SSH identity changed — `SSH-409`

Do not reset fingerprints automatically. Confirm the server was reinstalled or its key intentionally rotated, then reset trusted SSH fingerprints from Settings.

## SSH runtime — `SSH-500`

Install the latest APK and export the diagnostic report. CI verifies required JSch classes after R8, so a recurring runtime error may indicate an outdated APK, signature/install conflict, or OEM-specific behavior.

## Xray UUID — `XRAY-UUID`

Use a valid UUID. Check that VMess/VLESS was imported into the correct protocol and that whitespace was not added.

## Core started but no Internet — `ROUTE-204`

Check:

1. protocol;
2. UUID/password;
3. TLS/REALITY;
4. SNI;
5. Host/path/service name;
6. transport;
7. IP mode;
8. MTU;
9. DNS;
10. server outbound policy.

Start with IPv4 only, MTU 1400, and Automatic protected DNS.

## Application routing — `APP-ROUTE-001`

Only-selected mode has no valid selection. Select at least one installed application or switch to all/exclude mode.

## Application missing — `APP-ROUTE-404`

A selected package was uninstalled or is unavailable. Refresh the application list and remove stale packages.

## TUN failure — `TUN-500`

- Disconnect another VPN.
- Revoke and grant VPN authorization again.
- Reboot the device.
- Check OEM battery/security tools.
- Verify the only-selected list is valid.

## Connection succeeds but pages stall

- Lower MTU from 1400 to 1360 or 1280.
- Test IPv4-only.
- Change DNS mode.
- Check whether only some applications are routed.
- Export logs after reproducing the stall.

## Wi-Fi/mobile handover fails

- Confirm automatic reconnection is enabled.
- Disable battery restrictions.
- Record whether the physical network returned before retry exhaustion.
- Test with Kill Switch both enabled and disabled to distinguish intentional blocking.

## Cannot install test APK

A previous APK may use a different signing key. Uninstalling can remove local profiles, so export a GNX backup first. Prefer a properly signed release artifact for long-term upgrades.

## Required support information

- app version and version code;
- Android version, manufacturer, and model;
- selected connection mode;
- IP, DNS, MTU, and app-routing mode;
- exact structured error code;
- sanitized diagnostic export;
- whether the same profile works in another client;
- whether the failure occurs on Wi-Fi, mobile data, or both.