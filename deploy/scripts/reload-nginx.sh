#!/usr/bin/env bash
# Reload nginx in ~/deploy after certificate files change.
set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-$HOME/deploy}"
cd "${DEPLOY_ROOT}"

echo "[INFO] nginx -t"
docker compose exec -T nginx nginx -t
echo "[INFO] nginx -s reload"
docker compose exec -T nginx nginx -s reload
echo "[INFO] nginx reloaded"
