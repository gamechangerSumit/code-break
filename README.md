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

## Product roadmap

See [ROADMAP.md](ROADMAP.md) for the current feature inventory, live-collaboration additions, and prioritized next steps. Roadmap items are explicitly marked as not yet shipped.

## Validation

The GitHub Actions workflow runs frontend dependency installation, ESLint, the production Vite build, and backend Maven tests (with PostgreSQL).

```bash
cd frontend && pnpm install --frozen-lockfile && pnpm lint && pnpm build
cd ../backend && ./mvnw -B test
```

## Deploy to AWS EC2 (Ubuntu)

The repository includes a production-oriented Compose stack, private service networks, persistent volumes, Caddy automatic HTTPS, and EC2 helper scripts.

### 1. Prepare AWS

- Launch an Ubuntu 22.04/24.04 EC2 instance. For a small test deployment, start with at least 2 vCPU and 4 GB RAM; increase this for concurrent rooms/build workloads.
- Attach a persistent EBS volume sized for your database and workspace files. Monitor free space and configure snapshots/backups.
- Assign an Elastic IP.
- Configure the EC2 security group inbound rules:
  - TCP 22 only from your own public IP.
  - TCP 80 from the internet (HTTP and ACME validation).
  - TCP 443 from the internet (HTTPS).
- Do not open ports 5432, 6379, 8088, 9000, or 9001 to the internet. Compose does not publish these ports on the host.

### 2. Point your domain

Create an A record for your app hostname (for example `codebreak.example.com`) pointing to the EC2 Elastic IP. Caddy obtains and renews the TLS certificate automatically once DNS resolves and ports 80/443 are reachable.

### 3. Install Docker on EC2

SSH into the instance, clone the repository, and install Docker Engine plus the Compose plugin:

```bash
git clone https://github.com/gamechangerSumit/code-break.git
cd code-break
sudo bash deploy/ec2-bootstrap.sh
```

Log out and SSH back in so your user receives the Docker group membership. Then continue from the repository directory.

### 4. Deploy

```bash
DOMAIN=codebreak.example.com bash deploy/ec2-deploy.sh
```

The deployment script creates `backend/.env` if missing, generates strong random database/JWT/object-storage secrets, restricts the file to the current user, validates Compose, and builds/starts the stack. Keep `backend/.env` private and backed up securely; never commit it.

### 5. Verify and operate

```bash
docker compose --env-file backend/.env -f backend/docker-compose.yml ps
docker compose --env-file backend/.env -f backend/docker-compose.yml logs -f
```

Open `https://codebreak.example.com`. To deploy later updates:

```bash
git pull --ff-only origin main
DOMAIN=codebreak.example.com bash deploy/ec2-deploy.sh
```

To stop containers without deleting data:

```bash
docker compose --env-file backend/.env -f backend/docker-compose.yml down
```

Never use `down -v` on a live deployment unless you intentionally want to delete the persistent database, Redis, object-storage, and Caddy volumes.

### Production operations

This is a single-EC2 deployment baseline, not a highly available architecture. Configure automated EBS snapshots and application-level PostgreSQL/object-storage backups, test restores, monitor disk/RAM/CPU, and keep the OS and images patched. For higher availability, move PostgreSQL and object storage to managed/redundant services, add centralized secrets management and monitoring, and scale the application behind a load balancer. Review database migration strategy before changing `SPRING_JPA_HIBERNATE_DDL_AUTO` from its current default.


### Optional platform capabilities

The project tools panel includes workspace history and side-by-side version comparison, project activity, invitations, AI assistance, sandboxed code execution, sandboxed HTML preview, GitHub repository import/push, and peer-to-peer calls. Monaco documents synchronize through a dedicated Yjs/Hocuspocus CRDT service with Redis-backed state persistence; the previous STOMP edit path remains a fallback while a CRDT session connects.

Configure optional integrations in `backend/.env` before starting the stack:

- `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL`: AI coding assistant. The provider key stays on the backend.
- `CODE_RUNNER_URL`, `CODE_RUNNER_API_KEY`: private Judge0-compatible runner. Keep the runner on a private network; the backend applies language allowlisting and per-submission CPU/memory/time limits. No public code runner is bundled.
- `GITHUB_TOKEN_ENCRYPTION_KEY`: generate with `openssl rand -base64 32`. GitHub personal access tokens are encrypted at rest with AES-256-GCM. Use fine-grained tokens with minimum required scopes.
- `GRAFANA_ADMIN_PASSWORD`: generated automatically by the EC2 deployment script if left as the example placeholder.

### Monitoring and backups

The EC2 deployment script enables the optional `monitoring` and `backup` Compose profiles. Prometheus scrapes the backend's internal Actuator endpoint. Grafana and Prometheus bind only to EC2 loopback ports 3000 and 9090; access them through an SSH tunnel, for example:

```bash
ssh -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090 ubuntu@EC2_PUBLIC_IP
```

The backup service runs daily, keeps 14 days of PostgreSQL custom-format dumps, and copies RustFS bucket objects to a persistent backup volume. Backups on the same EC2/EBS failure domain are not disaster recovery: configure encrypted EBS snapshots or copy backups to a separate AWS account/region, and test restores. A PostgreSQL restore helper is available at `deploy/backup/restore-postgres.sh`.

This deployment remains a single-node baseline. It can be vertically resized, but it is not yet horizontally scalable or highly available: the current STOMP simple broker and JVM collaboration locks are single-instance, and the Yjs service currently uses one Hocuspocus instance with Redis-backed persistence rather than a multi-node pub/sub topology. Do not add backend replicas until a shared STOMP broker, shared session/state design, Yjs server fan-out/sharding, and multi-instance integration tests are complete. For production growth, use an ALB, multiple stateless app instances, a managed PostgreSQL service, Redis with HA, redundant object storage, a shared message broker, and a TURN service for WebRTC.

### Current capability boundaries

- The integrated terminal/interactive PTY is not included yet. Code execution is a separate, bounded Judge0-compatible submission flow; configure a private runner before enabling it.
- WebRTC calling uses STUN by default. Configure a TURN relay for reliable connectivity across symmetric NATs and restrictive enterprise networks.
- Backups are local to the EC2/EBS deployment by default. Add off-host encrypted copies and routinely test recovery before treating them as disaster recovery.
