#!/usr/bin/env bash
# Greenfield TLS bootstrap for ~/deploy
# 1) Ensure dirs exist
# 2) If no Let's Encrypt lineage, put a temporary self-signed under live/ so nginx can boot
# 3) Real issuance is done by deploy.yml (stop nginx → certbot standalone → start)
set -euo pipefail

DOMAIN="${TLS_DOMAIN:-afternote.kro.kr}"
DEPLOY_ROOT="${DEPLOY_ROOT:-$HOME/deploy}"
CONF_DIR="${DEPLOY_ROOT}/data/certbot/conf"
WWW_DIR="${DEPLOY_ROOT}/data/certbot/www"
LIVE_DIR="${CONF_DIR}/live/${DOMAIN}"

mkdir -p "${CONF_DIR}" "${WWW_DIR}"

# Real Let's Encrypt lineage has an account under conf/accounts
if [ -d "${CONF_DIR}/accounts" ] && [ -f "${LIVE_DIR}/fullchain.pem" ]; then
  echo "[INFO] Let's Encrypt cert already present for ${DOMAIN}"
  exit 0
fi

if [ -f "${LIVE_DIR}/fullchain.pem" ] && [ -f "${LIVE_DIR}/privkey.pem" ]; then
  echo "[INFO] Temporary TLS material already present for ${DOMAIN}"
  exit 0
fi

echo "[INFO] No Let's Encrypt cert for ${DOMAIN}; creating temporary self-signed cert"
mkdir -p "${LIVE_DIR}"
openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
  -keyout "${LIVE_DIR}/privkey.pem" \
  -out "${LIVE_DIR}/fullchain.pem" \
  -subj "/CN=${DOMAIN}"
cp "${LIVE_DIR}/fullchain.pem" "${LIVE_DIR}/cert.pem"
cp "${LIVE_DIR}/fullchain.pem" "${LIVE_DIR}/chain.pem"
