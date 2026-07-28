# Payload Engine

## Purpose

The payload engine generates a bounded sequence of socket writes for SSH injection-style modes. It is not an arbitrary scripting engine. Only documented variables and control directives are accepted.

## Templates

- `CONNECT`
- `GET`
- `POST`
- `HEAD`
- `WebSocket Upgrade`

Templates are starting points. The editor displays exact CRLF markers before a profile is saved.

## Variables

| Variable | Expansion |
|---|---|
| `[host]` | target SSH host |
| `[port]` | target SSH port |
| `[host_port]` | `host:port` |
| `[sni]` | configured TLS SNI, falling back to host |
| `[proxy]` | configured proxy host |
| `[proxy_port]` | configured proxy port |
| `[crlf]` | carriage return + line feed |
| `[lf]` | line feed |
| `[cr]` | carriage return |
| `[random]` | random 16-character alphanumeric token |
| `[rotate]` | one value selected from SNI, host, and proxy host |

## Controls

### `[split]`

Finishes the current segment and begins a new socket write. It does not add bytes by itself.

### `[delay=N]`

Adds a delay between writes. Current safety limits:

- maximum payload size: 32 KiB;
- maximum actions: 64;
- maximum individual delay: 5,000 ms;
- maximum total delay: 15,000 ms.

A profile exceeding a limit is rejected before connection.

## Execution

1. Validate syntax and limits.
2. Expand variables for the current profile.
3. Generate random/rotated values once for the compiled plan.
4. Convert text and controls into `Send` and `Delay` actions.
5. Write and flush each segment in order.
6. Read the remote response or SSH banner.
7. Continue only when the response is acceptable.

## Examples

### CONNECT

```text
CONNECT [host_port] HTTP/1.1[crlf]
Host: [host_port][crlf]
Proxy-Connection: Keep-Alive[crlf]
Connection: Keep-Alive[crlf]
[crlf]
```

### Split request

```text
CONNECT [host_port] HTTP/1.1[crlf]
Host: [sni][crlf]
[split][delay=250]
Connection: Keep-Alive[crlf][crlf]
```

### WebSocket Upgrade

```text
GET / HTTP/1.1[crlf]
Host: [sni][crlf]
Upgrade: websocket[crlf]
Connection: Upgrade[crlf]
Sec-WebSocket-Version: 13[crlf]
Sec-WebSocket-Key: [random][crlf][crlf]
```

## Logging and privacy

Persistent logs record stage, response class, byte count, segment count, and error code. They do not record the complete payload or expanded secret values.

## Limitations

- No loops, conditionals, file access, shell commands, or unrestricted macros.
- `[rotate]` is local value selection, not domain discovery.
- Delays are synchronous inside the transport worker and intentionally bounded.
- A syntactically valid payload is not proof that a remote server accepts it.

## Testing

Unit tests cover deterministic expansion, splitting, delays, unknown variables, safety limits, templates, and exact CRLF preview. Physical tests must cover 200, 101, 403, 407, partial responses, timeouts, and direct SSH banners.