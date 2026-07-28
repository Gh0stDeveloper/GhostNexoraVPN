# Split Tunneling

## Modes

### All applications

All compatible application traffic is routed through the VPN. Ghost Nexora VPN is excluded from its own TUN to avoid a recursive connection loop.

### Only selected applications

Only packages selected in **Applications by VPN** are allowed into the TUN. Android applies the rule with `addAllowedApplication()` before the interface is established.

The mode fails closed when:

- no packages are selected (`APP-ROUTE-001`);
- every selected package is no longer installed (`APP-ROUTE-404`).

It never silently changes to all-app routing.

### Exclude selected applications

All applications except the selected package list use the VPN. Ghost Nexora VPN is also excluded.

## Package visibility

The manifest declares visibility only for launchable applications through a MAIN/LAUNCHER query. It does not request unrestricted `QUERY_ALL_PACKAGES`.

## Persistence

The mode and normalized package-name set are stored in DataStore. Package names are operational settings, not credentials. Changes apply to the next VPN connection because Android does not allow modifying an established TUN allow/disallow list.

## UI behavior

- Search by app label or package name.
- Show whether an app is a system app.
- Clear the selection.
- Display selected count and validation warning.
- Exclude the VPN app from the list.

## Security considerations

- Only-selected mode is the safest choice when a user wants a narrow VPN scope.
- Exclusion mode intentionally allows selected applications to use the physical network.
- Kill Switch protects traffic captured by the VPN; it cannot block an application deliberately excluded from the TUN.
- DNS behavior follows the application routing enforced by Android and the application's own resolver behavior.
- Local/private-network bypass and domain-specific routing are separate future features and must not be inferred from app split tunneling.

## Test cases

1. Browser selected in only-selected mode; another app bypasses the VPN.
2. Browser excluded in exclusion mode; another app uses VPN.
3. Empty only-selected list rejects connection before TUN.
4. Uninstall selected app, then connect; stale-only list rejects safely.
5. Reinstall/refresh and select again.
6. Verify the VPN app does not loop.
7. Test IPv4-only and dual-stack.
8. Test Kill Switch behavior for included and excluded apps.
9. Test OEM Android versions with restricted package visibility.

## Planned routing extensions

- local-network/private-address bypass;
- domain include/exclude rules;
- forced domains through VPN;
- per-profile application rules;
- TCP/UDP policy rules;
- reusable app groups.

These require Xray routing integration and leak tests before being presented as available.