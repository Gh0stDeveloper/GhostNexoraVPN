# DNSTT + Xray

Importa un JSON de Ghost Nexora con un backend Xray TCP. El servidor DNSTT recibe
el tráfico del resolver y entrega el flujo TCP al backend que se configuró en el
servidor. El destino del outbound importado conserva su función como identidad
del backend TLS aunque la conexión física se haga a un puerto local.

```json
{
  "type": "gnx-dnstt",
  "version": 1,
  "name": "Mi túnel DNS",
  "transport": "udp",
  "resolver": "192.0.2.53:53",
  "domain": "t.example.net",
  "publicKey": "<clave pública DNSTT en 64 dígitos hexadecimales>",
  "outbound": {
    "protocol": "vless",
    "settings": {
      "vnext": [{
        "address": "vpn.example.net",
        "port": 443,
        "users": [{"id": "<UUID del backend>", "encryption": "none"}]
      }]
    },
    "streamSettings": {
      "network": "raw",
      "security": "tls",
      "tlsSettings": {"serverName": "vpn.example.net"}
    }
  }
}
```

El campo `resolver` admite `host:puerto` para `udp` y `dot`, o una URL
`https://` para `doh`. El modo protege la resolución DNS de las aplicaciones
cuando el DNS de la VPN está habilitado. El diagnóstico de resolver UDP comprueba
si devuelve una respuesta DNS y la prueba de salida comprueba el túnel real.
El modo bloquea UDP genérico y conserva el manejo de las consultas DNS.

El backend puede ser VLESS, VMess, Trojan, Shadowsocks, proxy HTTPS o SOCKS5
sobre TLS, de acuerdo con los perfiles TCP admitidos por el importador.
