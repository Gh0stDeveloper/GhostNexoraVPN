# Ghost Nexora VPN — V20 Fixed / Enhanced

## Cambios principales
- Importación ampliada para aceptar:
  - JSON exportado por la app,
  - enlaces `vmess://`, `vless://` y `trojan://` desde archivo o portapapeles.
- Nuevo parser de protocolos para convertir enlaces de la comunidad a perfiles internos almacenables.
- Pantalla de importación con acceso directo al portapapeles.
- Historial real por perfil con filtro visual y métricas básicas de eventos.
- Retención automática de logs en función del límite configurado en Ajustes.
- Trimming inmediato de logs al cambiar el límite máximo.
- Preparación automática de `geoip.dat` y `geosite.dat` desde assets cuando estén disponibles.
- Corrección y endurecimiento previo de la base de actualización online:
  - `versionCode` y `tag` semántico,
  - checksum SHA-256 opcional,
  - descarga de texto auxiliar,
  - ventana de enfriamiento para evitar comprobaciones repetidas.
- Exportación JSON con versión dinámica (`BuildConfig.VERSION_NAME`) y fecha en UTC.
- Ajustes de versiones visibles y corrección del `Application` principal.

## Estado
La base queda más alineada con la documentación técnica y con mejor soporte para importación, observabilidad y mantenimiento de logs.
