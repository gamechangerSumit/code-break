# Production deployment

This Compose profile is intended for a **single Linux host with a public DNS name**. Caddy terminates HTTPS and proxies browser traffic to the frontend container; the frontend Nginx configuration forwards `/api` and `/ws` to the backend. PostgreSQL, Redis, MinIO, and the backend remain on the private Compose network. The existing localhost-only ports for the frontend and data services are not public ingress.

## Prerequisites

- A Linux host with Docker Engine and the Docker Compose plugin.
- A DNS A record (and AAAA record only if IPv6 is configured) for the app hostname pointing to the host.
- Inbound TCP 80/443 and UDP 443 allowed by the host/cloud firewall. Caddy uses these ports to obtain and renew certificates.
- Persistent disk space for PostgreSQL, Redis, MinIO, and Caddy volumes.

## Configure secrets

From `backend/`:

```sh
cp .env.example .env
```

Edit `.env` and set all credentials to unique, strong values. In particular:

- `APP_DOMAIN`: public hostname only, e.g. `codebreak.example.com` (no scheme or path).
- `APP_ORIGIN`: `https://` followed by the exact public hostname.
- `CORS_ALLOWED_ORIGINS` and `WEBSOCKET_ALLOWED_ORIGINS`: the same exact HTTPS origin.
- `VITE_WS_URL`: `wss://` followed by the hostname and `/ws`.
- `JWT_SECRET`: cryptographically random value of at least 32 bytes.
- `POSTGRES_PASSWORD`, `DATABASE_PASSWORD`: identical strong database password.
- `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` and `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`: keep the root and application credentials aligned as required by the Compose configuration.

Do not use the example values in production. Keep `.env` out of version control and restrict access to it (for example, `chmod 600 .env`). Set `ACME_EMAIL` to an address monitored by the operator for certificate notices.

## Deploy

Run from `backend/` after DNS and firewall configuration:

```sh
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.prod.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.prod.yml up --build -d
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.prod.yml ps
```

Open `https://$APP_DOMAIN`. Verify registration/login, create a project, create and edit a file, refresh and confirm persistence, then join from a second account and verify viewer write restrictions. Confirm the browser WebSocket connects over `wss://`.

To update:

```sh
git pull
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.prod.yml up --build -d
```

## Operations and recovery

- Back up PostgreSQL with `pg_dump` and back up the MinIO data volume/object bucket on a schedule. Redis is a cache/dirty-state component; do not treat it as the durable workspace backup.
- Store backups off-host and periodically test restoration. A volume on the same host is not a backup.
- Monitor `docker compose ... ps`, container logs, disk usage, certificate renewal, and the application health endpoint at `/actuator/health` from inside the backend network.
- Apply OS/Docker security updates and rotate credentials if exposed.
- Do not expose PostgreSQL, Redis, MinIO console/API, or the backend port directly to the internet. Use a managed database/object store and an explicit network policy for multi-host deployments.

## Deployment boundary

This repository change provides a repeatable single-host HTTPS deployment path. It does **not** create a cloud account, provision a server, configure DNS/firewall rules, inject production secrets, or perform the first live deployment. Those actions require the target hosting provider, domain, and operator-controlled credentials. For a managed platform or multi-instance deployment, adapt the infrastructure and storage architecture rather than treating this Compose profile as high availability.
