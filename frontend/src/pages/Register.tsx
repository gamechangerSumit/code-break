import { type FormEvent, useState } from "react";
import { ArrowRight, Code2, Eye, EyeOff, LockKeyhole, Mail, UserRound } from "lucide-react";

interface RegisterProps {
    onBackToLogin: () => void;
}

const API_URL = "http://localhost:8088";

async function getErrorMessage(
    response: Response
): Promise<string> {
    try {
        const data = await response.json();

        if (data?.message) {
            return data.message;
        }

        if (data?.error) {
            return data.error;
        }
    } catch {
        // Ignore invalid/non-JSON error bodies.
    }

    return `Registration failed (${response.status})`;
}

export default function Register({
    onBackToLogin,
}: RegisterProps) {
    const [username, setUsername] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");

    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] =
        useState(false);

    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [loading, setLoading] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        setError("");
        setSuccess("");

        const cleanUsername = username.trim();
        const cleanEmail = email.trim();

        if (!cleanUsername) {
            setError("Username is required.");
            return;
        }

        if (!cleanEmail) {
            setError("Email is required.");
            return;
        }

        if (password.length < 6) {
            setError("Password must be at least 6 characters.");
            return;
        }

        if (password !== confirmPassword) {
            setError("Passwords do not match.");
            return;
        }

        setLoading(true);

        try {
            const response = await fetch(
                `${API_URL}/api/auth/register`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify({
                        username: cleanUsername,
                        email: cleanEmail,
                        password,
                    }),
                }
            );

            if (!response.ok) {
                throw new Error(
                    await getErrorMessage(response)
                );
            }

            setSuccess(
                "Account created successfully. You can sign in now."
            );

            setUsername("");
            setEmail("");
            setPassword("");
            setConfirmPassword("");

            window.setTimeout(() => {
                onBackToLogin();
            }, 900);
        } catch (err) {
            setError(
                err instanceof Error
                    ? err.message
                    : "Registration failed."
            );
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="login-page register-page">
            <div className="register-glow register-glow-one" />
            <div className="register-glow register-glow-two" />

            <div className="register-card">
                <div className="register-brand">
                    <div className="register-logo">
                        <Code2
                            size={21}
                            strokeWidth={2.2}
                        />
                    </div>

                    <div>
                        <div className="register-brand-name">
                            Code Break
                        </div>

                        <div className="register-brand-caption">
                            Collaborative coding workspace
                        </div>
                    </div>
                </div>

                <div className="register-heading">
                    <span className="register-eyebrow">
                        GET STARTED
                    </span>

                    <h1>Create your account</h1>

                    <p>
                        Join Code Break and start coding
                        together in real time.
                    </p>
                </div>

                <form
                    className="register-form"
                    onSubmit={handleSubmit}
                >
                    <label className="register-field">
                        <span>Username</span>

                        <div className="register-input-wrap">
                            <UserRound
                                size={17}
                                strokeWidth={1.8}
                            />

                            <input
                                type="text"
                                placeholder="Choose a username"
                                value={username}
                                onChange={(event) =>
                                    setUsername(
                                        event.target.value
                                    )
                                }
                                autoComplete="username"
                                disabled={loading}
                                required
                            />
                        </div>
                    </label>

                    <label className="register-field">
                        <span>Email</span>

                        <div className="register-input-wrap">
                            <Mail
                                size={17}
                                strokeWidth={1.8}
                            />

                            <input
                                type="email"
                                placeholder="you@example.com"
                                value={email}
                                onChange={(event) =>
                                    setEmail(
                                        event.target.value
                                    )
                                }
                                autoComplete="email"
                                disabled={loading}
                                required
                            />
                        </div>
                    </label>

                    <label className="register-field">
                        <span>Password</span>

                        <div className="register-input-wrap">
                            <LockKeyhole
                                size={17}
                                strokeWidth={1.8}
                            />

                            <input
                                type={
                                    showPassword
                                        ? "text"
                                        : "password"
                                }
                                placeholder="Create a password"
                                value={password}
                                onChange={(event) =>
                                    setPassword(
                                        event.target.value
                                    )
                                }
                                autoComplete="new-password"
                                disabled={loading}
                                required
                            />

                            <button
                                type="button"
                                className="register-password-toggle"
                                onClick={() =>
                                    setShowPassword(
                                        current => !current
                                    )
                                }
                                title={
                                    showPassword
                                        ? "Hide password"
                                        : "Show password"
                                }
                                aria-label={
                                    showPassword
                                        ? "Hide password"
                                        : "Show password"
                                }
                            >
                                {showPassword ? (
                                    <EyeOff
                                        size={17}
                                        strokeWidth={1.8}
                                    />
                                ) : (
                                    <Eye
                                        size={17}
                                        strokeWidth={1.8}
                                    />
                                )}
                            </button>
                        </div>
                    </label>

                    <label className="register-field">
                        <span>Confirm password</span>

                        <div className="register-input-wrap">
                            <LockKeyhole
                                size={17}
                                strokeWidth={1.8}
                            />

                            <input
                                type={
                                    showConfirmPassword
                                        ? "text"
                                        : "password"
                                }
                                placeholder="Repeat your password"
                                value={confirmPassword}
                                onChange={(event) =>
                                    setConfirmPassword(
                                        event.target.value
                                    )
                                }
                                autoComplete="new-password"
                                disabled={loading}
                                required
                            />

                            <button
                                type="button"
                                className="register-password-toggle"
                                onClick={() =>
                                    setShowConfirmPassword(
                                        current => !current
                                    )
                                }
                                title={
                                    showConfirmPassword
                                        ? "Hide password"
                                        : "Show password"
                                }
                                aria-label={
                                    showConfirmPassword
                                        ? "Hide password"
                                        : "Show password"
                                }
                            >
                                {showConfirmPassword ? (
                                    <EyeOff
                                        size={17}
                                        strokeWidth={1.8}
                                    />
                                ) : (
                                    <Eye
                                        size={17}
                                        strokeWidth={1.8}
                                    />
                                )}
                            </button>
                        </div>
                    </label>

                    {error && (
                        <div
                            className="login-error register-message"
                            role="alert"
                        >
                            {error}
                        </div>
                    )}

                    {success && (
                        <div
                            className="register-success"
                            role="status"
                        >
                            {success}
                        </div>
                    )}

                    <button
                        type="submit"
                        className="register-submit"
                        disabled={loading}
                    >
                        <span>
                            {loading
                                ? "Creating account..."
                                : "Create account"}
                        </span>

                        {!loading && (
                            <ArrowRight
                                size={17}
                                strokeWidth={2}
                            />
                        )}
                    </button>
                </form>

                <div className="register-divider">
                    <span>Already have an account?</span>
                </div>

                <button
                    type="button"
                    className="register-login-button"
                    onClick={onBackToLogin}
                    disabled={loading}
                >
                    Sign in to Code Break
                </button>

                <div className="register-footer">
                    <span className="register-live-dot" />
                    Secure JWT authentication
                </div>
            </div>
        </div>
    );
}
