const API_URL =
    "http://localhost:8088";


// =========================================
// PROJECT
// =========================================

export interface Project {
    id: number;
    name: string;
    description: string | null;
    joinCode: string | null;
}


// =========================================
// ROLE
// =========================================

export type ProjectRole =
    | "OWNER"
    | "EDITOR"
    | "VIEWER";


// =========================================
// MEMBER
// =========================================

export interface ProjectMember {
    id: number;
    userId: number;
    username: string;
    role: ProjectRole;
}


// =========================================
// ERROR
// =========================================

async function getErrorMessage(
    response: Response,
    fallback: string
): Promise<string> {

    try {

        const data =
            await response.json();

        if (
            data?.message
        ) {
            return data.message;
        }

        if (
            data?.error
        ) {
            return data.error;
        }

    } catch {
        // Ignore JSON parsing errors.
    }

    return fallback;
}


// =========================================
// GET PROJECTS
// =========================================

export async function getProjects(
    token: string
): Promise<Project[]> {

    const response =
        await fetch(
            `${API_URL}/api/projects`,
            {
                method: "GET",

                headers: {
                    Authorization:
                        `Bearer ${token}`,
                },
            }
        );

    if (!response.ok) {
        throw new Error(
            await getErrorMessage(
                response,
                "Failed to fetch projects."
            )
        );
    }

    return response.json();
}


// =========================================
// CREATE PROJECT
// =========================================

export async function createProject(
    name: string,
    description: string,
    token: string
): Promise<Project> {

    const response =
        await fetch(
            `${API_URL}/api/projects`,
            {
                method: "POST",

                headers: {
                    "Content-Type":
                        "application/json",

                    Authorization:
                        `Bearer ${token}`,
                },

                body:
                    JSON.stringify({
                        name,
                        description,
                    }),
            }
        );

    if (!response.ok) {
        throw new Error(
            await getErrorMessage(
                response,
                "Failed to create project."
            )
        );
    }

    return response.json();
}

// =========================================
// DELETE PROJECT
// OWNER ONLY
// =========================================

export async function deleteProject(
    projectId: number,
    token: string
): Promise<void> {

    const response =
        await fetch(
            `${API_URL}/api/projects/${projectId}`,
            {
                method: "DELETE",

                headers: {
                    Authorization:
                        `Bearer ${token}`,
                },
            }
        );


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Failed to delete project"
            )
        );

    }
}


// =========================================
// JOIN PROJECT
// =========================================

export async function joinProject(
    joinCode: string,
    token: string
): Promise<ProjectMember> {

    const response =
        await fetch(
            `${API_URL}/api/projects/join`,
            {
                method: "POST",

                headers: {
                    "Content-Type":
                        "application/json",

                    Authorization:
                        `Bearer ${token}`,
                },

                body:
                    JSON.stringify({
                        joinCode,
                    }),
            }
        );

    if (!response.ok) {
        throw new Error(
            await getErrorMessage(
                response,
                "Failed to join project."
            )
        );
    }

    return response.json();
}


// =========================================
// GET MEMBERS
// =========================================

export async function getProjectMembers(
    projectId: number,
    token: string
): Promise<ProjectMember[]> {

    const response =
        await fetch(
            `${API_URL}/api/projects/${projectId}/members`,
            {
                method: "GET",

                headers: {
                    Authorization:
                        `Bearer ${token}`,
                },
            }
        );

    if (!response.ok) {
        throw new Error(
            await getErrorMessage(
                response,
                "Failed to fetch project members."
            )
        );
    }

    return response.json();
}


// =========================================
// CHANGE MEMBER ROLE
// =========================================

export async function changeMemberRole(
    projectId: number,
    memberId: number,
    role: ProjectRole,
    token: string
): Promise<ProjectMember> {

    const response =
        await fetch(
            `${API_URL}/api/projects/${projectId}/members/${memberId}/role?role=${encodeURIComponent(role)}`,
            {
                method: "PUT",

                headers: {
                    Authorization:
                        `Bearer ${token}`,
                },
            }
        );

    if (!response.ok) {
        throw new Error(
            await getErrorMessage(
                response,
                "Failed to change member role."
            )
        );
    }

    return response.json();
}