# Production operations

## Health checks

- Caddy serves the application on TCP 80/443.
- Backend readiness is exposed internally at `/actuator/health/readiness`.
- PostgreSQL and Redis use Docker health checks.
- The backend and frontend containers are not published directly to the EC2 host.

## Backups

Run from the repository root on the EC2 instance:

```bash
bash deploy/backup.sh
```

The script pauses backend writes during capture (the site may briefly return 502), then creates a timestamped, permission-restricted directory under `backups/` containing:
- A PostgreSQL custom-format dump.
- A compressed archive of the RustFS object data volume.
- A compressed archive of Redis persisted workspace state.
- SHA-256 checksums.

Backups on the same EC2/EBS volume are not disaster recovery. Copy completed backup directories to a separate encrypted S3 bucket or another account/region, apply retention, and periodically test restores. For example, after configuring an IAM instance role with write access to a dedicated bucket: `aws s3 sync backups/ s3://YOUR-BACKUP-BUCKET/code-break/ --sse AES256`.

## PostgreSQL restore

Take a fresh backup first. Restore is destructive and requires an explicit confirmation:

```bash
CONFIRM_DATABASE_RESTORE=yes bash deploy/restore-postgres.sh /absolute/path/to/postgres.dump
```

The restore script stops the backend during restore and restarts it afterward. Restore the matching RustFS archive to its data volume only during a planned maintenance window; do not overwrite a running object store.

## Safe updates

```bash
git pull --ff-only origin main
DOMAIN=codebreak.yourdomain.com bash deploy/ec2-deploy.sh
```

Review release notes and backup before deploying database schema changes. Keep `backend/.env` out of Git and restrict access to it.
