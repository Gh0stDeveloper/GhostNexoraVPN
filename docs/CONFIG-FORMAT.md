# Configuration Formats

## Internal profile model

A stored profile contains:

- unique ID and display name;
- server host and port;
- username/UUID and password/auth;
- connection mode;
- TLS enabled flag and SNI;
- payload or Xray option string;
- proxy type, host, and port;
- tags and notes;
- enabled/favorite/last-used metadata.

Sensitive values are encrypted before Room persistence.

## GNX2

GNX2 is the recommended portable format. It is a versioned encrypted container rather than plain JSON. See `SECURITY.md` for cryptographic details.

Properties:

- password protected;
- authenticated encryption;
- random key/salt/nonce material;
- multiple profile support;
- text-envelope representation for clipboard/QR workflows;
- integrity failure on modification or wrong password.

## Legacy Ghost Nexora JSON

Legacy JSON remains importable for migration. It is not recommended for sharing credentials because it is plaintext.

Simplified document structure:

```json
{
  "appName": "Ghost Nexora VPN",
  "version": "1.0.35",
  "exportedAt": "2026-07-28T00:00:00Z",
  "profiles": [
    {
      "name": "Example",
      "host": "vpn.example.com",
      "port": 443,
      "connectionMode": "v2ray",
      "username": "uuid",
      "sslEnabled": true,
      "sni": "cdn.example.com",
      "payload": "protocol=vless | net=ws | path=/vpn | security=tls"
    }
  ]
}
```

## Protocol links

Supported imports:

- `vmess://`
- `vless://`
- `trojan://`
- `hysteria2://`
- `hy2://`
- `ssh://`

The parser preserves recognized transport and security query fields. Unknown parameters may not affect the generated profile and should be reviewed in the technical preview.

## SSH link convention

Ghost Nexora accepts a practical SSH URI convention:

```text
ssh://username:password@server:port?mode=ssl_payload_proxy&sni=cdn.example.com&payload=...&proxyHost=proxy.example.com&proxyPort=8080&proxyType=http#ProfileName
```

Recognized `mode` values include:

- `direct`
- `ssl` / `tls`
- `payload`
- `ssl_payload`
- `proxy`
- `payload_proxy`
- `ssl_payload_proxy`

This is an application import convention, not a claim that all SSH clients use the same URI schema.

## Standard Xray JSON

The importer recognizes compatible Xray outbound objects from:

- a root `outbounds` array;
- a root `outbound` object;
- a standalone outbound object;
- an array of outbound objects.

Supported outbound protocols: VLESS, VMess, Trojan, and Hysteria/Hysteria2.

Recognized stream settings include TCP, WebSocket, gRPC, XHTTP, HTTPUpgrade, TLS, and REALITY fields. Routing, inbound, logging, API, observatory, and unrelated outbounds are not imported as profiles.

## Xray option string

The current profile model stores advanced Xray fields as a normalized `key=value` string separated by `|`, `;`, or newlines. Typical keys:

```text
protocol=vless | net=ws | host=cdn.example.com | path=/vpn |
security=tls | sni=cdn.example.com | fp=chrome
```

REALITY example:

```text
protocol=vless | net=tcp | security=reality | sni=example.com |
pbk=PUBLIC_KEY | sid=SHORT_ID | spx=/ | fp=chrome |
flow=xtls-rprx-vision
```

A future schema migration may replace this option string with typed protocol-specific entities. Migration must preserve imported configurations and encrypted storage.

## Duplicate identity

Duplicate detection hashes a canonical representation of security-relevant fields:

- connection mode;
- normalized host/port;
- credentials;
- TLS/SNI;
- payload/Xray options;
- proxy settings.

Display name and profile ID are intentionally excluded. The fingerprint is used locally and is not a server identity or cryptographic signature.

## Future protected metadata

Expiration, device binding, creator identity, minimum app version, edit restrictions, and signatures are roadmap fields. Documentation must not imply they are enforced until container parsing, policy, UI, and tests exist.
