# Ghost Nexora VPN 1.0.42

## Correcciones

- Corrige el `ExceptionInInitializerError` al abrir, previsualizar o editar notas HTML en Android. La causa era una expresión regular CSS aceptada por la JVM de escritorio pero rechazada por el motor ICU de Android.
- El saneador de notas ya no puede cerrar la aplicación durante su inicialización: todas las expresiones estáticas se compilan con fallback seguro y el contenido tiene una salida de texto escapado ante cualquier error inesperado.
- Amplía la compatibilidad de notas con CSS Grid, Flexbox, gradientes, transformaciones, transiciones, animaciones locales, `@media`, `@supports`, `@keyframes`, etiquetas de presentación heredadas, tablas, `<font>`, `<marquee>` e imágenes `data:` embebidas.
- Mantiene bloqueados scripts, manejadores `on*`, iframes, objetos, formularios, JavaScript, CSS remoto, fuentes remotas y cualquier carga de red desde la WebView.
- Mejora el diagnóstico de `ECONNREFUSED`: diferencia un rechazo real del servidor de un fallo de bypass, TLS o SNI. El SNI se conserva exclusivamente como identidad TLS y nunca se usa como sustituto silencioso del servidor SSH.

## Política de versiones

A partir de esta versión, todo pull request debe incrementar tanto `versionCode` como `versionName`. El workflow `Version Policy` rechaza cualquier actualización que reutilice la versión anterior.

## Validación requerida

- Abrir notas simples y notas con estilos extensos en una compilación Release/R8.
- Importar y previsualizar notas GNX3 bloqueadas y editables.
- Probar enlaces `https`, `http`, `mailto` y `tel`.
- Confirmar que recursos remotos dentro de HTML/CSS no se cargan.
- Repetir el perfil SSH + SSL cuando el puerto remoto esté activo. Un `ECONNREFUSED` recibido desde la IP del servidor significa que el host respondió pero no hay un servicio escuchando en ese puerto en ese momento.
