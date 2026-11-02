#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"
ENV_FILE="${ENV_FILE:-backend/.env}"
BACKUP_DIR="${BACKUP_DIR:-${ROOT_DIR}/backups}"
COMPOSE=(docker compose --env-file "${ENV_FILE}" -f backend/docker-compose.yml)

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}; deploy the stack first." >&2
  exit 1
fi
command -v docker >/dev/null || { echo "Docker is required." >&2; exit 1; }
mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="${BACKUP_DIR}/codebreak-${STAMP}"
mkdir -m 700 "${DEST}"

cleanup() {
  if [[ -d "${DEST}" ]]; then
    find "${DEST}" -maxdepth 1 -type f -size 0 -delete
  fi
}
trap cleanup EXIT

echo "Checking database readiness..."
"${COMPOSE[@]}" exec -T postgres pg_isready -U postgres -d codebreak

echo "Creating PostgreSQL logical backup..."
"${COMPOSE[@]}" exec -T postgres pg_dump -U postgres -d codebreak -Fc > "${DEST}/postgres.dump"
test -s "${DEST}/postgres.dump"

echo "Archiving RustFS workspace objects..."
RUSTFS_ID="$("${COMPOSE[@]}" ps -q rustfs)"
if [[ -z "${RUSTFS_ID}" ]]; then
  echo "RustFS container is not running." >&2
  exit 1
fi
RUSTFS_VOLUME="$(docker inspect "${RUSTFS_ID}" --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}{{end}}{{end}}')"
if [[ -z "${RUSTFS_VOLUME}" ]]; then
  echo "Could not identify the RustFS data volume." >&2
  exit 1
fi
docker run --rm -v "${RUSTFS_VOLUME}:/source:ro" -v "${DEST}:/backup" alpine:3.20 \
  sh -c 'tar -czf /backup/rustfs-data.tar.gz -C /source .'
test -s "${DEST}/rustfs-data.tar.gz"

(
  cd "${DEST}"
  sha256sum postgres.dump rustfs-data.tar.gz > SHA256SUMS
)
chmod 600 "${DEST}"/*
echo "Backup completed: ${DEST}"
echo "Copy this directory off the EC2 host and test restores regularly."
