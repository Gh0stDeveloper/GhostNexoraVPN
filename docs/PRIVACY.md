# Privacy

## Scope

This document describes the current application architecture. A published store release should provide a jurisdiction-appropriate public privacy policy with the final distribution, analytics, crash-reporting, and update configuration.

## Local data

Ghost Nexora VPN stores locally:

- VPN profiles and encrypted credentials;
- sanitized creator HTML notes and opaque locked-profile envelopes;
- application settings;
- selected split-tunneling package names;
- trusted SSH fingerprints;
- sanitized connection logs;
- update-check metadata.

## Network data

When a VPN is connected, selected application traffic is sent through the configured remote server. The remote server operator and upstream providers may observe traffic according to the protocol and destination encryption. Ghost Nexora VPN does not make an untrusted server private.

The app performs connectivity checks against HTTP 204 endpoints through the configured outbound. Update checks contact GitHub Releases for this repository.

## Logs

Logs are designed for local diagnostics. Sanitization removes common credential/token forms before storage and export. The app should not transmit diagnostic reports automatically. Users choose whether to export and share them.

A future privacy mode may suppress destination-domain details from DNS or transport logs. Documentation must match the actual setting when implemented.

## Permissions

- VPN authorization: creates the Android network tunnel.
- Internet/network state: connects to configured servers and detects physical networks.
- Foreground service/notifications: keeps the VPN operational and visible.
- Battery optimization exemption: optional persistent operation.
- Overlay: optional floating control.
- Install packages: optional verified application update installation.
- Boot completed: optional reconnect policy.

File import/export uses Android's document APIs; broad legacy storage permissions are not required.

Creator-note resources are not fetched inside the application. Links open only
after the user selects them and are handed to an external application.

## Application visibility

The app queries launchable applications to present the split-tunneling selector. It stores only selected package names. It does not need full package visibility through `QUERY_ALL_PACKAGES`.

## Analytics and advertising

The current architecture should not claim analytics or advertising collection unless such SDKs are present and configured. Before adding any analytics/crash SDK:

- document data fields and retention;
- provide consent where required;
- avoid profile/server/credential collection;
- disable automatic sensitive network breadcrumbs;
- update this policy and store disclosure.

## Data deletion

Users can remove profiles, logs, trusted fingerprints, and application data. Clearing Android application storage deletes local data and Keystore-bound profile access. Export encrypted backups before clearing or uninstalling.

## Security limitations

A rooted or compromised device may expose local memory or configuration while the app is using it. Remote server privacy and destination encryption remain the user's/server operator's responsibility.

## Contact

Privacy questions:

- `ghostnexora@gmail.com`
- Telegram `@Gh0stDeveloper`
