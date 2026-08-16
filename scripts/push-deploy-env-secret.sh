#!/usr/bin/env bash
# 로컬 deploy/production.env → GitHub Actions Secret DEPLOY_PRODUCTION_ENV
# main 배포 시 EC2 ~/deploy/.env 로 덮어쓴다.
#
# 사용:
#   1) cp deploy/production.env.example deploy/production.env  (또는 EC2에서 받아 채움)
#   2) 값 수정
#   3) ./scripts/push-deploy-env-secret.sh
#   4) main 에 push / Deploy workflow 실행
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ROOT}/deploy/production.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "[ERROR] missing $ENV_FILE"
  echo "  cp deploy/production.env.example deploy/production.env"
  exit 1
fi

if ! command -v gh >/dev/null; then
  echo "[ERROR] GitHub CLI(gh) 필요"
  exit 1
fi

echo "Uploading $ENV_FILE → secret DEPLOY_PRODUCTION_ENV ..."
gh secret set DEPLOY_PRODUCTION_ENV < "$ENV_FILE"
echo "done. Next deploy will sync to EC2 ~/deploy/.env"
