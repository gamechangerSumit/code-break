#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run with: sudo bash deploy/ec2-bootstrap.sh"
  exit 1
fi

if [[ ! -f /etc/os-release ]]; then
  echo "Cannot identify Linux distribution."
  exit 1
fi
. /etc/os-release
if [[ "${ID}" != "ubuntu" ]]; then
  echo "This bootstrap script currently supports Ubuntu EC2 images only."
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git openssl

install -m 0755 -d /etc/apt/keyrings
curl -fsSL "https://download.docker.com/linux/ubuntu/gpg" -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

ARCH="$(dpkg --print-architecture)"
CODENAME="${UBUNTU_CODENAME:-$VERSION_CODENAME}"
echo "deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

DEPLOY_USER="${SUDO_USER:-ubuntu}"
if id "${DEPLOY_USER}" >/dev/null 2>&1; then
  usermod -aG docker "${DEPLOY_USER}"
  echo "Docker installed. User '${DEPLOY_USER}' added to docker group."
  echo "Log out and SSH in again before running docker without sudo."
else
  echo "Docker installed. Add your SSH user to the docker group manually."
fi

docker --version
docker compose version
