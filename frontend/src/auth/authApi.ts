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


export interface RegisterResponse {
    id: number;
    username: string;
    email: string;
}

export async function register(
    username: string,
    email: string,
    password: string
): Promise<RegisterResponse> {
    return requestJson<RegisterResponse>(
        "/auth/register",
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                username,
                email,
                password,
            }),
        }
    );
}
