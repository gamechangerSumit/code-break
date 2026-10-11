# Code Break frontend

The frontend is a React 19 + TypeScript single-page application built with Vite. Monaco Editor provides the coding surface; STOMP/WebSocket connects the UI to real-time project collaboration.

## Requirements

- Node.js 22
- pnpm 10
- A running Code Break backend and its PostgreSQL, Redis and MinIO dependencies

## Configure

Copy the environment template:

    cp .env.example .env

Defaults target a local backend:

- VITE_API_URL=http://localhost:8088/api
- VITE_WS_URL=ws://localhost:8088/ws

Set both values to the corresponding API and WebSocket endpoints for your environment. Use wss:// for WebSockets over HTTPS.

## Develop

    pnpm install --frozen-lockfile
    pnpm run dev

Vite serves the app at http://localhost:5173 by default.

## Quality checks

    pnpm run lint
    pnpm run build

The build runs TypeScript project checks before producing the optimized Vite bundle. CI runs both lint and build checks.

## Browser note

Local workspace folder access uses the File System Access API, which is best supported by Chromium-based browsers such as Chrome and Edge. Browser permissions are granted by the user when selecting a folder.
