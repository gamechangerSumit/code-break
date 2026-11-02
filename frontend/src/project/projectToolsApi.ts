import { API_URL } from "../config";

const root = `${API_URL}/api`;
function headers(token: string, json = true): HeadersInit {
    return { Authorization: `Bearer ${token}`, ...(json ? { "Content-Type": "application/json" } : {}) };
}
async function parse<T>(response: Response): Promise<T> {
    if (!response.ok) throw new Error((await response.text()) || `Request failed (${response.status})`);
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
}
export interface WorkspaceVersion {
    id: number; filePath: string; versionNumber: number; createdBy: string;
    message: string; createdAt: string;
}
export interface ProjectActivity {
    id: number; actor: string; type: string; message: string; createdAt: string;
}
export async function listVersions(projectId: number, path: string, token: string) {
    const query = new URLSearchParams({ path });
    return parse<WorkspaceVersion[]>(await fetch(`${root}/projects/${projectId}/versions?${query}`, { headers: headers(token, false) }));
}
export async function createSnapshot(projectId: number, path: string, token: string) {
    return parse<WorkspaceVersion>(await fetch(`${root}/projects/${projectId}/versions`, {
        method: "POST", headers: headers(token), body: JSON.stringify({ path, message: "Manual snapshot" }),
    }));
}
export async function restoreVersion(projectId: number, versionId: number, token: string) {
    return parse<WorkspaceVersion>(await fetch(`${root}/projects/${projectId}/versions/${versionId}/restore`, {
        method: "POST", headers: headers(token),
    }));
}
export async function createProjectInvitation(projectId: number, token: string) {
    return parse<{ token: string; expiresAt: string }>(await fetch(`${root}/projects/${projectId}/invitations`, {
        method: "POST", headers: headers(token),
    }));
}
export async function listProjectActivity(projectId: number, token: string) {
    return parse<ProjectActivity[]>(await fetch(`${root}/projects/${projectId}/activity`, { headers: headers(token, false) }));
}

export async function requestAiAssistance(projectId: number, token: string, payload: { prompt: string; code?: string; filePath?: string }) {
    return parse<{ answer: string; model: string }>(await fetch(`${root}/ai/projects/${projectId}/assist`, {
        method: "POST", headers: headers(token), body: JSON.stringify(payload),
    }));
}

export interface ExecutionResult {
    stdout: string; stderr: string; compileOutput: string; message: string;
    status: string; time: string | number | null; memory: string | number | null;
    exitCode: number | null;
}
export async function executeProjectCode(projectId: number, token: string, payload: { language: string; sourceCode: string; stdin?: string }) {
    return parse<ExecutionResult>(await fetch(`${root}/projects/${projectId}/execute`, {
        method: "POST", headers: headers(token), body: JSON.stringify(payload),
    }));
}

export async function acceptProjectInvitation(tokenValue: string, token: string) {
    return parse<{ id: number; userId: number; username: string; role: string }>(await fetch(`${root}/invitations/accept`, {
        method: "POST", headers: headers(token), body: JSON.stringify({ token: tokenValue }),
    }));
}

export interface GithubRepository { fullName: string; url: string; isPrivate: boolean; defaultBranch: string; }
export async function connectGithub(tokenValue: string, token: string) {
    return parse<{ githubUsername: string; connectedAt: string }>(await fetch(`${root}/github/connect`, {
        method: "POST", headers: headers(token), body: JSON.stringify({ token: tokenValue }),
    }));
}
export async function listGithubRepositories(token: string) {
    return parse<GithubRepository[]>(await fetch(`${root}/github/repositories`, { headers: headers(token, false) }));
}
export async function disconnectGithub(token: string) {
    return parse<void>(await fetch(`${root}/github/connection`, { method: "DELETE", headers: headers(token, false) }));
}

export async function importGithubRepository(projectId: number, token: string, fullName: string, branch = "main") {
    return parse<{ repository: string; importedFiles: number; bytes: number }>(await fetch(`${root}/github/projects/${projectId}/import`, {
        method: "POST", headers: headers(token), body: JSON.stringify({ fullName, branch }),
    }));
}

export async function getGithubConnection(token: string) {
    return parse<{ githubUsername: string; connectedAt: string }>(await fetch(`${root}/github/connection`, {
        headers: headers(token, false),
    }));
}
