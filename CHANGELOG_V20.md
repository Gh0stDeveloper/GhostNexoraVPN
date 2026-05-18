# Ghost Nexora VPN — V20

## Cambios principales
- Corrección del selector de límite de logs en Ajustes.
- Persistencia real del ajuste `logsMaxEntries` en DataStore.
- Pantalla de historial funcional para sustituir el placeholder.
- Mejoras en la comprobación de actualizaciones:
  - comparación por `versionCode` cuando está disponible,
  - compatibilidad con `tag` semántico,
  - soporte para checksum SHA-256 opcional,
  - descarga de texto auxiliar (`.sha256`),
  - ventana de enfriamiento para evitar consultas repetidas.
- Exportación JSON con versión dinámica (`BuildConfig.VERSION_NAME`) y fecha en UTC.
- Ajuste de versiones visibles en About y Ajustes.
- Corrección del nombre de `Application` en el manifiesto.

## Estado
La base queda alineada con la documentación técnica y lista para seguir con la siguiente iteración.
