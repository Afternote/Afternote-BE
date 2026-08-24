#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PATCHER="${ROOT}/patch-certbot-webroot.py"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

tmpdir=$(mktemp -d)
trap 'rm -rf "${tmpdir}"' EXIT

# Standalone lineage: no webroot_path
cat > "${tmpdir}/standalone.conf" <<'EOF'
version = 2.11.0
[renewalparams]
authenticator = standalone
account = abc
server = https://acme-v02.api.letsencrypt.org/directory
EOF

python3 "${PATCHER}" "${tmpdir}/standalone.conf" afternote.kro.kr
grep -qx "authenticator = webroot" "${tmpdir}/standalone.conf" || fail "authenticator not webroot"
grep -qx "webroot_path = /var/www/certbot," "${tmpdir}/standalone.conf" || fail "webroot_path missing"
grep -qx "afternote.kro.kr = /var/www/certbot" "${tmpdir}/standalone.conf" || fail "webroot_map domain missing"

# Webroot authenticator but no path (the EC2 dry-run failure)
cat > "${tmpdir}/half.conf" <<'EOF'
version = 2.11.0
[renewalparams]
authenticator = webroot
account = abc
EOF

python3 "${PATCHER}" "${tmpdir}/half.conf" afternote.kro.kr
grep -qx "webroot_path = /var/www/certbot," "${tmpdir}/half.conf" || fail "half conf webroot_path"
grep -Fqx "[[webroot_map]]" "${tmpdir}/half.conf" || fail "half conf map section"

echo "[OK] patch-certbot-webroot"
