#!/usr/bin/env bash
# =====================================================================
# .env 생성 : .env.example 을 복사하고 비어 있는 비밀값을 무작위로 채운다 (이미 있는 값은 그대로)
#   사용 : ./gen-secrets.sh church.example.com
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] || cp .env.example .env
rand() { openssl rand -base64 "${1:-24}" | tr -d '/+=\n' | cut -c1-"${2:-32}"; }
set_if_empty() {
  local key="$1" value="$2"
  if grep -qE "^${key}=$" .env; then
    sed -i.bak "s|^${key}=$|${key}=${value}|" .env && rm -f .env.bak
    echo "[gen-secrets] ${key} 생성"
  fi
}
if [ -n "${1:-}" ]; then
  sed -i.bak "s|^DOMAIN=.*|DOMAIN=$1|" .env && rm -f .env.bak
fi
set_if_empty APP_ADMIN_PASSWORD "$(rand 18 20)"
set_if_empty KC_ADMIN_PASSWORD "$(rand 18 20)"
set_if_empty KC_DB_PASSWORD "$(rand 24 32)"
set_if_empty BFF_CLIENT_SECRET "$(rand 32 40)"
set_if_empty BATCH_CLIENT_SECRET "$(rand 32 40)"
set_if_empty SESSION_ENCRYPTION_KEY "$(openssl rand -base64 32)"
set_if_empty H2_PASSWORD "$(rand 24 32)"
chmod 600 .env
echo "[gen-secrets] deploy/.env 준비 완료 - DOMAIN=$(grep '^DOMAIN=' .env | cut -d= -f2)"
echo "[gen-secrets] 관리자 비밀번호는 .env 의 APP_ADMIN_PASSWORD (첫 로그인 후 Keycloak 에서 바꿔도 됨)"
