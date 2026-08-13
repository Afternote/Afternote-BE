#!/usr/bin/env bash
# OpenAPI contract test (Schemathesis) — JWT 로그인 후 GET 검증.
# Usage:
#   BASE_URL=https://afternote.kro.kr \
#   CONTRACT_TEST_EMAIL=... \
#   CONTRACT_TEST_PASSWORD=... \
#   ./scripts/contract-test.sh
set -euo pipefail

BASE_URL="${BASE_URL:-}"
CONTRACT_TEST_EMAIL="${CONTRACT_TEST_EMAIL:-}"
CONTRACT_TEST_PASSWORD="${CONTRACT_TEST_PASSWORD:-}"

if [ -z "$BASE_URL" ]; then
  echo "[ERROR] BASE_URL is required"
  exit 1
fi
if [ -z "$CONTRACT_TEST_EMAIL" ] || [ -z "$CONTRACT_TEST_PASSWORD" ]; then
  echo "[ERROR] CONTRACT_TEST_EMAIL and CONTRACT_TEST_PASSWORD are required"
  exit 1
fi
BASE_URL="${BASE_URL%/}"

# prod 안전: GET만, 5xx만 실패 처리.
# receiver-auth: 특수 헤더(X-Auth-Code) + 잘못된 path id 500
# admin: 관리자 전용
# mind-record: Gemini 외부 호출/쿼터로 timeout·불안정
EXCLUDE_PATH_REGEX="${CONTRACT_EXCLUDE_PATH_REGEX:-/api/v1/admin|/api/v1/receiver-auth|/api/v1/mind-record}"
MAX_EXAMPLES="${CONTRACT_MAX_EXAMPLES:-5}"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VENV_DIR="${ROOT_DIR}/.contract-venv"

echo "Contract test target: ${BASE_URL}"
echo "Exclude path regex: ${EXCLUDE_PATH_REGEX}"

python3 -m venv "${VENV_DIR}"
# shellcheck disable=SC1091
source "${VENV_DIR}/bin/activate"
python -m pip install --upgrade pip
python -m pip install 'schemathesis>=3.39,<4'

echo "Logging in as ${CONTRACT_TEST_EMAIL} ..."
LOGIN_HTTP="$(
  curl -sS -o /tmp/contract-login.json -w '%{http_code}' -X POST "${BASE_URL}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"${CONTRACT_TEST_EMAIL}\",\"password\":\"${CONTRACT_TEST_PASSWORD}\"}" \
    || true
)"
LOGIN_JSON="$(cat /tmp/contract-login.json 2>/dev/null || true)"
if [ "${LOGIN_HTTP}" != "200" ]; then
  echo "[ERROR] Contract login failed (HTTP ${LOGIN_HTTP})." >&2
  echo "[ERROR] POST ${BASE_URL}/api/v1/auth/login" >&2
  echo "[ERROR] Response body: ${LOGIN_JSON}" >&2
  echo "[ERROR] 그린필드/빈 DB면 CONTRACT_TEST 계정이 없을 수 있습니다. 동일 이메일로 회원가입 후 Secret 비밀번호를 맞추세요." >&2
  exit 22
fi

ACCESS_TOKEN="$(
  LOGIN_JSON="${LOGIN_JSON}" python - <<'PY'
import json, os, sys
payload = json.loads(os.environ["LOGIN_JSON"])
token = (payload.get("data") or {}).get("accessToken")
if not token:
    print("login response missing data.accessToken:", payload, file=sys.stderr)
    sys.exit(1)
print(token)
PY
)"

echo "Login OK (JWT acquired)"

schemathesis run "${BASE_URL}/v3/api-docs" \
  --base-url "${BASE_URL}" \
  --include-method GET \
  --checks not_a_server_error \
  --hypothesis-max-examples "${MAX_EXAMPLES}" \
  --request-timeout 15000 \
  --exclude-path-regex "${EXCLUDE_PATH_REGEX}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"

echo "Contract test OK"
