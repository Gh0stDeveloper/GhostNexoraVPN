#!/usr/bin/env sh
set -eu

TAG="${XRAY_LIB_TAG:-v26.5.19}"
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
DEST_DIR="$ROOT_DIR/app/libs"
DEST_FILE="$DEST_DIR/libv2ray.aar"
URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/$TAG/libv2ray.aar"

mkdir -p "$DEST_DIR"

echo "Descargando AndroidLibXrayLite $TAG..."
if command -v curl >/dev/null 2>&1; then
  curl -fL --retry 3 --connect-timeout 20 "$URL" -o "$DEST_FILE"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$DEST_FILE" "$URL"
else
  echo "Error: instala curl o wget para descargar libv2ray.aar" >&2
  exit 1
fi

if [ ! -s "$DEST_FILE" ]; then
  echo "Error: libv2ray.aar no se descargó correctamente" >&2
  exit 1
fi

printf 'Core disponible en %s\n' "$DEST_FILE"
