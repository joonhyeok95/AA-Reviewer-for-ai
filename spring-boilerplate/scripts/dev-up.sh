#!/usr/bin/env bash
# =====================================================================
# 로컬 전체 실행 (macOS / Linux / Git Bash) - Windows PowerShell 은 dev-up.ps1
#   Redis + Keycloak(Docker) → 앱(9091[, 9092]) → Gateway(9909) → 화면(5173)
#   사용 : ./scripts/dev-up.sh [--instances 2] [--no-frontend]
#   종료 : ./scripts/dev-down.sh [--infra]
#   저장소 3개(spring-boilerplate, spring-gateway, vue-frontend)가 같은 폴더 아래 나란히 있어야 한다.
# =====================================================================
set -euo pipefail

INSTANCES=1
FRONTEND=1
while [ $# -gt 0 ]; do
  case "$1" in
    --instances) INSTANCES="$2"; shift 2 ;;
    --no-frontend) FRONTEND=0; shift ;;
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

step "Redis / Keycloak (Docker)"
docker compose -f "$ROOT/infra/redis/docker-compose.yml" up -d >/dev/null
docker compose -f "$ROOT/infra/keycloak/docker-compose.yml" up -d >/dev/null
wait_http "http://localhost:7000/realms/flowlog/.well-known/openid-configuration" "Keycloak" 240

start_bg app-9091 "$ROOT" ./mvnw -q spring-boot:run
if [ "$INSTANCES" = 2 ]; then
  start_bg app-9092 "$ROOT" ./mvnw -q spring-boot:run \
    "-Dspring-boot.run.arguments=--server.port=9092 --management.server.port=9192"
fi
start_bg gateway "$GATEWAY_DIR" ./mvnw -q spring-boot:run
[ "$FRONTEND" = 1 ] && start_bg frontend "$FRONTEND_DIR" npm run dev

wait_http "http://127.0.0.1:9191/actuator/health" "앱 9091"
[ "$INSTANCES" = 2 ] && wait_http "http://127.0.0.1:9192/actuator/health" "앱 9092"
wait_http "http://127.0.0.1:9910/actuator/health" "Gateway"
[ "$FRONTEND" = 1 ] && wait_http "http://127.0.0.1:5173/" "화면" 120

echo
echo "접속 : http://localhost:9909   (Swagger http://localhost:9909/cm/swagger-ui.html)"
echo "종료 : ./scripts/dev-down.sh"
