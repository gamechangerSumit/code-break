const API_URL = "http://localhost:8088";

interface LoginResponse {
    token: string;
}

export async function login(
    username: string,
    password: string
): Promise<LoginResponse> {

    const response = await fetch(
        `${API_URL}/api/auth/login`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                username,
                password,
            }),
        }
    );

    if (!response.ok) {
        throw new Error("Invalid username or password");
    }

    return response.json();
}