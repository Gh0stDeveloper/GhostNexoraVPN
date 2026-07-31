# Ghost Nexora VPN 1.0.45

## Exportación individual

- La pantalla de exportación separa claramente los permisos del perfil y la
  protección del archivo.
- El creador puede elegir entre configuración editable y configuración
  bloqueada.
- El creador puede elegir entre cifrado automático administrado por la
  aplicación y contraseña personalizada.
- Las configuraciones bloqueadas ocultan host, credenciales SSH, método, SNI,
  proxy y payload, y no permiten edición, duplicación ni reexportación.
- La nota del creador admite texto o HTML/CSS saneado y conserva una vista
  previa antes de exportar.

## Importación

- Los archivos protegidos con contraseña siguen solicitando la contraseña antes
  de mostrar o guardar el perfil.
- Los archivos con protección automática se abren únicamente con el material de
  compatibilidad de la aplicación oficial.
- El primer perfil nuevo importado pasa a ser el perfil activo, de modo que su
  nota aparece al volver a Inicio.

## Inicio y notas

- La nota completa del perfil activo se muestra debajo de la tarjeta del perfil.
- Las notas de perfiles bloqueados siguen siendo visibles sin revelar los
  parámetros del túnel.
- La visualización permanece sin JavaScript, almacenamiento, archivos ni cargas
  de red.

## Interfaz

- Las pestañas Inicio y Log son más compactas y quedan más cerca de la parte
  superior.
- El botón de conexión y el espaciado vertical se redujeron para aprovechar
  mejor la pantalla.
- El Log utiliza la superficie completa, sin tarjeta exterior ni recuadros por
  línea, manteniendo filtros, seguimiento automático y copia saneada.

## Seguridad

GNX3 conserva AES-256-GCM autenticado, claves de datos aleatorias, envoltura de
clave separada, HMAC-SHA256 y PBKDF2-HMAC-SHA256 para contraseñas. Cada
exportación genera salts y nonces/IV nuevos; no existe un IV fijo reutilizado.

## Versión

- `versionName`: `1.0.45`
- `versionCode`: `45`
