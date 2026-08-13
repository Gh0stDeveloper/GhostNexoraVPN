# Security Architecture

## Threat model

Ghost Nexora VPN protects configuration confidentiality, update integrity, transport verification, and routing correctness against ordinary application compromise and accidental leakage. It does not assume an attacker with permanent root and full control of the device can be completely excluded.

## Profile storage

Sensitive profile fields are encrypted before Room persistence using AES-256-GCM and a non-exportable Android Keystore key. Additional authenticated data binds ciphertext to the profile and field identity. Random nonces are generated for each operation.

For a locked GNX3 import, the entire validated profile is immediately resealed
as one AES-256-GCM envelope with a separate non-exportable Keystore key. The
ordinary Room columns contain only its name, safe creator note, package
identity, UI metadata, and opaque ciphertext. Only the private VPN service can
request the full profile; edit, duplicate, re-export, and standalone diagnostic
flows cannot.

## GNX3 individual exports

GNX3 uses:

- one random 256-bit data key per file;
- GZIP before AES-256-GCM payload encryption;
- a separate AES-GCM operation to wrap the data key;
- HMAC-SHA256 over the complete versioned body;
- independent random salt, wrapping nonce, and payload nonce;
- PBKDF2-HMAC-SHA256 with 420,000 iterations for creator passwords;
- HMAC-based key expansion for the app-managed compatibility mode.

The lock flag and protection mode are authenticated. Salts and nonces are
stored with the ciphertext because they are not secrets, but they are generated
afresh and never fixed or reused by the exporter.

App-managed compatibility material is derived from the APK signing identity,
package name, and split native/application data. It avoids a literal final key
in DEX and rejects differently signed builds. It is not equivalent to a
server-held secret or hardware security module: anyone controlling the APK and
process can eventually reconstruct it. Creator-password mode provides the
stronger offline boundary.

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

## Creator HTML notes

Imported notes pass through a conservative element, attribute, protocol, and
CSS allowlist. Active elements, handlers, forms, embedded resources, remote
CSS, and overlay-oriented declarations are removed. Rendering uses a WebView
with JavaScript, storage, database, file/content access, images, network loads,
mixed content, frames, forms, objects, and media disabled, plus a restrictive
Content Security Policy. Allowed contact links are delegated externally only
after user interaction.

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

When a locked profile is active, exact host, port, username, password, SNI,
payload, proxy, connection method, and sensitive SSH/TLS/SOCKS stage details
are replaced before persistent logging.

## TLS

- Platform certificate trust and hostname verification are used by default.
- SNI is explicit when required.
- SSH-over-TLS profiles may explicitly enable HTTP Custom compatibility. That
  scoped policy accepts private/self-signed chains and SNI/SAN mismatch, checks
  leaf-certificate validity, and delegates final identity authentication to the
  persistent SSH host key.
- No compatibility trust manager is installed globally or reused by strict TLS,
  Xray protocols, updater, or API traffic.
- REALITY parameters are accepted only when present in the profile.

Planned explicit certificate/public-key pinning should be opt-in per profile and must show issuer, subject, validity, and fingerprint before trust.

## SSH

- Known-host fingerprints persist locally.
- Changed host identity is rejected.
- JSch random generation is injected directly using `SecureRandom`.
- Local SOCKS listens on loopback only.
- Passwords are never logged.
- The application package is excluded from its own full-device TUN so JSch transport sockets use the physical network rather than recursively re-entering Xray.

Private-key support must encrypt imported key material and passphrases at rest and erase temporary buffers where feasible.

## Routing safety

- Dashboard connection ownership stays in the private, non-exported `:vpn` process; the separate non-destructive diagnostic action may create a temporary preflight runtime.
- In all-app and exclude-selected modes, `VpnService.Builder.addDisallowedApplication(packageName)` is mandatory and exceptions are not ignored.
- In only-selected mode, Android forbids combining allowed and disallowed lists; the VPN package is excluded by omission from the allowlist.
- The TUN remains fail-closed while background outbound verification is pending or failing: captured traffic has no direct fallback.
- `Connected` is published only after SSH/Xray/TUN startup succeeds, but it does not wait for a remote connectivity probe.
- Initial and periodic probes execute outside the service startup path.
- Remote I/O does not hold the `TunnelManager` or `XrayCoreEngine` monitor, allowing disconnect and core shutdown to proceed during a slow probe.
- Kill Switch behavior is explicit.
- Application-only allowlists fail closed when empty or unavailable.

## Updates

The updater validates:

- repository release identity;
- explicit version code or semantic version fallback;
- package name;
- newer APK version;
- complete download;
- SHA-256 when supplied.

Production releases should additionally verify the APK signing certificate against an embedded expected digest before installation.

## Runtime hardening

- R8/resource shrinking and JNI keep rules protect Release structure.
- Sensitive screens use `FLAG_SECURE`.
- The locked-profile path checks Android debugger state, `TracerPid`, and
  common instrumentation/root-module mappings before decryption.
- Native and JVM byte buffers are overwritten when their lifetime ends where
  the API permits it.
- APK signing identity participates in app-managed GNX3 compatibility.

These measures raise analysis cost and stop ordinary leakage. They do not make
DEX/native code impossible to deobfuscate, reliably detect every renamed hook,
or keep data encrypted while SSH/Xray must actively consume it. Java/Kotlin
`String` objects are immutable and cannot be deterministically wiped. A rooted
device or hostile modified APK remains outside the confidentiality guarantee.

## Device protection roadmap

Planned defense-in-depth features:

- biometric/PIN application lock;
- automatic lock timeout;
- APK signature verification;
- SBOM generation;
- dependency and secret scanning;
- tamper evidence.

Root or hook detection must not be described as infallible and should not automatically block legitimate users without a recoverable policy.

## Security review checklist

- No new secrets in source control.
- No plaintext credentials in Room/DataStore.
- No locked network parameters in ordinary Room columns or UI flows.
- No executable/network-loaded creator-note content.
- No relaxed TLS defaults or global trust-all manager.
- No unbounded input or delays.
- No direct fallback route.
- No self-routing loop for the VPN package or its private process.
- No synchronous remote probe before Connected-state publication.
- No network probe holding a teardown lock.
- No exported Android component without need.
- No new reflection-dependent runtime class without R8 verification.
- Unit, Lint, Debug, Release/R8, native ABI, and manifest checks pass.
- User-facing documentation matches actual behavior.
