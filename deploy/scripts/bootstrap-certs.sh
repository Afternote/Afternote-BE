#!/usr/bin/env bash
# Greenfield TLS bootstrap for ~/deploy
# Certbot (docker/root) often leaves live/ as root-only — always probe with sudo.
set -euo pipefail

DOMAIN="${TLS_DOMAIN:-afternote.kro.kr}"
DEPLOY_ROOT="${DEPLOY_ROOT:-$HOME/deploy}"
CONF_DIR="${DEPLOY_ROOT}/data/certbot/conf"
WWW_DIR="${DEPLOY_ROOT}/data/certbot/www"
LIVE_DIR="${CONF_DIR}/live/${DOMAIN}"

mkdir -p "${CONF_DIR}" "${WWW_DIR}"

have_tls() {
  sudo test -f "${LIVE_DIR}/fullchain.pem" && sudo test -f "${LIVE_DIR}/privkey.pem"
}

if have_tls; then
  echo "[INFO] TLS material already present for ${DOMAIN}"
  # Keep deploy user able to manage renewals / scripts
  sudo chown -R "$(id -u)":"$(id -g)" "${CONF_DIR}" "${WWW_DIR}" 2>/dev/null || true
  exit 0
fi

echo "[INFO] No TLS cert for ${DOMAIN}; creating temporary self-signed cert"
sudo mkdir -p "${LIVE_DIR}"
sudo openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
  -keyout "${LIVE_DIR}/privkey.pem" \
  -out "${LIVE_DIR}/fullchain.pem" \
  -subj "/CN=${DOMAIN}"
sudo cp "${LIVE_DIR}/fullchain.pem" "${LIVE_DIR}/cert.pem"
sudo cp "${LIVE_DIR}/fullchain.pem" "${LIVE_DIR}/chain.pem"
sudo chown -R "$(id -u)":"$(id -g)" "${CONF_DIR}" "${WWW_DIR}" 2>/dev/null || true
