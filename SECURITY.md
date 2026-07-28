# Security Policy

## Supported versions

Security fixes are applied to the current development branch and the most recent stable release. Older APKs may contain dependencies or runtime behavior that is no longer maintained.

| Version | Security support |
|---|---|
| Current stable release | Supported |
| Current draft PR build | Supported for testing; not a stable release |
| Older releases | Upgrade required |

## Reporting a vulnerability

Do not publish credentials, private VPN configurations, server addresses, private keys, exported GNX files, or diagnostic reports containing personal infrastructure in a public issue.

Report security concerns privately to:

- Email: `ghostnexora@gmail.com`
- Telegram: `@Gh0stDeveloper`

Include:

1. A concise description of the issue.
2. Affected version and Android version.
3. Reproduction steps.
4. Expected and observed behavior.
5. A sanitized proof of concept when relevant.
6. Whether credentials, profile confidentiality, VPN routing, update integrity, or local storage are affected.

## Security boundaries

Ghost Nexora VPN protects profile data at rest with Android Keystore and authenticated encryption. Password-protected GNX exports use versioned authenticated encryption. Logs are sanitized before storage and export.

The following are not absolute guarantees:

- A user controlling an unlocked or rooted device can inspect application memory, files, and network behavior.
- Root, hooking, debugger, and tamper detection are defense-in-depth signals, not an unbreakable boundary.
- Client-side configuration protection can raise extraction cost but cannot make server details mathematically impossible to recover while the client must use them.
- TLS pinning protects only configurations that specify an expected certificate or public-key fingerprint.
- A VPN client cannot make an insecure or compromised remote server trustworthy.

## Prohibited security shortcuts

The project does not accept:

- global `Trust All` certificate validation;
- disabled hostname verification as a default;
- fixed encryption keys or embedded master passwords;
- secret values in logs or crash reports;
- silent fallback from protected VPN routing to direct Internet;
- update installation without package/version validation;
- claims of protocol interoperability without reproducible tests.

## Dependency and release security

The release workflow should maintain:

- dependency update monitoring;
- Android Lint and unit tests;
- Debug and Release/R8 builds;
- native ABI verification;
- manifest checks;
- secret scanning;
- dependency vulnerability scanning;
- SBOM generation;
- APK SHA-256 and signing-certificate verification.

See `docs/SECURITY.md` and `docs/BUILD-AND-RELEASE.md` for implementation details.