# Ghost Nexora VPN 1.0.50

## Corrección del inicio nativo Xray

El transporte `SSH + SSL/SNI` ya completaba correctamente TLS, la verificación
de la huella SSH, la autenticación por contraseña y la apertura del bridge
SOCKS. El bloqueo restante ocurría después de que AndroidLibXrayLite notificaba:

```text
[CORE] Señal nativa de inicio recibida
[CORE] Evento nativo 0 · Started successfully, running
```

La biblioteca ejecuta esas notificaciones antes de devolver `startLoop()`. La
versión Java del servicio persistía cada mensaje con Room/DataStore de forma
síncrona dentro del callback nativo. Si esa escritura esperaba E/S, el callback
no regresaba, `startLoop()` tampoco terminaba y el servicio no alcanzaba el
estado `Conectado` aunque Xray ya estuviera funcionando.

## Comportamiento nuevo

- Los eventos de core, TUN, SSH y SOCKS se encolan inmediatamente.
- Un escritor FIFO dedicado los persiste fuera del hilo de inicio y del callback
  de AndroidLibXrayLite.
- Una vez que `startLoop()` devuelve y `isRunning` es verdadero, el servicio
  continúa hasta publicar `Conectado` y programa la comprobación real de salida
  a Internet en segundo plano.
- La desconexión no depende de que Room o DataStore terminen de escribir un
  evento diagnóstico.

Esta corrección no modifica el transporte físico ni la política SNI de 1.0.49:
el host/IP SSH sigue siendo el extremo TCP, y el SNI TLS puede ser un nombre
independiente con un certificado diferente en el modo compatible.

## Versión

- `versionName`: `1.0.50`
- `versionCode`: `50`
