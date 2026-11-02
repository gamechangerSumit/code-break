#!/usr/bin/env bash
set -Eeuo pipefail
if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/postgres-YYYYMMDDTHHMMSSZ.dump"
  exit 2
fi
if [[ ! -f "$1" ]]; then
  echo "Backup file not found: $1"
  exit 2
fi
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
set -a
source backend/.env
set +a
echo "WARNING: this restores into the existing codebreak database and may overwrite current data."
read -r -p "Type RESTORE to continue: " confirm
[[ "$confirm" == "RESTORE" ]] || exit 1
docker compose --env-file backend/.env -f backend/docker-compose.yml exec -T postgres \
  pg_restore -U postgres -d codebreak --clean --if-exists < "$1"
