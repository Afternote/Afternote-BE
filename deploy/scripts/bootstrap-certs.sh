#!/usr/bin/env bash
# Greenfield / empty-disk TLS bootstrap for ~/deploy
# - Ensures certbot volume dirs exist
# - If Let's Encrypt live cert missing: write temporary self-signed so nginx can start
# - Then (optionally) request a real cert via webroot once HTTP is up
set -euo pipefail

DOMAIN="${TLS_DOMAIN:-afternote.kro.kr}"
EMAIL="${CERTBOT_EMAIL:-}"
DEPLOY_ROOT="${DEPLOY_ROOT:-$HOME/deploy}"
CONF_DIR="${DEPLOY_ROOT}/data/certbot/conf"
WWW_DIR="${DEPLOY_ROOT}/data/certbot/www"
LIVE_DIR="${CONF_DIR}/live/${DOMAIN}"

mkdir -p "${CONF_DIR}" "${WWW_DIR}"

ensure_self_signed() {
  if [ -f "${LIVE_DIR}/fullchain.pem" ] && [ -f "${LIVE_DIR}/privkey.pem" ]; then
    echo "[INFO] TLS material already present for ${DOMAIN}"
    return 0
  fi

  echo "[INFO] No Let's Encrypt cert for ${DOMAIN}; creating temporary self-signed cert"
  mkdir -p "${LIVE_DIR}"
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout "${LIVE_DIR}/privkey.pem" \
    -out "${LIVE_DIR}/fullchain.pem" \
    -subj "/CN=${DOMAIN}"
  # nginx often expects chain.pem / cert.pem as well
  cp "${LIVE_DIR}/fullchain.pem" "${LIVE_DIR}/cert.pem"
  cp "${LIVE_DIR}/fullchain.pem" "${LIVE_DIR}/chain.pem"
}

request_letsencrypt() {
  if [ -z "${EMAIL}" ]; then
    echo "[WARN] CERTBOT_EMAIL not set; keeping current cert (self-signed or previous)"
    return 0
  fi

  # Skip if already a Let's Encrypt cert (Account/README marker or long-lived)
  if [ -d "${CONF_DIR}/accounts" ] && [ -f "${LIVE_DIR}/fullchain.pem" ]; then
    # renew quietly if near expiry; ignore failure on fresh self-signed
    echo "[INFO] Attempting certbot certonly --webroot for ${DOMAIN}"
  else
    echo "[INFO] Requesting Let's Encrypt certificate for ${DOMAIN}"
  fi

  cd "${DEPLOY_ROOT}"
  docker compose --profile cert run --rm --entrypoint certbot certbot certonly \
    --webroot -w /var/www/certbot \
    --email "${EMAIL}" \
    --agree-tos \
    --no-eff-email \
    --non-interactive \
    -d "${DOMAIN}" \
    --keep-until-expiring \
    || echo "[WARN] certbot did not complete; stack may still run on self-signed until next deploy"
}

ensure_self_signed
