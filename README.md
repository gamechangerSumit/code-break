# Code Break

Code Break is a collaborative coding workspace with a React/TypeScript frontend and a Spring Boot backend. It includes project membership and roles, a Monaco-based editor, workspace file/folder management, real-time collaboration and chat, PostgreSQL persistence, Redis-backed workspace support, and RustFS S3-compatible object storage.

## Stack

- Frontend: React 19, TypeScript, Vite, Monaco Editor, STOMP/WebSocket
- Backend: Java 21, Spring Boot, Spring Security, JWT, Spring Data JPA
- Services: PostgreSQL 16, Redis 7, RustFS
- Runtime: Docker Compose (local full-stack deployment)

## Run the full stack with Docker

1. Copy `backend/.env.example` to `backend/.env`.
2. Replace every sample password/secret with unique random values. Keep the JWT secret at least 32 bytes.
3. From the `backend` directory, run:

   ```bash
   docker compose --env-file .env up --build -d
   ```

4. Open [http://localhost](http://localhost). The API is reverse-proxied under `/api`; WebSocket traffic is reverse-proxied under `/ws`.
5. Stop the stack with `docker compose --env-file .env down`. Add `-v` only if you intentionally want to delete persisted database/cache/object-storage volumes.

The Compose stack persists PostgreSQL, Redis, and RustFS data in named volumes. The sample credentials are for local development only; do not expose the database, Redis, or RustFS administration ports publicly.

## Run services separately for development

Start PostgreSQL, Redis, and RustFS using `docker compose up -d postgres redis rustfs` from `backend` (or run equivalent services yourself). Set the backend environment variables shown in `backend/.env.example`, then run:

```bash
cd backend
./mvnw spring-boot:run
```

For the frontend:

```bash
cd frontend
corepack enable
pnpm install --frozen-lockfile
pnpm dev
```

Vite serves the frontend at `http://localhost:5173`; the local API and WebSocket defaults point to port 8088. Optional frontend overrides are documented in `frontend/.env.example`.

## Validation

The GitHub Actions workflow runs frontend dependency installation, ESLint, the production Vite build, and backend Maven tests (with PostgreSQL).

```bash
cd frontend && pnpm install --frozen-lockfile && pnpm lint && pnpm build
cd ../backend && ./mvnw -B test
```

## Production deployment notes

The Compose stack is a single-node deployment baseline. RustFS single-node/single-disk mode is suitable for local testing and small workloads; production storage should use a multi-node topology with redundancy, TLS, monitoring, and tested backups. Put the frontend behind a TLS-enabled ingress/load balancer, configure a real domain, strong unique secrets, persistent storage, restricted network access, and an explicit CORS allowlist. Set `APP_CORS_ALLOWED_ORIGINS` to the exact browser origin(s). Do not commit real `.env` files or credentials. Review database migration strategy before setting `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` in production.
