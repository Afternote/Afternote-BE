#!/usr/bin/env bash
# Issue or renew Let's Encrypt via webroot (nginx stays on :80), then reload nginx.
# Usage:
#   ./renew-certs.sh           # convert standalone→webroot if needed, renew if due, reload
#   ./renew-certs.sh --dry-run # after webroot is configured, ACME dry-run (no file change, no reload)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tls-lib.sh disable=SC1091
source "${ROOT}/tls-lib.sh"

DEPLOY_ROOT="${DEPLOY_ROOT:-$HOME/deploy}"
DOMAIN="${TLS_DOMAIN:-afternote.kro.kr}"
CONF_DIR="${DEPLOY_ROOT}/data/certbot/conf"
WWW_DIR="${DEPLOY_ROOT}/data/certbot/www"
LIVE_DIR="${CONF_DIR}/live/${DOMAIN}"
LIVE_PEM="${LIVE_DIR}/fullchain.pem"

DRY_RUN=0
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=1
fi

if [ -f "${DEPLOY_ROOT}/.certbot-email" ]; then
  CERTBOT_EMAIL="${CERTBOT_EMAIL:-$(tr -d '[:space:]' < "${DEPLOY_ROOT}/.certbot-email")}"
fi

cd "${DEPLOY_ROOT}"

chown_certs() {
  sudo chown -R "$(id -u)":"$(id -g)" "${CONF_DIR}" "${WWW_DIR}" 2>/dev/null || true
}

run_certbot() {
  docker compose --profile cert run --rm --entrypoint certbot certbot "$@"
}

# keep-until-expiring often skips a challenge, so standalone lineage would stay standalone.
ensure_renewal_webroot() {
  local conf="${CONF_DIR}/renewal/${DOMAIN}.conf"
  if ! sudo test -f "${conf}"; then
    echo "[WARN] renewal conf missing at ${conf}"
    return 0
  fi
  if run_certbot reconfigure \
    --cert-name "${DOMAIN}" \
    --webroot -w /var/www/certbot \
    --non-interactive \
    -d "${DOMAIN}"; then
    echo "[INFO] certbot reconfigure set authenticator=webroot"
    return 0
  fi
  echo "[INFO] patching ${conf} authenticator=webroot"
  sudo python3 - "${conf}" "${DOMAIN}" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
domain = sys.argv[2]
text = path.read_text()
lines = []
seen_auth = False
seen_webroot_path = False
for line in text.splitlines(True):
    if line.startswith("authenticator ="):
        lines.append("authenticator = webroot\n")
        seen_auth = True
        continue
    if line.startswith("webroot_path ="):
        lines.append("webroot_path = /var/www/certbot,\n")
        seen_webroot_path = True
        continue
    lines.append(line)
body = "".join(lines)
if not seen_auth:
    if "[renewalparams]\n" in body:
        body = body.replace("[renewalparams]\n", "[renewalparams]\nauthenticator = webroot\n", 1)
    else:
        body += "\n[renewalparams]\nauthenticator = webroot\n"
if not seen_webroot_path:
    body += "webroot_path = /var/www/certbot,\n"
if "[[webroot_map]]" not in body:
    body += f"[[webroot_map]]\n{domain} = /var/www/certbot\n"
path.write_text(body)
PY
}

have_pem() {
  sudo test -f "${LIVE_PEM}" && sudo test -f "${LIVE_DIR}/privkey.pem"
}

reload_nginx() {
  "${ROOT}/reload-nginx.sh"
}

echo "[INFO] TLS renew domain=${DOMAIN} dry_run=${DRY_RUN}"

if [ -z "${CERTBOT_EMAIL:-}" ] && [ "${DRY_RUN}" -eq 0 ]; then
  echo "[ERROR] CERTBOT_EMAIL is empty (secret or ${DEPLOY_ROOT}/.certbot-email)"
  exit 1
fi

mkdir -p "${CONF_DIR}" "${WWW_DIR}"

if ! docker compose exec -T nginx nginx -t >/dev/null 2>&1; then
  echo "[ERROR] nginx is not running or nginx -t failed; webroot needs :80"
  docker compose ps nginx || true
  exit 1
fi

if have_pem && tls_is_lets_encrypt "${LIVE_PEM}"; then
  days=$(tls_days_left "${LIVE_PEM}")
  echo "[INFO] existing Let's Encrypt cert, remaining_days=${days}"
else
  days=-1
  echo "[INFO] no Let's Encrypt cert on disk (missing or self-signed)"
fi

if [ "${DRY_RUN}" -eq 1 ]; then
  if ! have_pem || ! tls_is_lets_encrypt "${LIVE_PEM}"; then
    echo "[ERROR] --dry-run needs an existing Let's Encrypt lineage"
    exit 1
  fi
  echo "[INFO] certbot renew --dry-run (must use webroot, not bind :80)"
  run_certbot renew --dry-run --non-interactive
  chown_certs
  echo "[INFO] dry-run OK"
  exit 0
fi

# First LE issue from self-signed: drop fake files so certbot can create a real lineage.
# nginx workers keep the previous cert in memory until reload.
if have_pem && ! tls_is_lets_encrypt "${LIVE_PEM}"; then
  echo "[INFO] replacing non-LE cert with Let's Encrypt (webroot); nginx stays up"
  sudo rm -rf "${LIVE_DIR}" \
    "${CONF_DIR}/archive/${DOMAIN}" \
    "${CONF_DIR}/renewal/${DOMAIN}.conf"
fi

# keep-until-expiring updates authenticator to webroot without forcing a new cert.
echo "[INFO] certonly --webroot (issue or convert standalone → webroot)"
if ! run_certbot certonly \
  --webroot -w /var/www/certbot \
  --preferred-challenges http \
  --keep-until-expiring \
  --email "${CERTBOT_EMAIL}" \
  --agree-tos \
  --no-eff-email \
  --non-interactive \
  --cert-name "${DOMAIN}" \
  -d "${DOMAIN}"; then
  echo "[ERROR] certbot webroot certonly failed"
  if ! have_pem; then
    echo "[INFO] restoring 1-day self-signed so nginx can still boot"
    tls_write_self_signed "${LIVE_DIR}" "${DOMAIN}"
  fi
  chown_certs
  exit 1
fi
chown_certs

ensure_renewal_webroot
chown_certs

if ! have_pem || ! tls_is_lets_encrypt "${LIVE_PEM}"; then
  echo "[ERROR] expected Let's Encrypt pem after certonly"
  exit 1
fi

days=$(tls_days_left "${LIVE_PEM}")
echo "[INFO] after certonly, remaining_days=${days}"

if tls_needs_renew "${days}"; then
  echo "[INFO] remaining <= ${TLS_RENEW_BEFORE_DAYS}d; certbot renew"
  run_certbot renew --non-interactive
  chown_certs
  days=$(tls_days_left "${LIVE_PEM}")
  echo "[INFO] after renew, remaining_days=${days}"
else
  echo "[INFO] remaining > ${TLS_RENEW_BEFORE_DAYS}d; skip renew"
fi

reload_nginx

if tls_needs_alert "${days}"; then
  echo "[ERROR] Let's Encrypt remaining_days=${days} <= ${TLS_ALERT_BEFORE_DAYS}; auto-renew did not leave enough validity"
  exit 1
fi

echo "[INFO] TLS webroot path OK, remaining_days=${days}"
