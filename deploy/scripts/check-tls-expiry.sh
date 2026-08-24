#!/usr/bin/env bash
# Fail if the presented (or on-disk) certificate is within the alert window.
# Usage:
#   ./check-tls-expiry.sh --from-host afternote.kro.kr
#   ./check-tls-expiry.sh --from-disk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tls-lib.sh disable=SC1091
source "${ROOT}/tls-lib.sh"

DEPLOY_ROOT="${DEPLOY_ROOT:-$HOME/deploy}"
DOMAIN="${TLS_DOMAIN:-afternote.kro.kr}"
MODE=""
HOST=""

usage() {
  echo "Usage: $0 --from-host <hostname> | --from-disk" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --from-host)
      MODE=host
      HOST="${2:-}"
      [ -n "${HOST}" ] || usage
      shift 2
      ;;
    --from-disk)
      MODE=disk
      shift
      ;;
    *)
      usage
      ;;
  esac
done

[ -n "${MODE}" ] || usage

pem=""
cleanup() {
  if [ -n "${pem}" ] && [ -f "${pem}" ]; then
    rm -f "${pem}"
  fi
}

if [ "${MODE}" = "host" ]; then
  pem=$(mktemp)
  trap cleanup EXIT
  echo "[INFO] fetching leaf certificate from ${HOST}:443"
  # s_client often exits 1 after a successful handshake when stdin closes.
  set +o pipefail
  echo | openssl s_client -connect "${HOST}:443" -servername "${HOST}" 2>/dev/null \
    | awk 'BEGIN {p=0} /BEGIN CERTIFICATE/{p=1} p{print} /END CERTIFICATE/{exit}' \
    > "${pem}"
  set -o pipefail
  if ! openssl x509 -in "${pem}" -noout >/dev/null 2>&1; then
    echo "[ERROR] TLS handshake to ${HOST}:443 did not return an X.509 certificate"
    exit 1
  fi
else
  pem="${DEPLOY_ROOT}/data/certbot/conf/live/${DOMAIN}/fullchain.pem"
  if [ ! -f "${pem}" ] && ! sudo test -f "${pem}"; then
    echo "[ERROR] missing ${pem}"
    exit 1
  fi
fi

issuer=$(tls_x509 "${pem}" -noout -issuer)
enddate=$(tls_x509 "${pem}" -noout -enddate)
days=$(tls_days_left "${pem}")
echo "[INFO] ${enddate}"
echo "[INFO] ${issuer}"
echo "[INFO] remaining_days=${days} alert_at<=${TLS_ALERT_BEFORE_DAYS}"

if ! grep -qi "Let's Encrypt" <<<"${issuer}"; then
  echo "[ERROR] presented certificate is not Let's Encrypt"
  exit 1
fi

if tls_needs_alert "${days}"; then
  echo "[ERROR] certificate is expired or within ${TLS_ALERT_BEFORE_DAYS} days of expiry"
  exit 1
fi

echo "[OK] TLS remaining_days=${days}"
