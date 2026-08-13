# Ghost Nexora VPN 1.0.49

## Corrección de `Trust anchor` en SSH + SSL

El modo **Compatible con HTTP Injector/Custom** ahora admite certificados TLS
privados, autofirmados o con una cadena incompleta. Esto corrige el error:

```text
CertPathValidatorException: Trust anchor for certification path not found
```

La separación del transporte se mantiene:

- **Extremo TCP:** host/IP y puerto SSH configurados.
- **SNI TLS:** nombre independiente enviado en `ClientHello`.
- **Identidad final:** host y huella del servidor SSH.

El SNI no tiene que ser el dominio del VPS, pertenecer al certificado del host
SSH ni coincidir con los SAN del certificado presentado.

## Alcance y seguridad

- La compatibilidad debe activarse explícitamente en el perfil SSH sobre TLS.
- El modo estricto continúa usando la confianza y verificación de hostname de
  Android sin ningún fallback permisivo.
- El modo compatible conserva el cifrado TLS y exige un certificado X.509 hoja
  presente y vigente, pero no autentica su CA ni su relación con el SNI.
- La identidad final se comprueba en la capa SSH y los cambios posteriores de
  huella se rechazan.
- V2Ray, Trojan, Hysteria2, actualizaciones y llamadas API no usan esta política.

## Versión

- `versionName`: `1.0.49`
- `versionCode`: `49`
