import { API_URL } from "../config";

export interface WorkspaceSnapshotSummary {
    id: number;
    filePath: string;
    createdBy: string;
    message: string | null;
    createdAt: string;
    contentLength: number;
}

export interface WorkspaceSnapshotDetail {
    id: number;
    path: string;
    content: string;
    createdBy: string;
    message: string;
    createdAt: string;
}

async function request<T>(url: string, token: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(url, {
        ...init,
        headers: {
            Authorization: `Bearer ${token}`,
            ...(init.body ? { "Content-Type": "application/json" } : {}),
            ...init.headers,
        },
    });
    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || `Request failed (${response.status})`);
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
}

const base = (projectId: number) => `${API_URL}/api/projects/${projectId}/history`;

export function listSnapshots(projectId: number, path: string, token: string) {
    return request<WorkspaceSnapshotSummary[]>(
        `${base(projectId)}?path=${encodeURIComponent(path)}`, token
    );
}

export function createSnapshot(projectId: number, path: string, token: string, message: string) {
    return request<WorkspaceSnapshotSummary>(
        `${base(projectId)}?path=${encodeURIComponent(path)}`, token,
        { method: "POST", body: JSON.stringify({ message }) }
    );
}

export function getSnapshot(projectId: number, snapshotId: number, token: string) {
    return request<WorkspaceSnapshotDetail>(`${base(projectId)}/${snapshotId}`, token);
}

export function restoreSnapshot(projectId: number, snapshotId: number, token: string) {
    return request<{ restored: boolean; snapshot: WorkspaceSnapshotSummary }>(
        `${base(projectId)}/${snapshotId}/restore`, token, { method: "POST" }
    );
}
