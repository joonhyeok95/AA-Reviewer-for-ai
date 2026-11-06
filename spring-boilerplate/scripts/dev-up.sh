#!/usr/bin/env bash
# =====================================================================
# 로컬 전체 실행 (macOS / Linux / Git Bash) - Windows PowerShell 은 dev-up.ps1
#   Redis + Keycloak(Docker) → 앱(9091[, 9092]) → Gateway(9909) → 화면(5173)
#   사용 : ./scripts/dev-up.sh [--instances 2] [--no-frontend] [--lan [--lan-host 192.168.x.x]]
#   --lan : 같은 네트워크의 다른 기기에서 http://{IP}:9909 로 접속 (로그인 화면도 Gateway 경유, 9909 만 개방)
#   종료 : ./scripts/dev-down.sh [--infra]
#   저장소 3개(spring-boilerplate, spring-gateway, vue-frontend)가 같은 폴더 아래 나란히 있어야 한다.
# =====================================================================
set -euo pipefail

INSTANCES=1
FRONTEND=1
LAN=0
LAN_HOST=""
while [ $# -gt 0 ]; do
  case "$1" in
    --instances) INSTANCES="$2"; shift 2 ;;
    --no-frontend) FRONTEND=0; shift ;;
    --lan) LAN=1; shift ;;
    --lan-host) LAN=1; LAN_HOST="$2"; shift 2 ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEVELOP="$(dirname "$ROOT")"
GATEWAY_DIR="$DEVELOP/spring-gateway"
FRONTEND_DIR="$DEVELOP/vue-frontend"
RUN_DIR="$ROOT/.run"
PID_FILE="$RUN_DIR/pids.txt"
mkdir -p "$RUN_DIR"

step() { echo "[dev-up] $*"; }

port_busy() {
  if command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:"$1" -sTCP:LISTEN -n -P >/dev/null 2>&1
  else
    (echo >/dev/tcp/127.0.0.1/"$1") >/dev/null 2>&1
  fi
}

wait_http() {
  local url="$1" name="$2" timeout="${3:-180}" waited=0
  until code=$(curl -s -o /dev/null -w "%{http_code}" "$url") && [ "$code" != "000" ] && [ "$code" -lt 500 ]; do
    sleep 3; waited=$((waited + 3))
    if [ "$waited" -ge "$timeout" ]; then
      echo "[dev-up] $name 이(가) ${timeout}초 안에 뜨지 않았습니다. 로그: $RUN_DIR" >&2; exit 1
    fi
  done
  step "$name 준비됨 ($url)"
}

start_bg() {
  local name="$1" dir="$2"; shift 2
  (cd "$dir" && nohup "$@" >"$RUN_DIR/$name.log" 2>&1 &
   echo "$name=$!" >>"$PID_FILE")
  step "$name 시작 (로그 .run/$name.log)"
}

[ -d "$GATEWAY_DIR" ] || { echo "저장소가 없습니다: $GATEWAY_DIR" >&2; exit 1; }
[ "$FRONTEND" = 0 ] || [ -d "$FRONTEND_DIR" ] || { echo "저장소가 없습니다: $FRONTEND_DIR" >&2; exit 1; }

PORTS="9091 9191 9909 9910"
[ "$INSTANCES" = 2 ] && PORTS="$PORTS 9092 9192"
[ "$FRONTEND" = 1 ] && PORTS="$PORTS 5173"
BUSY=""
for port in $PORTS; do port_busy "$port" && BUSY="$BUSY $port"; done
if [ -n "$BUSY" ]; then
  echo "[dev-up] 사용 중인 포트가 있어 시작하지 않습니다:$BUSY (이전 실행이면 dev-down.sh)" >&2; exit 1
fi
rm -f "$PID_FILE"

# LAN 접속 : 공개 URL 을 http://{IP}:9909 로 맞춘다 (Keycloak 은 Gateway 경유)
PUBLIC_URL="http://localhost:9909"
APP_ENV=()
GATEWAY_ENV=()
if [ "$LAN" = 1 ]; then
  if [ -z "$LAN_HOST" ]; then
    LAN_HOST=$( (hostname -I 2>/dev/null | cut -d' ' -f1) || true)
    [ -n "$LAN_HOST" ] || LAN_HOST=$(ipconfig getifaddr en0 2>/dev/null || true)
  fi
  if ! [[ "$LAN_HOST" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]]; then
    echo "LAN IP 를 찾지 못했습니다. --lan-host 192.168.x.x 로 지정하세요." >&2; exit 2
  fi
  PUBLIC_URL="http://${LAN_HOST}:9909"
  IFS=. read -r o1 o2 o3 _ <<<"$LAN_HOST"
  SUBNET="${o1}\.${o2}\.${o3}\.\d{1,3}"
  APP_ENV=(KEYCLOAK_ISSUER_URI="$PUBLIC_URL/realms/flowlog" NOTICE_LINK_TEMPLATE="$PUBLIC_URL/notices/{id}")
  # 같은 대역 기기의 요청에도 X-Forwarded-Host 를 붙여야 백엔드가 공개 URL 로 콜백 주소를 만든다
  GATEWAY_ENV=(GATEWAY_IDP_PROXY_ENABLED=true "GATEWAY_TRUSTED_PROXIES=127\.0\.0\.1|0:0:0:0:0:0:0:1|::1|$SUBNET")
  step "LAN 모드 : $PUBLIC_URL (방화벽에서 TCP 9909 인바운드 허용 필요)"
fi

step "Redis / Keycloak (Docker)"
docker compose -f "$ROOT/infra/redis/docker-compose.yml" up -d >/dev/null
# Keycloak 공개 URL(KC_PUBLIC_URL)이 바뀌면 컨테이너를 다시 만든다 (dev 모드라 realm 재import)
if [ "$LAN" = 1 ]; then
  KC_PUBLIC_URL="$PUBLIC_URL" PUBLIC_HOST="$LAN_HOST" \
    docker compose -f "$ROOT/infra/keycloak/docker-compose.yml" up -d >/dev/null
else
  docker compose -f "$ROOT/infra/keycloak/docker-compose.yml" up -d >/dev/null
fi
wait_http "http://localhost:7000/realms/flowlog/.well-known/openid-configuration" "Keycloak" 240

start_bg app-9091 "$ROOT" env "${APP_ENV[@]}" ./mvnw -q spring-boot:run
if [ "$INSTANCES" = 2 ]; then
  start_bg app-9092 "$ROOT" env "${APP_ENV[@]}" ./mvnw -q spring-boot:run \
    "-Dspring-boot.run.arguments=--server.port=9092 --management.server.port=9192"
fi
start_bg gateway "$GATEWAY_DIR" env "${GATEWAY_ENV[@]}" ./mvnw -q spring-boot:run
if [ "$FRONTEND" = 1 ]; then
  if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
    step "화면 의존성 설치 (npm ci, 최초 1회)"
    (cd "$FRONTEND_DIR" && npm ci --no-audit --no-fund >/dev/null)
  fi
  start_bg frontend "$FRONTEND_DIR" npm run dev
fi

wait_http "http://127.0.0.1:9191/actuator/health" "앱 9091"
[ "$INSTANCES" = 2 ] && wait_http "http://127.0.0.1:9192/actuator/health" "앱 9092"
wait_http "http://127.0.0.1:9910/actuator/health" "Gateway"
[ "$FRONTEND" = 1 ] && wait_http "http://127.0.0.1:5173/" "화면" 120

echo
echo "접속 : $PUBLIC_URL   (Swagger $PUBLIC_URL/cm/swagger-ui.html)"
echo "종료 : ./scripts/dev-down.sh"
