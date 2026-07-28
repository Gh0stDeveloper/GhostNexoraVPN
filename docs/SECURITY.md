# Security Architecture

## Threat model

Ghost Nexora VPN protects configuration confidentiality, update integrity, transport verification, and routing correctness against ordinary application compromise and accidental leakage. It does not assume an attacker with permanent root and full control of the device can be completely excluded.

## Profile storage

Sensitive profile fields are encrypted before Room persistence using AES-256-GCM and a non-exportable Android Keystore key. Additional authenticated data binds ciphertext to the profile and field identity. Random nonces are generated for each operation.

## GNX2 exports

GNX2 uses:

- random data key per export;
- AES-256-GCM payload encryption;
- PBKDF2-HMAC-SHA256 password derivation;
- random salt;
- separate authenticated data-key wrapping;
- HMAC-SHA256 over the versioned container;
- GZIP compression before encryption.

No fixed master password or export key is embedded in the application.

## Logging

The sanitizer removes or masks:

- passwords;
- Authorization headers;
- Bearer and Basic credentials;
- tokens/API keys;
- URI credentials;
- private-key blocks;
- long opaque values likely to be secrets.

Protocol payloads are not written in full. Diagnostic exports are UTF-8 and explicitly marked sanitized.

## TLS

- Platform certificate trust is used.
- Hostname verification is enabled by default.
- SNI is explicit when required.
- SSH profiles may explicitly allow SNI/SAN mismatch for HTTP Custom
  compatibility; platform certificate-chain validation remains active.
- Global trust-all is prohibited.
- REALITY parameters are accepted only when present in the profile.

Planned explicit certificate/public-key pinning should be opt-in per profile and must show issuer, subject, validity, and fingerprint before trust.

## SSH

- Known-host fingerprints persist locally.
- Changed host identity is rejected.
- JSch random generation is injected directly using `SecureRandom`.
- Local SOCKS listens on loopback only.
- Passwords are never logged.

Private-key support must encrypt imported key material and passphrases at rest and erase temporary buffers where feasible.

## Routing safety

- Remote preflight runs before Android default routes are installed.
- Active outbound verification runs after core startup.
- TUN traffic has no silent direct fallback.
- Kill Switch behavior is explicit.
- Application-only allowlists fail closed when empty or unavailable.
- The VPN process is excluded where needed to prevent routing loops.

## Updates

The updater validates:

- repository release identity;
- explicit version code or semantic version fallback;
- package name;
- newer APK version;
- complete download;
- SHA-256 when supplied.

Production releases should additionally verify the APK signing certificate against an embedded expected digest before installation.

## Device protection roadmap

Planned defense-in-depth features:

- biometric/PIN application lock;
- `FLAG_SECURE` on secret and key screens;
- automatic lock timeout;
- temporary secret buffer wiping;
- APK signature verification;
- SBOM generation;
- dependency and secret scanning;
- optional informational root/hooking/debugger signals;
- tamper evidence.

Root or hook detection must not be described as infallible and should not automatically block legitimate users without a recoverable policy.

## Security review checklist

- No new secrets in source control.
- No plaintext credentials in Room/DataStore.
- No relaxed TLS defaults or global trust-all manager.
- No unbounded input or delays.
- No direct fallback route.
- No exported Android component without need.
- No new reflection-dependent runtime class without R8 verification.
- Unit, Lint, Debug, Release/R8, native ABI, and manifest checks pass.
- User-facing documentation matches actual behavior.
