#!/bin/sh
# Writes /config.js from the environment at container start (nginx serves it from /tmp, which is
# writable with a read-only root file system), so one image serves staging and production.
set -eu

js_string() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/</\\u003c/g'
}

cat > /tmp/dukkan-config.js <<CONFIG
window.DUKKAN_CONFIG = {
  apiBaseUrl: "$(js_string "${DUKKAN_API_BASE_URL:-}")",
  mapTileUrl: "$(js_string "${DUKKAN_MAP_TILE_URL:-}")",
  mapAttribution: "$(js_string "${DUKKAN_MAP_ATTRIBUTION:-}")"
};
CONFIG
