const API_URL = "http://localhost:8088";

export interface ProjectFile {
    id: number;
    name: string;
    path: string;
    content: string;
    createdAt?: string;
    updatedAt?: string;
}


// ========================================
// GET PROJECT FILES
// ========================================

export async function getProjectFiles(
    projectId: number,
    token: string
): Promise<ProjectFile[]> {

    const response = await fetch(
        `${API_URL}/api/projects/${projectId}/files`,
        {
            method: "GET",
            headers: {
                Authorization: `Bearer ${token}`,
            },
        }
    );

    if (!response.ok) {
        throw new Error(
            `Failed to fetch project files: ${response.status}`
        );
    }

    return response.json();
}


// ========================================
// CREATE FILE
// ========================================

export async function createProjectFile(
    projectId: number,
    name: string,
    path: string,
    content: string,
    token: string
): Promise<ProjectFile> {

    const response = await fetch(
        `${API_URL}/api/projects/${projectId}/files`,
        {
            method: "POST",

            headers: {
                "Content-Type": "application/json",
                Authorization: `Bearer ${token}`,
            },

            body: JSON.stringify({
                name,
                path,
                content,
            }),
        }
    );

    if (!response.ok) {
        throw new Error(
            `Failed to create file: ${response.status}`
        );
    }

    return response.json();
}


// ========================================
// UPDATE / SAVE FILE
// ========================================

export async function updateProjectFile(
    projectId: number,
    fileId: number,
    content: string,
    token: string
): Promise<ProjectFile> {

    const response = await fetch(
        `${API_URL}/api/projects/${projectId}/files/${fileId}`,
        {
            method: "PUT",

            headers: {
                "Content-Type": "application/json",
                Authorization: `Bearer ${token}`,
            },

            body: JSON.stringify({
                content,
            }),
        }
    );

    if (!response.ok) {
        throw new Error(
            `Failed to save file: ${response.status}`
        );
    }

    return response.json();
}


// ========================================
// DELETE FILE
// ========================================

export async function deleteProjectFile(
    projectId: number,
    fileId: number,
    token: string
): Promise<void> {

    const response = await fetch(
        `${API_URL}/api/projects/${projectId}/files/${fileId}`,
        {
            method: "DELETE",

            headers: {
                Authorization: `Bearer ${token}`,
            },
        }
    );

    if (!response.ok) {
        throw new Error(
            `Failed to delete file: ${response.status}`
        );
    }
}