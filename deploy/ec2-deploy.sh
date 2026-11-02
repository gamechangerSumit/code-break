#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "Docker Engine and the Compose plugin are required. Run: sudo bash deploy/ec2-bootstrap.sh"
  exit 1
fi

if [[ ! -f backend/.env ]]; then
  cp backend/.env.example backend/.env
  chmod 600 backend/.env
fi

set_env() {
  local key="$1"
  local value="$2"
  if grep -qE "^\${key}=" backend/.env; then
    sed -i "s|^\${key}=.*|\${key}=\${value}|" backend/.env
  else
    printf '%s=%s\\n' "${key}" "${value}" >> backend/.env
  fi
}

read_env() {
  local key="$1"
  sed -n "s/^\${key}=//p" backend/.env | tail -n 1
}

DOMAIN_VALUE="${DOMAIN:-$(read_env DOMAIN)}"
if [[ -z "${DOMAIN_VALUE}" || "${DOMAIN_VALUE}" == "localhost" || "${DOMAIN_VALUE}" == *"example.com"* ]]; then
  echo "Set your real DNS name first, for example:"
  echo "  DOMAIN=codebreak.yourdomain.com bash deploy/ec2-deploy.sh"
  exit 1
fi

set_env DOMAIN "${DOMAIN_VALUE}"
set_env POSTGRES_PASSWORD "$(read_env POSTGRES_PASSWORD | grep -q '^replace-' && openssl rand -hex 32 || read_env POSTGRES_PASSWORD)"
set_env JWT_SECRET "$(read_env JWT_SECRET | grep -q '^replace-' && openssl rand -hex 48 || read_env JWT_SECRET)"
set_env RUSTFS_ACCESS_KEY "$(read_env RUSTFS_ACCESS_KEY | grep -q '^replace-' && openssl rand -hex 16 || read_env RUSTFS_ACCESS_KEY)"
set_env RUSTFS_SECRET_KEY "$(read_env RUSTFS_SECRET_KEY | grep -q '^replace-' && openssl rand -hex 32 || read_env RUSTFS_SECRET_KEY)"
set_env APP_CORS_ALLOWED_ORIGINS "https://${DOMAIN_VALUE}"
chmod 600 backend/.env

COMPOSE=(docker compose --env-file backend/.env -f backend/docker-compose.yml)
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" up -d --build --remove-orphans
"${COMPOSE[@]}" ps

echo
echo "Deployment containers have been started."
echo "Site: https://${DOMAIN_VALUE}"
echo "Check logs: docker compose --env-file backend/.env -f backend/docker-compose.yml logs -f"
echo "Update later: git pull --ff-only origin main && bash deploy/ec2-deploy.sh"
