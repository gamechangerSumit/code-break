import {
    authHeaders,
    request,
    requestJson,
} from "../api/http";

export interface Project {
    id: number;
    name: string;
    description: string | null;
    joinCode: string | null;
}

export type ProjectRole =
    | "OWNER"
    | "EDITOR"
    | "VIEWER";

export interface ProjectMember {
    id: number;
    userId: number;
    username: string;
    role: ProjectRole;
}

export async function getProjects(
    token: string
): Promise<Project[]> {
    return requestJson<Project[]>(
        "/projects",
        {
            headers: authHeaders(token, false),
        }
    );
}

export async function createProject(
    name: string,
    description: string,
    token: string
): Promise<Project> {
    return requestJson<Project>(
        "/projects",
        {
            method: "POST",
            headers: authHeaders(token),
            body: JSON.stringify({
                name,
                description,
            }),
        }
    );
}

export async function deleteProject(
    projectId: number,
    token: string
): Promise<void> {
    await request(
        `/projects/${projectId}`,
        {
            method: "DELETE",
            headers: authHeaders(token, false),
        }
    );
}

export async function joinProject(
    joinCode: string,
    token: string
): Promise<ProjectMember> {
    return requestJson<ProjectMember>(
        "/projects/join",
        {
            method: "POST",
            headers: authHeaders(token),
            body: JSON.stringify({
                joinCode,
            }),
        }
    );
}

export async function getProjectMembers(
    projectId: number,
    token: string
): Promise<ProjectMember[]> {
    return requestJson<ProjectMember[]>(
        `/projects/${projectId}/members`,
        {
            headers: authHeaders(token, false),
        }
    );
}

export async function changeMemberRole(
    projectId: number,
    memberId: number,
    role: ProjectRole,
    token: string
): Promise<ProjectMember> {
    return requestJson<ProjectMember>(
        `/projects/${projectId}/members/${memberId}/role?role=${encodeURIComponent(role)}`,
        {
            method: "PUT",
            headers: authHeaders(token, false),
        }
    );
}
