#!/usr/bin/env bash
# Reproducible remaining-day thresholds without talking to Let's Encrypt.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tls-lib.sh disable=SC1091
source "${ROOT}/tls-lib.sh"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

make_cert() {
  local days=$1
  local out=$2
  openssl req -x509 -nodes -newkey rsa:2048 -days "${days}" \
    -keyout "${out}.key" \
    -out "${out}.pem" \
    -subj "/CN=tls-lib-test" 2>/dev/null
}

tmpdir=$(mktemp -d)
trap 'rm -rf "${tmpdir}"' EXIT

make_cert 40 "${tmpdir}/long"
make_cert 25 "${tmpdir}/renew"
make_cert 10 "${tmpdir}/alert"

long_days=$(tls_days_left "${tmpdir}/long.pem")
renew_days=$(tls_days_left "${tmpdir}/renew.pem")
alert_days=$(tls_days_left "${tmpdir}/alert.pem")

echo "[INFO] fixture remaining days: long=${long_days} renew=${renew_days} alert=${alert_days}"

[ "${long_days}" -ge 38 ] && [ "${long_days}" -le 40 ] || fail "40-day cert remaining=${long_days}"
[ "${renew_days}" -ge 23 ] && [ "${renew_days}" -le 25 ] || fail "25-day cert remaining=${renew_days}"
[ "${alert_days}" -ge 8 ] && [ "${alert_days}" -le 10 ] || fail "10-day cert remaining=${alert_days}"

tls_needs_renew "${long_days}" && fail "40-day cert should not renew"
tls_needs_alert "${long_days}" && fail "40-day cert should not alert"

tls_needs_renew "${renew_days}" || fail "25-day cert should renew"
tls_needs_alert "${renew_days}" && fail "25-day cert should not alert"

tls_needs_renew "${alert_days}" || fail "10-day cert should renew"
tls_needs_alert "${alert_days}" || fail "10-day cert should alert"

tls_is_lets_encrypt "${tmpdir}/long.pem" && fail "self-signed must not look like Let's Encrypt"

echo "[OK] tls-lib remaining-day thresholds"
