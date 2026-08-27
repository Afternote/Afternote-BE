#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
BASE_URL="${BASE_URL%/}"
MAX_ATTEMPTS="${HTTP_SMOKE_MAX_ATTEMPTS:-60}"
RETRY_DELAY_SECONDS="${HTTP_SMOKE_RETRY_DELAY_SECONDS:-2}"

case "$MAX_ATTEMPTS" in
  ''|*[!0-9]*)
    echo "[ERROR] HTTP_SMOKE_MAX_ATTEMPTS must be a positive integer" >&2
    exit 2
    ;;
esac
if [ "$MAX_ATTEMPTS" -lt 1 ]; then
  echo "[ERROR] HTTP_SMOKE_MAX_ATTEMPTS must be at least 1" >&2
  exit 2
fi

SMOKE_DIR="$(mktemp -d)"
trap 'rm -rf "$SMOKE_DIR"' EXIT

OPENAPI_JSON="$SMOKE_DIR/openapi.json"
HEALTH_JSON="$SMOKE_DIR/health.json"
ASSET_LINKS_JSON="$SMOKE_DIR/assetlinks.json"
APP_VERSION_JSON="$SMOKE_DIR/app-version.json"

is_ready() {
  curl --fail --silent --connect-timeout 2 --max-time 5 \
    "$BASE_URL/v3/api-docs" >"$OPENAPI_JSON" 2>/dev/null \
    && curl --fail --silent --connect-timeout 2 --max-time 5 \
      "$BASE_URL/actuator/health" >"$HEALTH_JSON" 2>/dev/null \
    && curl --fail --silent --connect-timeout 2 --max-time 5 \
      "$BASE_URL/api/v1/app/version?platform=ANDROID&versionCode=10001" \
      >"$APP_VERSION_JSON" 2>/dev/null \
    && python3 - "$OPENAPI_JSON" "$HEALTH_JSON" "$APP_VERSION_JSON" \
      <<'PY' >/dev/null 2>&1
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    openapi = json.load(source)
with open(sys.argv[2], encoding="utf-8") as source:
    health = json.load(source)
with open(sys.argv[3], encoding="utf-8") as source:
    app_version = json.load(source)

if not str(openapi.get("openapi", "")).startswith("3."):
    raise SystemExit(1)
if health.get("status") != "UP":
    raise SystemExit(1)
if (app_version.get("data") or {}).get("latestVersionCode") != 10001:
    raise SystemExit(1)
PY
}

ready=0
for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
  if is_ready; then
    ready=1
    break
  fi
  echo "Waiting for the application (${attempt}/${MAX_ATTEMPTS}) ..."
  sleep "$RETRY_DELAY_SECONDS"
done

if [ "$ready" -ne 1 ]; then
  echo "[ERROR] Application did not become ready at $BASE_URL" >&2
  exit 1
fi

python3 - "$OPENAPI_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)

paths = payload.get("paths") or {}
required_operations = {
    "/api/v1/time-letters": {"get", "post", "delete"},
    "/api/v1/time-letters/{timeLetterId}": {"get", "patch"},
}
for path, methods in required_operations.items():
    actual = set((paths.get(path) or {}).keys())
    missing = methods - actual
    if missing:
        raise SystemExit(f"OpenAPI missing {path} operations: {sorted(missing)}")

print(f"OpenAPI OK ({len(paths)} paths, Time-Letters contract present)")
PY

python3 - "$HEALTH_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)

components = payload.get("components") or {}
for name in ("db", "redis"):
    status = (components.get(name) or {}).get("status")
    if status != "UP":
        raise SystemExit(f"health component {name} is {status!r}, expected 'UP'")

print("Health OK (MySQL and Redis UP)")
PY

curl --fail --silent --show-error --connect-timeout 2 --max-time 5 \
  "$BASE_URL/.well-known/assetlinks.json" >"$ASSET_LINKS_JSON"
python3 - "$ASSET_LINKS_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)

package_names = {
    (statement.get("target") or {}).get("package_name")
    for statement in payload
    if isinstance(statement, dict)
}
if "com.afternote.app" not in package_names:
    actual = sorted(name for name in package_names if name is not None)
    raise SystemExit(f"assetlinks package missing: {actual}")

print("Asset Links OK")
PY

python3 - "$APP_VERSION_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)

data = payload.get("data") or {}
if payload.get("status") != 200 or payload.get("code") != 200:
    raise SystemExit(f"app version envelope mismatch: {payload}")
if data.get("latestVersionCode") != 10001:
    raise SystemExit(f"unexpected latestVersionCode: {data}")
if data.get("updateRequired") is not False:
    raise SystemExit(f"unexpected updateRequired: {data}")

print("App Version OK")
PY

echo "PR-local HTTP baseline OK"
