#!/usr/bin/env bash
# dev-up.sh 로 띄운 앱·Gateway·화면 종료. --infra 면 Redis·Keycloak·Oracle 컨테이너도 중지 (DB 볼륨은 유지)
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="$ROOT/.run/pids.txt"

kill_tree() {
  local pid="$1" child
  for child in $(pgrep -P "$pid" 2>/dev/null); do kill_tree "$child"; done
  kill "$pid" 2>/dev/null
}

if [ -f "$PID_FILE" ]; then
  while IFS='=' read -r name pid; do
    kill_tree "$pid"
    echo "[dev-down] $name (PID $pid) 종료"
  done <"$PID_FILE"
  rm -f "$PID_FILE"
else
  echo "[dev-down] 실행 기록(.run/pids.txt)이 없습니다."
fi

if [ "${1:-}" = "--infra" ]; then
  docker compose -f "$ROOT/infra/keycloak/docker-compose.yml" stop >/dev/null
  docker compose -f "$ROOT/infra/redis/docker-compose.yml" stop >/dev/null
  docker compose -f "$ROOT/infra/oracle/docker-compose.yml" stop >/dev/null 2>&1 || true
  echo "[dev-down] Redis / Keycloak / Oracle 컨테이너 중지 (DB 데이터는 볼륨에 유지)"
fi
