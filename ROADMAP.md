# Code Break — Product Roadmap

This document separates existing functionality from features that still require implementation. A roadmap item is not considered shipped merely because it is listed here.

## Available in the current codebase

- Account registration and login with JWT authentication.
- Project dashboard, project creation/joining, membership and OWNER / EDITOR / VIEWER roles.
- Shared workspace files and folders, workspace import/export, autosave and workspace synchronization.
- Monaco editor with real-time code updates.
- Project room chat.
- PostgreSQL persistence, Redis workspace state, S3-compatible object storage and Docker Compose deployment baseline.

## Added in the live collaboration PR

- Live collaborator cursors and selections in Monaco.
- Per-user cursor colours and labels.
- Project presence snapshots and online collaborator count.
- Presence cleanup when a WebSocket session leaves or disconnects.
- WebSocket JWT enforcement and project membership checks for private project topic subscriptions.
- File-extension-based editor language selection and improved Monaco editing assistance.

## Next implementation priorities

### P0 — Collaboration correctness and reliability

- Replace the current best-effort concurrent edit handling with a CRDT/OT document model (prefer Yjs) so simultaneous edits cannot overwrite one another.
- Persist collaboration documents and recover state after reconnect/restart.
- Move presence and fan-out to shared Redis-backed state for multi-instance deployments.
- Add integration tests for multiple users editing the same file, reconnecting, switching files and losing network connectivity.

### P1 — Core coding workspace

- Durable file version history, named snapshots, diff viewer and restore.
- Multi-tab editor, split editor groups, breadcrumbs and command palette.
- Integrated terminal and task runner.
- Secure code execution in a separate sandbox service with CPU, memory, time, filesystem and network limits.
- Test-case input/output panel and execution history.
- HTML/CSS/JS live preview with isolated iframe sandboxing.

### P1 — Team workflows

- Invitations by email/link, revocation and expiry.
- Activity feed and in-app notifications.
- Comments and annotations on files/lines.
- Project templates, starter workspaces and downloadable archives.

### P2 — Optional integrations

- AI code explanation, debugging, test generation and refactoring (requires an AI provider and user-configured credentials).
- Voice/video rooms and screen sharing (requires a real-time media provider such as LiveKit or equivalent).
- Git provider integration, branch/commit/pull-request workflows (requires OAuth app setup).
- External issue tracker and deployment integrations.

### P2 — Production operations

- Database migrations with a controlled production migration policy.
- Automated backups and restore drills for PostgreSQL and object storage.
- Metrics, structured logs, tracing, alerting and health/readiness probes.
- Rate limits, abuse controls, audit events, session revocation and security headers.
- Load tests for WebSocket rooms and code execution.
- Multi-node deployment, TLS ingress, secrets management and disaster-recovery runbooks.

## Definition of done

A feature is complete only when its UI, backend authorization, persistence/recovery behaviour, failure states, automated tests and deployment configuration are implemented. External integrations are complete only after credentials and service configuration are supplied and verified in a real environment.
