#!/bin/bash
# =====================================================================
# flowlog realm 후처리 (keycloak-setup 컨테이너에서 실행)
#  - realm JSON 에 clientScopes 를 넣으면 기본 scope(profile/email/basic...)가 생성되지 않으므로
#    커스텀 scope 는 import 이후 kcadm 으로 추가한다. (재실행해도 안전 - 이미 있으면 건너뜀)
# =====================================================================
set -euo pipefail

REALM=flowlog
CONFIG=/tmp/kcadm.config

kc() {
  /opt/keycloak/bin/kcadm.sh "$@" --config "$CONFIG"
}

echo "[setup] waiting for keycloak (${KC_SERVER}) ..."
until kc config credentials --server "$KC_SERVER" --realm master \
    --user "$KC_ADMIN" --password "$KC_ADMIN_PASSWORD" >/dev/null 2>&1; do
  sleep 3
done
until kc get "realms/${REALM}" >/dev/null 2>&1; do
  echo "[setup] waiting for realm import ..."
  sleep 2
done

client_uuid() {
  kc get clients -r "$REALM" -q "clientId=$1" --fields id --format csv --noquotes
}

# Keycloak 이미지(ubi-micro)에는 awk/grep 이 없으므로 bash 로만 파싱한다.
scope_uuid() {
  local id name
  while IFS=, read -r id name; do
    if [ "$name" = "$1" ]; then
      echo "$id"
    fi
  done < <(kc get client-scopes -r "$REALM" --fields id,name --format csv --noquotes)
}

ensure_scope() {
  local name="$1" desc="$2" id
  id=$(scope_uuid "$name")
  if [ -z "$id" ]; then
    kc create client-scopes -r "$REALM" \
      -s name="$name" -s protocol=openid-connect -s description="$desc" \
      -s 'attributes."include.in.token.scope"=true' \
      -s 'attributes."display.on.consent.screen"=false' >/dev/null
    id=$(scope_uuid "$name")
    echo "[setup] created client scope: $name" >&2
  fi
  echo "$id"
}

BFF=$(client_uuid flowlog-bff)
BATCH=$(client_uuid flowlog-batch)

for entry in "sample.list:샘플 조회" "sample.add:샘플 등록" "sample.edit:샘플 수정" "sample.del:샘플 삭제" \
    "notice.list:공지 조회" "notice.add:공지 등록" "notice.edit:공지 수정" "notice.del:공지 삭제" \
    "code.list:공통코드 조회" "code.add:공통코드 등록" "code.edit:공통코드 수정" "code.del:공통코드 삭제" \
    "menu.list:메뉴 조회" "menu.add:메뉴 등록" "menu.edit:메뉴 수정" "menu.del:메뉴 삭제" \
    "role.list:권한 조회" "role.add:권한 등록" "role.edit:권한 수정" "role.del:권한 삭제"; do
  name="${entry%%:*}"
  sid=$(ensure_scope "$name" "${entry#*:}")
  # scope 를 같은 이름의 realm 역할에 매핑 → 해당 역할이 있는 사용자/서비스계정에게만 scope 발급
  #  (Keycloak 이미지에는 tr/awk 가 없으므로 bash 치환으로 줄바꿈 제거)
  role_json=$(kc get "roles/${name}" -r "$REALM")
  role_json="${role_json//$'\n'/}"
  role_json="${role_json//$'\r'/}"
  kc create "client-scopes/${sid}/scope-mappings/realm" -r "$REALM" -b "[${role_json}]"
  kc update "clients/${BFF}/optional-client-scopes/${sid}" -r "$REALM"
  kc update "clients/${BATCH}/optional-client-scopes/${sid}" -r "$REALM"
done

echo "[setup] done - client scopes(role-restricted) linked to flowlog-bff, flowlog-batch (optional)"
