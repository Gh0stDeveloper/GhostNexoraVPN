# Import and Export

## Import sources

Ghost Nexora VPN can import from:

- Android file picker;
- clipboard;
- QR scanner;
- GNX3 individual encrypted binary/text envelope;
- GNX2 encrypted binary/text envelope;
- legacy Ghost Nexora JSON;
- standard Xray outbound JSON;
- VMess, VLESS, Trojan, Hysteria2/Hy2, and SSH links.

## Processing order

1. Detect GNX3, inspect its declared policy, then authenticate that policy and
   content with its creator password or official-app compatibility material.
2. Detect GNX2 and request its password.
3. Parse standard Xray JSON when a compatible outbound exists.
4. Parse legacy Ghost Nexora JSON.
5. Parse protocol links line by line.
6. Build technical summaries.
7. Compare security-relevant fingerprints with existing profiles.
8. Show a preview before any database write.

A locked GNX3 profile is resealed with Android Keystore immediately after its
container and identity are validated. The pending import and preview contain
only its masked view.

## Preview

The preview can show:

- protocol;
- server and port;
- transport;
- TLS or REALITY;
- SNI;
- Host/path;
- gRPC service name;
- proxy;
- warnings;
- possible duplicate count.

Credentials are not rendered in the preview.

For a locked GNX3 profile, server, protocol, transport, TLS/SNI, proxy, payload,
credentials, and method are all replaced by protected placeholders. The
creator's sanitized HTML note remains visible.

## Merge

Merge imports only profiles whose canonical fingerprint is not already present. It also removes duplicates repeated within the incoming content.

## Replace

Replace deletes existing profiles, then stores one copy of every unique imported configuration. It requires explicit user action.

## Duplicate fingerprint

The local fingerprint includes mode, endpoint, credentials, security, payload/Xray options, and proxy settings. It excludes profile name and ID so renamed copies are recognized.

## GNX3 individual export

Every editable profile has an individual export action. Before writing or
sharing the file, the creator can:

- lock or leave the profile editable;
- choose a creator password or app-managed compatibility mode;
- add an HTML/CSS note and preview the sanitized result.

Locked profiles cannot be edited, duplicated, diagnosed outside the VPN
service, or re-exported after import. This is an application policy protected
by authenticated encryption; it is not a claim that a hostile, modified client
can never recover parameters while using them.

Creator-password mode is recommended when confidentiality outside official
builds matters. App-managed mode avoids distributing a password and works
across APKs signed by the same developer, but any compatibility secret shipped
in a client can ultimately be reconstructed by an attacker controlling that
client.

## HTML/CSS creator notes

Notes permit presentation markup, headings, lists, tables, inline styles,
`<style>` blocks, and `http`, `https`, `mailto`, or `tel` contact links.

The importer removes scripts, frames, objects, forms, event handlers, active
URL schemes, remote CSS, resource URLs, and overlay-oriented CSS. The viewer
also disables JavaScript, DOM/database storage, file/content access, images,
network loads, mixed content, frames, forms, and embedded objects. A contact
link opens in an external application only after a user tap.

## GNX2 backup export

Exports require a password of at least ten characters. The password is held only for the operation and cleared from the UI state afterward. The export can be written through Android's Storage Access Framework or to the app-managed Downloads workflow where supported.

Locked profiles are intentionally excluded from GNX2 backups because their
creator prohibited re-export and the ordinary repository never exposes their
parameters.

## Safety limits

- Import size is limited.
- Malformed JSON is rejected.
- Unsupported Xray outbounds are ignored rather than converted incorrectly.
- Invalid protocol links are not stored.
- Password failures do not expose partial plaintext.
- Modified GNX3 policy/header/ciphertext data fails authentication.
- HTML note input and rendered output are bounded.
- Import results do not bypass normal profile validation during connection.

## Compatibility warning

Successful import means the configuration was parsed into the application's model. It does not prove the remote server exists or that every external-client extension is supported.

## Planned enhancements

- selected-profile backup archive;
- full encrypted application backup;
- expiration and minimum-version policies;
- verifiable creator identity/signature;
- device-binding policy;
- conflict-resolution UI for similar but non-identical profiles;
- typed Xray schema migration;
- protected private-key bundle support.
