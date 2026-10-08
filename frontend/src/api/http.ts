const DEFAULT_API_URL = "http://localhost:8088/api";

export const API_URL = (
    import.meta.env.VITE_API_URL?.trim() ||
    DEFAULT_API_URL
).replace(/\/+$/, "");

export class ApiError extends Error {
    readonly status: number;
    readonly code?: string;

    constructor(
        message: string,
        status: number,
        code?: string
    ) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.code = code;
    }
}

export function authHeaders(
    token: string,
    includeJson = true
): HeadersInit {
    const headers: Record<string, string> = {
        Authorization: `Bearer ${token}`,
    };

    if (includeJson) {
        headers["Content-Type"] = "application/json";
    }

    return headers;
}

async function readError(
    response: Response
): Promise<{ message: string; code?: string }> {
    const fallback = `Request failed (${response.status})`;

    try {
        const contentType =
            response.headers.get("content-type") ?? "";

        if (contentType.includes("application/json")) {
            const data: unknown = await response.json();

            if (
                typeof data === "object" &&
                data !== null
            ) {
                const record =
                    data as Record<string, unknown>;

                const message =
                    typeof record.message === "string"
                        ? record.message
                        : typeof record.error === "string"
                            ? record.error
                            : fallback;

                const code =
                    typeof record.code === "string"
                        ? record.code
                        : undefined;

                return { message, code };
            }
        }

        const text = await response.text();

        return {
            message: text.trim() || fallback,
        };
    } catch {
        return { message: fallback };
    }
}

export async function request(
    path: string,
    init: RequestInit = {}
): Promise<Response> {
    const controller = new AbortController();
    const timeout = window.setTimeout(
        () => controller.abort(),
        15_000
    );

    try {
        const response = await fetch(
            `${API_URL}${path.startsWith("/") ? path : `/${path}`}`,
            {
                ...init,
                signal:
                    init.signal
                        ? AbortSignal.any([
                            init.signal,
                            controller.signal,
                        ])
                        : controller.signal,
                headers: {
                    Accept: "application/json",
                    ...init.headers,
                },
            }
        );

        if (!response.ok) {
            const { message, code } =
                await readError(response);

            throw new ApiError(
                message,
                response.status,
                code
            );
        }

        return response;
    } catch (error) {
        if (
            error instanceof DOMException &&
            error.name === "AbortError"
        ) {
            throw new ApiError(
                "Request timed out. Please try again.",
                408,
                "REQUEST_TIMEOUT"
            );
        }

        if (error instanceof TypeError) {
            throw new ApiError(
                "Unable to reach the server. Check your connection and try again.",
                0,
                "NETWORK_ERROR"
            );
        }

        throw error;
    } finally {
        window.clearTimeout(timeout);
    }
}

export async function requestJson<T>(
    path: string,
    init: RequestInit = {}
): Promise<T> {
    const response = await request(
        path,
        init
    );

    return response.json() as Promise<T>;
}

export async function requestText(
    path: string,
    init: RequestInit = {}
): Promise<string> {
    const response = await request(
        path,
        init
    );

    return response.text();
}
