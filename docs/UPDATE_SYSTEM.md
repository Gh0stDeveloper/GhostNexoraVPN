# GhostNexoraVPN — Sistema de Detección de Actualizaciones

## Objetivo
El sistema de actualización consulta la última release publicada en GitHub, compara la versión remota con `BuildConfig.VERSION_CODE` y, si corresponde, muestra un diálogo para descargar e instalar la nueva APK.

## Flujo operativo
1. La app se abre y el `UpdateViewModel` ejecuta una comprobación automática.
2. `UpdateChecker` consulta la última release publicada en GitHub Releases.
3. El sistema lee:
   - `tag_name`
   - `name`
   - `body`
   - `published_at`
   - `assets`
4. Se resuelve el `versionCode` remoto a partir del tag, del nombre o del cuerpo de la release.
5. Si el `versionCode` remoto es mayor que el local, la app muestra el diálogo de actualización.
6. La descarga del APK se realiza en segundo plano.
7. Antes de instalar, se valida el checksum SHA-256 si está disponible.
8. La APK se instala encima de la versión actual con la misma firma y el mismo `applicationId`.

## Estructura de archivos
- `update/data/GitHubRelease.kt`
- `update/data/UpdateRepository.kt`
- `update/domain/UpdateChecker.kt`
- `update/util/ApkInstaller.kt`
- `update/UpdateViewModel.kt`
- `ui/components/UpdateDialog.kt`

## Reglas clave
- La app usa `versionCode` como criterio principal de actualización.
- `versionName` se usa solo como referencia visual.
- La actualización debe publicarse como **GitHub Release**, no como artifact temporal.
- El APK debe estar firmado con el mismo keystore para conservar datos y permitir instalación encima.

## Recomendación para el workflow
El workflow de GitHub Actions debe:
- compilar el release APK,
- crear la release,
- adjuntar el APK como asset,
- generar un tag basado en el build/version code.
