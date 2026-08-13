# Ghost Nexora VPN 1.0.52

Esta versión rediseña la pantalla principal y los registros, y limita el
mensaje del servidor al banner SSH correcto.

## Mensaje del servidor

- Solo se muestra el banner opcional que el servidor SSH entrega durante la autenticación.
- Se acepta como máximo un banner por conexión.
- Si el servidor no tiene banner, no se muestra ninguna entrada vacía.
- Ya no se abre un canal shell para leer el MOTD de Ubuntu; la versión, carga, memoria, actualizaciones y demás información administrativa de la VPS no aparecen en la aplicación.
- Las etiquetas HTML compatibles con Injector y los colores ANSI comunes se convierten en texto enriquecido seguro.
- JavaScript, eventos, formularios, frames, contenido activo, imágenes remotas y cargas de red permanecen bloqueados.

## Diseño y accesibilidad

- Nueva cabecera de conexión con estado, perfil, acción principal e indicación contextual.
- Métricas de sesión agrupadas y más legibles.
- Navegación Inicio/Registro compacta y coherente con el tema visual.
- Una sola consola compartida por Inicio y Registros.
- Texto normal blanco; conexión correcta verde; advertencias amarillas; errores rojos.
- Cada nivel conserva también una etiqueta textual para no depender únicamente del color.
- La vista `Resumen` oculta por defecto la negociación interna extensa de JSch; `Todos` conserva el diagnóstico completo.
- El registro completo incorpora búsqueda, copia, exportación, borrado con confirmación y seguimiento automático.

## Versión

- `versionName`: `1.0.52`
- `versionCode`: `52`
