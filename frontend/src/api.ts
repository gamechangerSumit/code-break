import {
    authHeaders,
    request,
    requestJson,
} from "./api/http";

export interface ProjectFile {
    id: number;
    name: string;
    path: string;
    content: string;
    createdAt?: string;
    updatedAt?: string;
}

export async function getProjectFiles(
    projectId: number,
    token: string
): Promise<ProjectFile[]> {
    return requestJson<ProjectFile[]>(
        `/projects/${projectId}/files`,
        {
            headers: authHeaders(token, false),
        }
    );
}

export async function createProjectFile(
    projectId: number,
    name: string,
    path: string,
    content: string,
    token: string
): Promise<ProjectFile> {
    return requestJson<ProjectFile>(
        `/projects/${projectId}/files`,
        {
            method: "POST",
            headers: authHeaders(token),
            body: JSON.stringify({
                name,
                path,
                content,
            }),
        }
    );
}

export async function updateProjectFile(
    projectId: number,
    fileId: number,
    content: string,
    token: string
): Promise<ProjectFile> {
    return requestJson<ProjectFile>(
        `/projects/${projectId}/files/${fileId}`,
        {
            method: "PUT",
            headers: authHeaders(token),
            body: JSON.stringify({ content }),
        }
    );
}

export async function deleteProjectFile(
    projectId: number,
    fileId: number,
    token: string
): Promise<void> {
    await request(
        `/projects/${projectId}/files/${fileId}`,
        {
            method: "DELETE",
            headers: authHeaders(token, false),
        }
    );
}
