#!/usr/bin/env bash
# =====================================================================
# EC2 처음 준비 (Amazon Linux 2023) - 한 번만, root 권한으로
#   sudo bash deploy/ec2-setup.sh
#  - Docker + Compose 플러그인 + Buildx, 부팅 시 자동 시작
#  - Docker 데몬 보안 기본값 : 컨테이너 권한 상승 차단(no-new-privileges), 로그 크기 제한, 데몬 재시작 시 컨테이너 유지
#  - OS 보안 업데이트 자동 적용 (dnf-automatic, 보안 패치만)
#  - 스왑 2GB (빌드·Keycloak 기동 때 메모리 여유), 시간대 Asia/Seoul
#  - docker 그룹 : ec2-user, ssm-user(Session Manager 기본 사용자), sudo 로 실행한 사용자
#  - 앱 폴더 /opt/flowlog (실행한 사용자 소유) - 저장소를 여기에 먼저 clone 한 뒤 이 스크립트를 실행
# =====================================================================
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "sudo bash $0 로 실행하세요" >&2; exit 1; }

step() { echo "[ec2-setup] $*"; }

step "패키지 (docker, git, dnf-automatic)"
dnf install -y docker git dnf-automatic >/dev/null

step "Docker 데몬 설정 (/etc/docker/daemon.json)"
mkdir -p /etc/docker
if [ ! -f /etc/docker/daemon.json ]; then
  cat > /etc/docker/daemon.json <<'JSON'
{
  "no-new-privileges": true,
  "live-restore": true,
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "5" }
}
JSON
fi
systemctl enable --now docker
systemctl restart docker

step "Compose / Buildx 플러그인"
PLUGINS=/usr/local/lib/docker/cli-plugins
mkdir -p "$PLUGINS"
ARCH=$(uname -m)
if ! docker compose version >/dev/null 2>&1; then
  curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${ARCH}" \
    -o "$PLUGINS/docker-compose"
  chmod +x "$PLUGINS/docker-compose"
fi
if ! docker buildx version >/dev/null 2>&1; then
  BUILDX_ARCH=$([ "$ARCH" = "aarch64" ] && echo arm64 || echo amd64)
  BUILDX_VER=$(curl -fsSL https://api.github.com/repos/docker/buildx/releases/latest | grep -m1 '"tag_name"' | cut -d'"' -f4)
  curl -fsSL "https://github.com/docker/buildx/releases/download/${BUILDX_VER}/buildx-${BUILDX_VER}.linux-${BUILDX_ARCH}" \
    -o "$PLUGINS/docker-buildx"
  chmod +x "$PLUGINS/docker-buildx"
fi

step "OS 보안 업데이트 자동 적용 (매일, 보안 패치만)"
sed -i 's/^upgrade_type.*/upgrade_type = security/; s/^apply_updates.*/apply_updates = yes/' \
  /etc/dnf/automatic.conf
systemctl enable --now dnf-automatic.timer

step "스왑 2GB"
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

step "시간대 / docker 그룹 / 앱 폴더"
timedatectl set-timezone Asia/Seoul || true
OWNER="${SUDO_USER:-ec2-user}"
for user in ec2-user ssm-user "$OWNER"; do
  if id "$user" >/dev/null 2>&1; then usermod -aG docker "$user"; fi
done
mkdir -p /opt/flowlog
chown -R "$OWNER":"$OWNER" /opt/flowlog

docker compose version
step "완료 - 세션을 닫았다가 다시 접속(docker 그룹 적용) 후 :"
step "  cd /opt/flowlog/deploy && ./gen-secrets.sh <도메인> && docker compose up -d --build"
