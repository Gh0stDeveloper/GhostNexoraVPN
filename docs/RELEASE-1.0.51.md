# Ghost Nexora VPN 1.0.51

Esta versión corrige el estado conectado falso y elimina conexiones de prueba automáticas del flujo VPN normal.

## Cambios principales

- La aplicación solo publica `Conectado` cuando el descriptor TUN sigue válido, Xray y el transporte siguen activos, y Android registra una red `TRANSPORT_VPN` perteneciente a la aplicación.
- Si Android no registra la VPN, el inicio falla con un error explícito en vez de mostrar `Conectado` sin el indicador VPN del sistema.
- Se retiraron la prueba automática `1/2`, los reintentos remotos periódicos, el inbound local `health-check` y los sockets TCP usados para medir latencia cada diez segundos.
- El monitor de salud ahora es pasivo: revisa únicamente el runtime existente y el registro VPN de Android.
- El banner de autenticación SSH se muestra en el registro. Además, se intenta leer el MOTD mediante un canal de shell breve dentro de la misma sesión SSH autenticada; no se crea otra conexión TCP, TLS ni SSH.
- `VpnService.Builder` recibe el intent de configuración y la red física subyacente antes de establecer la interfaz.

## Comportamiento esperado

Durante una conexión SSH + SSL debe existir una única conexión física al servidor configurado. Los canales `direct-tcpip` aparecen únicamente cuando una aplicación del dispositivo genera tráfico real. La app Android debe mostrar el indicador VPN del sistema antes de que Ghost Nexora publique `Conectado`.

## Versión

- `versionName`: `1.0.51`
- `versionCode`: `51`
