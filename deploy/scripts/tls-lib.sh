# Shared TLS helpers for EC2 deploy scripts. Source this file; do not execute it.
# shellcheck shell=bash

TLS_RENEW_BEFORE_DAYS="${TLS_RENEW_BEFORE_DAYS:-30}"
TLS_ALERT_BEFORE_DAYS="${TLS_ALERT_BEFORE_DAYS:-21}"

tls_x509() {
  local pem=$1
  shift
  if [ -r "${pem}" ]; then
    openssl x509 -in "${pem}" "$@"
  else
    sudo openssl x509 -in "${pem}" "$@"
  fi
}

tls_is_lets_encrypt() {
  local pem=$1
  local issuer
  issuer=$(tls_x509 "${pem}" -noout -issuer 2>/dev/null) || return 1
  grep -qi "Let's Encrypt" <<<"${issuer}"
}

# OpenSSL notAfter looks like: Nov 10 13:35:16 2026 GMT
tls_not_after_epoch() {
  local pem=$1
  local datestr
  datestr=$(tls_x509 "${pem}" -noout -enddate) || return 1
  datestr=${datestr#notAfter=}
  python3 -c '
import datetime as dt
import sys
raw = " ".join(sys.argv[1].replace("GMT", "").split())
when = dt.datetime.strptime(raw, "%b %d %H:%M:%S %Y").replace(tzinfo=dt.timezone.utc)
print(int(when.timestamp()))
' "${datestr}"
}

tls_days_left() {
  local pem=$1
  local end_epoch now
  end_epoch=$(tls_not_after_epoch "${pem}") || return 1
  now=$(date +%s)
  echo $(( (end_epoch - now) / 86400 ))
}

tls_needs_renew() {
  local days=$1
  [ "${days}" -le "${TLS_RENEW_BEFORE_DAYS}" ]
}

tls_needs_alert() {
  local days=$1
  [ "${days}" -le "${TLS_ALERT_BEFORE_DAYS}" ]
}

tls_write_self_signed() {
  local live_dir=$1
  local domain=$2
  sudo mkdir -p "${live_dir}"
  sudo openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout "${live_dir}/privkey.pem" \
    -out "${live_dir}/fullchain.pem" \
    -subj "/CN=${domain}"
  sudo cp "${live_dir}/fullchain.pem" "${live_dir}/cert.pem"
  sudo cp "${live_dir}/fullchain.pem" "${live_dir}/chain.pem"
}
