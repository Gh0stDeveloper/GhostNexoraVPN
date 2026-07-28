# Import and Export

## Import sources

Ghost Nexora VPN can import from:

- Android file picker;
- clipboard;
- QR scanner;
- GNX2 encrypted binary/text envelope;
- legacy Ghost Nexora JSON;
- standard Xray outbound JSON;
- VMess, VLESS, Trojan, Hysteria2/Hy2, and SSH links.

## Processing order

1. Detect GNX2 and request its password.
2. Parse standard Xray JSON when a compatible outbound exists.
3. Parse legacy Ghost Nexora JSON.
4. Parse protocol links line by line.
5. Build technical summaries.
6. Compare security-relevant fingerprints with existing profiles.
7. Show a preview before any database write.

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

## Merge

Merge imports only profiles whose canonical fingerprint is not already present. It also removes duplicates repeated within the incoming content.

## Replace

Replace deletes existing profiles, then stores one copy of every unique imported configuration. It requires explicit user action.

## Duplicate fingerprint

The local fingerprint includes mode, endpoint, credentials, security, payload/Xray options, and proxy settings. It excludes profile name and ID so renamed copies are recognized.

## GNX2 export

Exports require a password of at least ten characters. The password is held only for the operation and cleared from the UI state afterward. The export can be written through Android's Storage Access Framework or to the app-managed Downloads workflow where supported.

## Safety limits

- Import size is limited.
- Malformed JSON is rejected.
- Unsupported Xray outbounds are ignored rather than converted incorrectly.
- Invalid protocol links are not stored.
- Password failures do not expose partial plaintext.
- Import results do not bypass normal profile validation during connection.

## Compatibility warning

Successful import means the configuration was parsed into the application's model. It does not prove the remote server exists or that every external-client extension is supported.

## Planned enhancements

- selected-profile backup archive;
- full encrypted application backup;
- expiration and minimum-version policies;
- creator signature verification;
- device-binding policy;
- conflict-resolution UI for similar but non-identical profiles;
- typed Xray schema migration;
- protected private-key bundle support.