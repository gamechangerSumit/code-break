/**
 * Runtime endpoints.
 * Production defaults use the same origin and let the reverse proxy route
 * API and WebSocket traffic to the backend. Local development uses port 8088.
 */
export const API_URL: string =
    import.meta.env.VITE_API_URL ??
    (import.meta.env.PROD ? "" : "http://localhost:8088");

export const WS_URL: string =
    import.meta.env.VITE_WS_URL ??
    (import.meta.env.PROD
        ? `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/ws`
        : "ws://localhost:8088/ws");

export const COLLABORATION_URL: string =
    import.meta.env.VITE_COLLABORATION_URL ??
    (import.meta.env.PROD
        ? `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/collaboration`
        : "ws://localhost:1234/collaboration");
