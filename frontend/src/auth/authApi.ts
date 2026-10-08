import {
    requestJson,
} from "../api/http";

interface LoginResponse {
    token: string;
}

export async function login(
    username: string,
    password: string
): Promise<LoginResponse> {
    return requestJson<LoginResponse>(
        "/auth/login",
        {
            method: "POST",
            headers: {
                "Content-Type":
                    "application/json",
            },
            body: JSON.stringify({
                username,
                password,
            }),
        }
    );
}
