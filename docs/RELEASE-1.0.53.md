# Ghost Nexora VPN 1.0.53

Esta versión corrige el estado `Conectado` falso que podía aparecer cuando
Android mostraba el icono VPN y Xray estaba iniciado, pero el túnel no movía
datos reales.

## Aceptación real de la conexión

- La aplicación permanece en `Conectando` hasta recibir una respuesta por la red VPN exacta creada por Android.
- La comprobación atraviesa TUN → Xray → outbound y, en perfiles SSH, el bridge SOCKS y la misma sesión SSH autenticada.
- Se realiza una sola petición HTTPS `HEAD` a `one.one.one.one`; no existe la secuencia `1/2` ni un segundo destino de respaldo.
- No se crea una segunda VPN, una segunda autenticación SSH ni un canal shell.
- Si la ruta no responde en el tiempo límite, se muestra `[ROUTE-DATA-204]`, se cierra el TUN inicial y no se publica `Conectado`.
- Las reconexiones deben superar la misma comprobación antes de volver al estado `Conectado`.

## Supervisión y privacidad

- Después de aceptar la conexión, la supervisión es pasiva y no abre sondas periódicas.
- La solicitud técnica no contiene credenciales, host del perfil, payload ni tráfico del usuario.
- El log muestra primero la validación, luego la evidencia de subida/bajada y finalmente el estado `Conectado`.

## Versión

- `versionName`: `1.0.53`
- `versionCode`: `53`
