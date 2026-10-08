/**
 * Central runtime configuration.
 *
 * Previously every file (api.ts, authApi.ts, projectApi.ts,
 * workspaceApi.ts, useCollaboration.ts, MembersPanel.tsx) hardcoded
 * "http://localhost:8088" / "ws://localhost:8088/ws" on its own.
 * That meant a production build could never point at a real backend
 * without editing half the codebase.
 *
 * Now every one of those files imports API_URL / WS_URL from here,
 * and this file reads them from Vite env vars (falling back to
 * localhost for local dev).
 *
 * Add a `.env` (or `.env.production`) file next to package.json:
 *
 *   VITE_API_URL=https://api.your-domain.com
 *   VITE_WS_URL=wss://api.your-domain.com/ws
 *
 * See .env.example for a template.
 */

export const API_URL: string =
    import.meta.env.VITE_API_URL ??
    "http://localhost:8088";

export const WS_URL: string =
    import.meta.env.VITE_WS_URL ??
    "ws://localhost:8088/ws";