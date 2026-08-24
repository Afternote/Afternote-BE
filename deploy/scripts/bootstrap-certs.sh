#!/usr/bin/env bash
# Greenfield TLS bootstrap for ~/deploy
# Certbot (docker/root) often leaves live/ as root-only — always probe with sudo.
# Does not issue or renew Let's Encrypt; see renew-certs.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tls-lib.sh disable=SC1091
source "${ROOT}/tls-lib.sh"

DOMAIN="${TLS_DOMAIN:-afternote.kro.kr}"
DEPLOY_ROOT="${DEPLOY_ROOT:-$HOME/deploy}"
CONF_DIR="${DEPLOY_ROOT}/data/certbot/conf"
WWW_DIR="${DEPLOY_ROOT}/data/certbot/www"
LIVE_DIR="${CONF_DIR}/live/${DOMAIN}"
LIVE_PEM="${LIVE_DIR}/fullchain.pem"

mkdir -p "${CONF_DIR}" "${WWW_DIR}"

have_tls() {
  sudo test -f "${LIVE_PEM}" && sudo test -f "${LIVE_DIR}/privkey.pem"
}

if have_tls; then
  echo "[INFO] TLS material already present for ${DOMAIN}"
  if tls_is_lets_encrypt "${LIVE_PEM}"; then
    days=$(tls_days_left "${LIVE_PEM}")
    echo "[INFO] Let's Encrypt remaining_days=${days}"
  else
    echo "[INFO] on-disk cert is not Let's Encrypt (temporary self-signed or other)"
  fi
  sudo chown -R "$(id -u)":"$(id -g)" "${CONF_DIR}" "${WWW_DIR}" 2>/dev/null || true
  exit 0
fi

echo "[INFO] No TLS cert for ${DOMAIN}; creating temporary self-signed cert"
tls_write_self_signed "${LIVE_DIR}" "${DOMAIN}"
sudo chown -R "$(id -u)":"$(id -g)" "${CONF_DIR}" "${WWW_DIR}" 2>/dev/null || true
