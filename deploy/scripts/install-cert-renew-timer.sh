#!/usr/bin/env bash
# Install or refresh the host systemd timer that runs renew-certs.sh daily.
set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-$HOME/deploy}"
DEPLOY_USER="$(id -un)"
DEPLOY_HOME="${HOME}"
UNIT_SRC="${DEPLOY_ROOT}/systemd"
UNIT_DST=/etc/systemd/system

if [ ! -f "${UNIT_SRC}/afternote-cert-renew.service" ] || [ ! -f "${UNIT_SRC}/afternote-cert-renew.timer" ]; then
  echo "[ERROR] missing unit files under ${UNIT_SRC}"
  exit 1
fi

tmp=$(mktemp)
sed \
  -e "s|__DEPLOY_USER__|${DEPLOY_USER}|g" \
  -e "s|__DEPLOY_HOME__|${DEPLOY_HOME}|g" \
  -e "s|__DEPLOY_ROOT__|${DEPLOY_ROOT}|g" \
  "${UNIT_SRC}/afternote-cert-renew.service" > "${tmp}"
sudo install -m 0644 "${tmp}" "${UNIT_DST}/afternote-cert-renew.service"
rm -f "${tmp}"
sudo install -m 0644 "${UNIT_SRC}/afternote-cert-renew.timer" "${UNIT_DST}/afternote-cert-renew.timer"

sudo systemctl daemon-reload
sudo systemctl enable --now afternote-cert-renew.timer
sudo systemctl is-enabled afternote-cert-renew.timer
sudo systemctl list-timers afternote-cert-renew.timer --no-pager
echo "[INFO] installed afternote-cert-renew.timer as ${DEPLOY_USER}"
