# Code Break

Code Break is a browser-based collaborative coding workspace. It combines a React/TypeScript editor UI with a Spring Boot API, JWT authentication, project membership, file/folder management, chat, WebSocket collaboration, and workspace persistence backed by PostgreSQL, Redis, and MinIO.

## Stack

- **Frontend:** React 19, TypeScript, Vite, Monaco Editor, STOMP/WebSocket
- **Backend:** Java 21, Spring Boot, Spring Security, JWT, Spring Data JPA
- **Storage:** PostgreSQL + Flyway migrations, Redis, MinIO
- **Checks:** Maven tests, TypeScript production build, ESLint, Docker Compose config validation

## Local development

### 1. Start infrastructure

    cd backend
    cp .env.example .env

Edit .env before starting services. Replace all placeholder credentials, set a random JWT_SECRET of at least 32 bytes, keep DATABASE_PASSWORD equal to POSTGRES_PASSWORD, and keep MINIO_ACCESS_KEY / MINIO_SECRET_KEY aligned with the MinIO root credentials.

    docker compose up -d

The dependency ports are bound to 127.0.0.1 only.

### Run the entire stack in Docker

After editing `.env` as described above, run this from `backend/`:

    docker compose -f docker-compose.yml -f docker-compose.app.yml up --build

Open http://localhost:5173. The frontend container serves the app and proxies `/api` and `/ws` to the backend. The frontend port and infrastructure ports are bound to localhost only. To stop the stack, press Ctrl+C and run:

    docker compose -f docker-compose.yml -f docker-compose.app.yml down

### 2. Start the backend

From backend/, with Java 21 installed:

    ./mvnw spring-boot:run

The API defaults to http://localhost:8088/api. Database migrations are managed by Flyway; Hibernate schema mode defaults to validate.

### 3. Start the frontend

In a separate terminal:

    cd frontend
    cp .env.example .env
    pnpm install --frozen-lockfile
    pnpm run dev

The frontend defaults to http://localhost:5173 and connects to the local API and WebSocket endpoint. Adjust VITE_API_URL and VITE_WS_URL for a different backend address.

More detailed backend instructions: [backend/README.md](backend/README.md).

## Validation

From the repository root:

    cd backend && ./mvnw -B test
    cd ../frontend && pnpm install --frozen-lockfile && pnpm run lint && pnpm run build

CI validates the Compose configuration, builds and starts the full Docker stack, smoke-tests registration/login, authenticated project creation, and workspace file save/read, then runs backend tests and frontend lint/build.

## Security and deployment notes

- Never commit .env files or use example placeholders as real credentials.
- Configure exact allowed browser origins using CORS_ALLOWED_ORIGINS and WEBSOCKET_ALLOWED_ORIGINS.
- The supplied Compose file is a **local-development dependency stack**, not a complete internet-facing production deployment. Use a TLS-terminating reverse proxy, managed secrets, and infrastructure/network policies for deployment.
- Treat JWTs as credentials. Do not log or share them.
- Local workspace scanning excludes `.env`, environment-specific `.env.*` files, common private keys and credential files by default; safe templates such as `.env.example` can be shared.
