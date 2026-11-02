#!/bin/sh
set -eu

: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"
: "${RUSTFS_ACCESS_KEY:?RUSTFS_ACCESS_KEY is required}"
: "${RUSTFS_SECRET_KEY:?RUSTFS_SECRET_KEY is required}"

export PGPASSWORD="$POSTGRES_PASSWORD"
export RCLONE_CONFIG_S3_TYPE=s3
export RCLONE_CONFIG_S3_PROVIDER=Minio
export RCLONE_CONFIG_S3_ENV_AUTH=false
export RCLONE_CONFIG_S3_ACCESS_KEY_ID="$RUSTFS_ACCESS_KEY"
export RCLONE_CONFIG_S3_SECRET_ACCESS_KEY="$RUSTFS_SECRET_KEY"
export RCLONE_CONFIG_S3_ENDPOINT=http://rustfs:9000
export RCLONE_CONFIG_S3_REGION=us-east-1

while true; do
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  tmp="/backups/postgres-${stamp}.dump.tmp"
  echo "[backup] Starting PostgreSQL and RustFS backup at ${stamp}"
  if pg_dump -h postgres -U postgres -d codebreak -Fc > "$tmp"; then
    mv "$tmp" "/backups/postgres-${stamp}.dump"
  else
    rm -f "$tmp"
    echo "[backup] PostgreSQL dump failed" >&2
  fi

  if rclone copy s3:codebreak /backups/rustfs --transfers 4; then
    echo "[backup] RustFS object copy completed"
  else
    echo "[backup] RustFS object copy failed" >&2
  fi

  if [ -n "${BACKUP_S3_BUCKET:-}" ]; then
    export RCLONE_CONFIG_ARCHIVE_TYPE=s3
    export RCLONE_CONFIG_ARCHIVE_PROVIDER=AWS
    export RCLONE_CONFIG_ARCHIVE_ENV_AUTH=true
    export RCLONE_CONFIG_ARCHIVE_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
    if rclone copy /backups "archive:${BACKUP_S3_BUCKET}/codebreak" --transfers 4; then
      echo "[backup] Off-host S3 copy completed"
    else
      echo "[backup] Off-host S3 copy failed" >&2
    fi
  fi

  find /backups -type f -name 'postgres-*.dump' -mtime +14 -delete
  echo "[backup] Cycle finished; next run in 24 hours"
  sleep 86400
done
