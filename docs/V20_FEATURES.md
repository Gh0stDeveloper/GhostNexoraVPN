# Ghost Nexora VPN - V20 fixed+ / enhancement notes

## 1. Consola de logs tipo terminal

La pantalla principal ahora muestra los eventos con un formato más detallado.

- timestamp completo por línea,
- mensajes ordenados cronológicamente,
- auto-scroll al último evento,
- copia completa del registro con un solo toque.

El formato visible es:

`[yyyy-MM-dd HH:mm:ss] mensaje`

La capa visual está pensada para depuración rápida y para ubicar exactamente en qué etapa se corta el túnel:

- inicio del servicio,
- información del dispositivo,
- IP local,
- estado de red,
- handshake,
- autenticación,
- conexión,
- desconexión,
- error.

## 2. Asistente de primer arranque

La app ahora presenta una pantalla inicial para pedir permisos antes de entrar al dashboard.

Permisos guiados:

- VPN: crea la interfaz TUN.
- Overlay: necesario para la ventana flotante.
- Notificaciones: necesario para el servicio persistente.
- Batería: evita cierres agresivos.
- Instalación desde fuentes desconocidas: necesaria para aplicar actualizaciones descargadas.
- Almacenamiento: solo en dispositivos antiguos o cuando el sistema lo requiera.

Observación técnica:

- Android 13+ usa el selector de archivos y no necesita acceso clásico al almacenamiento para importar/exportar.
- El permiso de instalar APKs no puede concederse automáticamente; la app solo puede llevar al panel de ajustes correspondiente.
- El permiso VPN también requiere intervención del sistema.

## 3. Buscador manual de actualizaciones

La aplicación verifica actualizaciones automáticamente al abrirse y ahora también dispone de una verificación manual desde la barra superior.

Comportamiento:

- compara `versionCode` y `tag` de la release remota,
- acepta APK adjunto,
- puede leer checksum SHA-256 si existe,
- descarga en caché antes de abrir el instalador,
- conserva datos de usuario y configuraciones cuando el APK se instala sobre la misma firma y package name.

## 4. Generador de payloads

La pantalla de edición de perfiles incorpora un generador de payloads base para casos comunes.

Presets incluidos:

- Navegación,
- Streaming,
- Gaming,
- Custom.

Cada preset produce una plantilla editable basada en:

- host,
- puerto,
- SNI,
- encabezados HTTP de ejemplo.

La función no reemplaza el editor manual; solo acelera la creación inicial.

## 5. Preservación de datos en actualizaciones

La ruta de actualización sigue el flujo estándar de Android:

1. descargar el APK nuevo,
2. verificar checksum opcional,
3. abrir el instalador del sistema,
4. instalar sobre la app existente.

Los datos se mantienen si:

- el `applicationId` no cambia,
- la firma del APK coincide,
- el instalador completa la actualización sin desinstalación.

## 6. Criterio operativo recomendado

Orden sugerido para probar la V20 fixed+:

1. abrir la app por primera vez,
2. conceder permisos,
3. crear un perfil,
4. generar un payload base,
5. conectar,
6. revisar logs tipo terminal,
7. probar búsqueda manual de actualización,
8. exportar perfiles como respaldo.
