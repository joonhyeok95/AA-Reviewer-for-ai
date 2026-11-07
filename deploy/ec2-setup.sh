#!/usr/bin/env bash
# =====================================================================
# EC2 처음 준비 (Amazon Linux 2023, root 또는 sudo) - 한 번만
#   sudo bash deploy/ec2-setup.sh
#  - Docker + Compose 플러그인 + buildx, 부팅 시 자동 시작
#  - 스왑 2GB (빌드·Keycloak 기동 때 메모리 여유)
#  - ec2-user 를 docker 그룹에 (다시 로그인하면 sudo 없이 docker)
# =====================================================================
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  dnf install -y docker git
fi
systemctl enable --now docker

# Compose / Buildx 플러그인 (Amazon Linux 2023 패키지에 없을 수 있어 GitHub 릴리스에서)
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

if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

id ec2-user >/dev/null 2>&1 && usermod -aG docker ec2-user
timedatectl set-timezone Asia/Seoul || true

docker compose version
echo "[ec2-setup] 완료 - 다시 로그인 후: git clone … && cd repo/deploy && ./gen-secrets.sh <도메인> && docker compose up -d --build"
