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

const API_URL =
    "http://localhost:8088/api";


// =========================================================
// AUTH HEADERS
// =========================================================

function authHeaders(
    token: string
): HeadersInit {

    return {
        Authorization:
            `Bearer ${token}`,

        "Content-Type":
            "application/json",
    };
}


// =========================================================
// RESPONSE HANDLER
// =========================================================

async function handleResponse(
    response: Response
): Promise<Response> {

    if (response.ok) {
        return response;
    }

    let message =
        `Request failed (${response.status})`;

    try {

        const text =
            await response.text();

        if (text) {
            message = text;
        }

    } catch {
        // Keep default error.
    }

    throw new Error(message);
}


// =========================================================
// GET WORKSPACE
// =========================================================

export async function getWorkspace(
    projectId: number,
    token: string
): Promise<WorkspaceSnapshot> {

    const response =
        await fetch(
            `${API_URL}/projects/${projectId}/workspace`,
            {
                method: "GET",

                headers:
                    authHeaders(token),
            }
        );

    await handleResponse(
        response
    );

    return response.json();
}


// =========================================================
// GET FILE CONTENT
// =========================================================

export async function getWorkspaceFileContent(
    projectId: number,
    path: string,
    token: string
): Promise<string> {

    const params =
        new URLSearchParams({
            path,
        });

    const response =
        await fetch(
            `${API_URL}/projects/${projectId}/workspace/file?${params.toString()}`,
            {
                method: "GET",

                headers: {
                    Authorization:
                        `Bearer ${token}`,
                },
            }
        );

    await handleResponse(
        response
    );

    return response.text();
}


// =========================================================
// SAVE FILE
// =========================================================

export async function saveWorkspaceFile(
    projectId: number,
    request: WorkspaceFileSaveRequest,
    token: string
): Promise<WorkspaceFileMetadata> {

    const response =
        await fetch(
            `${API_URL}/projects/${projectId}/workspace/file`,
            {
                method: "PUT",

                headers:
                    authHeaders(token),

                body:
                    JSON.stringify(
                        request
                    ),
            }
        );

    await handleResponse(
        response
    );

    return response.json();
}


// =========================================================
// DELETE FILE
// =========================================================

export async function deleteWorkspaceFileApi(
    projectId: number,
    path: string,
    token: string
): Promise<void> {

    const params =
        new URLSearchParams({
            path,
        });

    const response =
        await fetch(
            `${API_URL}/projects/${projectId}/workspace/file?${params.toString()}`,
            {
                method: "DELETE",

                headers: {
                    Authorization:
                        `Bearer ${token}`,
                },
            }
        );

    await handleResponse(
        response
    );
}


// =========================================================
// CREATE FOLDER
// =========================================================

export async function createWorkspaceFolderApi(
    projectId: number,
    path: string,
    token: string
): Promise<WorkspaceFolderMetadata> {

    const response =
        await fetch(
            `${API_URL}/projects/${projectId}/workspace/folder`,
            {
                method: "POST",

                headers:
                    authHeaders(token),

                body:
                    JSON.stringify({
                        path,
                    }),
            }
        );

    await handleResponse(
        response
    );

    return response.json();
}


// =========================================================
// DELETE FOLDER
// =========================================================

export async function deleteWorkspaceFolderApi(
    projectId: number,
    path: string,
    token: string
): Promise<void> {

    const params =
        new URLSearchParams({
            path,
        });

    const response =
        await fetch(
            `${API_URL}/projects/${projectId}/workspace/folder?${params.toString()}`,
            {
                method: "DELETE",

                headers: {
                    Authorization:
                        `Bearer ${token}`,
                },
            }
        );

    await handleResponse(
        response
    );
}


// =========================================================
// IMPORT COMPLETE WORKSPACE
//
// Used by OWNER when a local folder is opened.
//
// This sends the complete workspace through REST
// instead of putting a potentially huge folder tree
// inside a STOMP message.
// =========================================================

export async function importWorkspace(
    projectId: number,
    request: WorkspaceImportRequest,
    token: string
): Promise<WorkspaceSnapshot> {

    const response =
        await fetch(
            `${API_URL}/projects/${projectId}/workspace/import`,
            {
                method: "POST",

                headers:
                    authHeaders(token),

                body:
                    JSON.stringify(
                        request
                    ),
            }
        );

    await handleResponse(
        response
    );

    return response.json();
}