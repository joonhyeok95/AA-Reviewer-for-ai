#!/usr/bin/env bash
# =====================================================================
# 옮겨 온 데이터(oracle-to-h2.sh 결과 flowlog-data.tgz)를 운영 볼륨에 넣는다 (EC2 에서 실행)
#   사용 : ./import-data.sh /path/flowlog-data.tgz
#  - 백엔드를 멈추고 볼륨 flowlog-app-data 의 db/ files/ 를 교체 (기존 것은 /data/before-import-날짜 로 보관)
#  - 파일 소유자는 컨테이너 사용자(10001)
# =====================================================================
set -euo pipefail
[ -f "${1:-}" ] || { echo "사용 : $0 flowlog-data.tgz" >&2; exit 1; }
DIR="$(cd "$(dirname "$1")" && pwd)"
NAME="$(basename "$1")"
cd "$(dirname "$0")"
# Windows(Git Bash)에서 검증할 때는 Docker 에 Windows 경로를 넘긴다
if command -v cygpath >/dev/null 2>&1; then DIR="$(cygpath -w "$DIR")"; export MSYS_NO_PATHCONV=1; fi

docker volume create flowlog-app-data >/dev/null
docker compose stop backend >/dev/null 2>&1 || true

STAMP=$(date +%Y%m%d-%H%M%S)
docker run --rm -v flowlog-app-data:/data -v "$DIR":/in:ro alpine:3 sh -c "
  set -e
  if [ -d /data/db ] || [ -d /data/files ]; then
    mkdir -p /data/before-import-$STAMP
    for d in db files; do [ -d /data/\$d ] && mv /data/\$d /data/before-import-$STAMP/; done
  fi
  tar -xzf /in/$NAME -C /data
  mkdir -p /data/backup
  chown -R 10001:10001 /data
  ls -la /data /data/db
"
echo "[import] 완료 - docker compose up -d 로 시작 (기존 데이터가 있었다면 볼륨의 /data/before-import-$STAMP 에 보관)"
