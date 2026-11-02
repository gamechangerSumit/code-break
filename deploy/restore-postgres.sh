#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /absolute/path/to/postgres.dump" >&2
  exit 2
fi
DUMP_FILE="$(realpath "$1")"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"
ENV_FILE="${ENV_FILE:-backend/.env}"
COMPOSE=(docker compose --env-file "${ENV_FILE}" -f backend/docker-compose.yml)

if [[ ! -s "${DUMP_FILE}" ]]; then
  echo "Backup file is missing or empty." >&2
  exit 1
fi
if [[ "${CONFIRM_DATABASE_RESTORE:-}" != "yes" ]]; then
  echo "This replaces the live Code Break database."
  echo "Take a fresh backup, then rerun with CONFIRM_DATABASE_RESTORE=yes." >&2
  exit 1
fi

echo "Stopping backend to prevent writes during restore..."
"${COMPOSE[@]}" stop backend
trap '"${COMPOSE[@]}" start backend >/dev/null' EXIT

echo "Restoring PostgreSQL database..."
"${COMPOSE[@]}" exec -T postgres pg_restore -U postgres -d codebreak --clean --if-exists --no-owner < "${DUMP_FILE}"
echo "Database restore completed. Backend will be restarted."
