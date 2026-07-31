# Ghost Nexora VPN 1.0.46

## Corrección SSH + SSL

La versión 1.0.45 y anteriores podían usar el dominio SNI como destino TCP
físico cuando estaba activa la compatibilidad tipo HTTP Injector/HTTP Custom.
Eso permitía completar TLS con el dominio frontal, pero el servidor cerraba la
conexión al recibir el intercambio SSH.

La ruta correcta queda separada de esta forma:

- **Extremo TCP real:** host y puerto del servidor SSH del perfil.
- **SNI TLS:** dominio enviado únicamente dentro de ClientHello.
- **Identidad SSH:** host real del servidor SSH.

La misma separación se aplica a conexión directa, proxy HTTP CONNECT y proxy
SOCKS5.

## Diagnóstico

El error `connection is closed by foreign host` después de un TLS exitoso ahora
se clasifica como `SSH-TLS-502`, con una explicación específica sobre Host,
puerto y SNI, en lugar del código genérico `VPN-000`.

## Seguridad

- Se conservan los TrustManager de Android.
- El modo estricto valida el certificado contra el SNI.
- El modo compatible permite diferencia SNI/SAN cuando el creador lo requiere.
- La identidad y huella del servidor SSH continúan verificándose por separado.

## Versión

- `versionName`: `1.0.46`
- `versionCode`: `46`
