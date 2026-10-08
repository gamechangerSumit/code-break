import {
    authHeaders,
    request,
    requestJson,
    requestText,
} from "../api/http";

export interface WorkspaceFileMetadata {
    path: string;
    name: string;
    size: number;
    version: number;
}

export interface WorkspaceFolderMetadata {
    path: string;
    name: string;
    parentPath: string | null;
}

export interface WorkspaceSnapshot {
    projectId: number;
    files: WorkspaceFileMetadata[];
    folders: WorkspaceFolderMetadata[];
}

export interface WorkspaceFileSaveRequest {
    path: string;
    content: string;
}

export interface WorkspaceImportFile {
    path: string;
    name: string;
    content: string;
}

export interface WorkspaceImportRequest {
    files: WorkspaceImportFile[];
    folders: string[];
}

export async function getWorkspace(
    projectId: number,
    token: string
): Promise<WorkspaceSnapshot> {
    return requestJson<WorkspaceSnapshot>(
        `/projects/${projectId}/workspace`,
        {
            headers: authHeaders(token, false),
        }
    );
}

export async function getWorkspaceFileContent(
    projectId: number,
    path: string,
    token: string
): Promise<string> {
    const params = new URLSearchParams({ path });

    return requestText(
        `/projects/${projectId}/workspace/file?${params.toString()}`,
        {
            headers: authHeaders(token, false),
        }
    );
}

export async function saveWorkspaceFile(
    projectId: number,
    requestBody: WorkspaceFileSaveRequest,
    token: string
): Promise<WorkspaceFileMetadata> {
    return requestJson<WorkspaceFileMetadata>(
        `/projects/${projectId}/workspace/file`,
        {
            method: "PUT",
            headers: authHeaders(token),
            body: JSON.stringify(requestBody),
        }
    );
}

export async function deleteWorkspaceFileApi(
    projectId: number,
    path: string,
    token: string
): Promise<void> {
    const params = new URLSearchParams({ path });

    await request(
        `/projects/${projectId}/workspace/file?${params.toString()}`,
        {
            method: "DELETE",
            headers: authHeaders(token, false),
        }
    );
}

export async function createWorkspaceFolderApi(
    projectId: number,
    path: string,
    token: string
): Promise<WorkspaceFolderMetadata> {
    return requestJson<WorkspaceFolderMetadata>(
        `/projects/${projectId}/workspace/folder`,
        {
            method: "POST",
            headers: authHeaders(token),
            body: JSON.stringify({ path }),
        }
    );
}

export async function deleteWorkspaceFolderApi(
    projectId: number,
    path: string,
    token: string
): Promise<void> {
    const params = new URLSearchParams({ path });

    await request(
        `/projects/${projectId}/workspace/folder?${params.toString()}`,
        {
            method: "DELETE",
            headers: authHeaders(token, false),
        }
    );
}

export async function importWorkspace(
    projectId: number,
    requestBody: WorkspaceImportRequest,
    token: string
): Promise<WorkspaceSnapshot> {
    return requestJson<WorkspaceSnapshot>(
        `/projects/${projectId}/workspace/import`,
        {
            method: "POST",
            headers: authHeaders(token),
            body: JSON.stringify(requestBody),
        }
    );
}
