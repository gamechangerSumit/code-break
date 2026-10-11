# Code Break backend — local setup

## Prerequisites

- Java 21
- Docker Engine and Docker Compose v2
- Node.js 22 and pnpm 10 for the frontend

## Start local dependencies

1. Copy the environment template: `cp .env.example .env`.
2. Edit `.env` before starting anything. Set a strong random value for `POSTGRES_PASSWORD`, copy that same value to `DATABASE_PASSWORD`, and set `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`. Copy those MinIO values to `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`. Set `JWT_SECRET` to a cryptographically random value of at least 32 bytes.
3. Start the dependencies from this directory: `docker compose up -d`.
4. Wait for the Postgres, Redis and MinIO health checks to become healthy.
5. Start the backend from this directory with `./mvnw spring-boot:run`.
6. Start the frontend in `../frontend` with `pnpm install --frozen-lockfile` followed by `pnpm run dev`.

The Compose stack publishes dependency ports only on `127.0.0.1`; they are not intended to be exposed directly to the internet. The backend and frontend are run separately in this local-development workflow. The Compose file starts infrastructure dependencies only.

## Environment notes

- Do not commit your `.env` file or reuse local-development credentials in a deployed environment.
- Keep `POSTGRES_PASSWORD` and `DATABASE_PASSWORD` identical.
- Keep `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` and `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` aligned for the local setup.
- Set `CORS_ALLOWED_ORIGINS` and `WEBSOCKET_ALLOWED_ORIGINS` to the exact browser origins used by your deployment.
- `JPA_DDL_AUTO=validate` is intentional; schema changes are applied through Flyway migrations.
