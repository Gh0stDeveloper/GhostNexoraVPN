# Contributing

## Development principles

Changes must preserve these invariants:

- no connected state without verified outbound Internet;
- no Android default routes before preflight succeeds;
- no global TLS trust bypass;
- no silent direct fallback for captured TUN traffic;
- no plaintext credential persistence;
- no unsanitized secrets in logs;
- no empty application allowlist that becomes full-device routing;
- no protocol compatibility claim without evidence.

## Workflow

1. Create a focused branch.
2. Describe the user problem and expected behavior.
3. Add or update tests.
4. Run unit tests, Lint, Debug, and Release/R8.
5. Update documentation and compatibility status.
6. Keep the PR draft until device/server validation is complete when runtime behavior changes.

## Code changes

Prefer:

- typed models and deterministic configuration builders;
- bounded inputs, timeouts, retries, and buffers;
- structured error codes;
- sanitized stage logs;
- Android platform APIs over broad permissions;
- focused R8 keep rules;
- explicit failure instead of unsafe fallback.

Avoid:

- catch-all exception suppression;
- global `Trust All` TLS;
- hard-coded credentials or keys;
- long-running work on the main thread;
- hidden protocol defaults that alter imported profiles;
- broad package visibility;
- disabling Lint/R8 to make a build pass;
- copying proprietary source or UI from other applications.

## Tests

A parser or configuration change requires unit tests. A transport change requires:

- success and failure-path tests where locally reproducible;
- structured error validation;
- Release/R8 packaging verification when reflection/JNI is involved;
- a physical test-matrix entry before compatibility is marked verified.

## Documentation

Update the relevant files in `docs/`, `README.md`, and `CHANGELOG.md`. Clearly label features as:

- CI verified;
- device testing pending;
- experimental;
- not implemented.

## Security reports

Do not open public issues containing credentials, private keys, server details, or unsanitized diagnostic logs. Follow `SECURITY.md`.

## Release changes

A public release must use the production signing identity, increment `versionCode`, publish checksums/signing metadata, and pass the release checklist in `docs/BUILD-AND-RELEASE.md`.