import {
    type FormEvent,
    useState,
} from "react";

import {
    ArrowRight,
    Code2,
    LockKeyhole,
    User,
    Zap,
} from "lucide-react";

import {
    login,
} from "../auth/authApi";


interface LoginProps {
    onLogin: () => void;
    onRegister?: () => void;
}


export default function Login({
    onLogin,
    onRegister,
}: LoginProps) {

    const [
        username,
        setUsername,
    ] = useState("");

    const [
        password,
        setPassword,
    ] = useState("");

    const [
        error,
        setError,
    ] = useState("");

    const [
        loading,
        setLoading,
    ] = useState(false);


    async function handleSubmit(
        event: FormEvent
    ) {

        event.preventDefault();

        setError("");
        setLoading(true);

        try {

            const result =
                await login(
                    username,
                    password
                );

            localStorage.setItem(
                "token",
                result.token
            );

            localStorage.setItem(
                "username",
                username
            );

            onLogin();

        } catch (error) {

            setError(
                error instanceof Error
                    ? error.message
                    : "Login failed"
            );

        } finally {

            setLoading(false);

        }
    }


    return (

        <div
            className="login-page"
            style={{
                minHeight:
                    "100vh",
                width:
                    "100%",
                display:
                    "flex",
                alignItems:
                    "center",
                justifyContent:
                    "center",
                position:
                    "relative",
                overflow:
                    "hidden",
                background:
                    "#04070c",
                color:
                    "#e6edf7",
                fontFamily:
                    "Inter, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
            }}
        >

            {/* ================================= */}
            {/* BACKGROUND */}
            {/* ================================= */}

            <div
                style={{
                    position:
                        "absolute",
                    inset:
                        0,
                    pointerEvents:
                        "none",
                    background:
                        "radial-gradient(circle at 50% 35%, rgba(37, 99, 235, 0.11), transparent 34%)",
                }}
            />

            <div
                style={{
                    position:
                        "absolute",
                    top:
                        "-180px",
                    left:
                        "50%",
                    width:
                        "520px",
                    height:
                        "520px",
                    transform:
                        "translateX(-50%)",
                    borderRadius:
                        "50%",
                    border:
                        "1px solid rgba(37, 99, 235, 0.08)",
                    pointerEvents:
                        "none",
                }}
            />

            <div
                style={{
                    position:
                        "absolute",
                    inset:
                        0,
                    opacity:
                        0.22,
                    pointerEvents:
                        "none",
                    backgroundImage:
                        "linear-gradient(rgba(59,130,246,.035) 1px, transparent 1px), linear-gradient(90deg, rgba(59,130,246,.035) 1px, transparent 1px)",
                    backgroundSize:
                        "32px 32px",
                    maskImage:
                        "linear-gradient(to bottom, black, transparent 80%)",
                    WebkitMaskImage:
                        "linear-gradient(to bottom, black, transparent 80%)",
                }}
            />


            {/* ================================= */}
            {/* LOGIN CARD */}
            {/* ================================= */}

            <div
                className="login-card"
                style={{
                    position:
                        "relative",
                    zIndex:
                        1,
                    width:
                        "min(390px, calc(100% - 32px))",
                    boxSizing:
                        "border-box",
                    padding:
                        "32px",
                    border:
                        "1px solid #17243a",
                    borderRadius:
                        "10px",
                    background:
                        "rgba(8, 13, 21, 0.96)",
                    boxShadow:
                        "0 24px 70px rgba(0, 0, 0, 0.48)",
                    backdropFilter:
                        "blur(18px)",
                }}
            >

                {/* ================================= */}
                {/* LOGO */}
                {/* ================================= */}

                <div
                    style={{
                        display:
                            "flex",
                        alignItems:
                            "center",
                        justifyContent:
                            "center",
                        marginBottom:
                            "18px",
                    }}
                >

                    <div
                        className="login-logo"
                        style={{
                            width:
                                "48px",
                            height:
                                "48px",
                            display:
                                "flex",
                            alignItems:
                                "center",
                            justifyContent:
                                "center",
                            borderRadius:
                                "10px",
                            border:
                                "1px solid #2455a0",
                            background:
                                "#0b1b35",
                            color:
                                "#60a5fa",
                            boxShadow:
                                "0 0 28px rgba(37, 99, 235, 0.16)",
                        }}
                    >

                        <Zap
                            size={24}
                            strokeWidth={2}
                        />

                    </div>

                </div>


                {/* ================================= */}
                {/* TITLE */}
                {/* ================================= */}

                <div
                    style={{
                        textAlign:
                            "center",
                        marginBottom:
                            "28px",
                    }}
                >

                    <h1
                        style={{
                            margin:
                                0,
                            color:
                                "#f1f5f9",
                            fontSize:
                                "24px",
                            lineHeight:
                                1.2,
                            fontWeight:
                                700,
                            letterSpacing:
                                "-0.5px",
                        }}
                    >
                        Code
                        <span
                            style={{
                                color:
                                    "#3b82f6",
                            }}
                        >
                            Break
                        </span>
                    </h1>


                    <p
                        className="login-subtitle"
                        style={{
                            margin:
                                "8px 0 0",
                            color:
                                "#68778c",
                            fontSize:
                                "12px",
                            lineHeight:
                                1.5,
                        }}
                    >
                        Code together, in real time.
                    </p>

                </div>


                {/* ================================= */}
                {/* FORM */}
                {/* ================================= */}

                <form
                    onSubmit={
                        handleSubmit
                    }
                >

                    {/* USERNAME */}

                    <div
                        style={{
                            marginBottom:
                                "13px",
                        }}
                    >

                        <label
                            htmlFor="login-username"
                            style={{
                                display:
                                    "block",
                                marginBottom:
                                    "7px",
                                color:
                                    "#8d9bb0",
                                fontSize:
                                    "11px",
                                fontWeight:
                                    600,
                            }}
                        >
                            Username
                        </label>


                        <div
                            style={{
                                position:
                                    "relative",
                            }}
                        >

                            <User
                                size={15}
                                strokeWidth={1.8}
                                style={{
                                    position:
                                        "absolute",
                                    left:
                                        "12px",
                                    top:
                                        "50%",
                                    transform:
                                        "translateY(-50%)",
                                    color:
                                        "#52647c",
                                    pointerEvents:
                                        "none",
                                }}
                            />

                            <input
                                id="login-username"
                                type="text"
                                placeholder="Enter your username"
                                value={
                                    username
                                }
                                onChange={
                                    event =>
                                        setUsername(
                                            event.target.value
                                        )
                                }
                                disabled={
                                    loading
                                }
                                required
                                autoComplete="username"
                                style={{
                                    width:
                                        "100%",
                                    height:
                                        "43px",
                                    boxSizing:
                                        "border-box",
                                    padding:
                                        "0 12px 0 38px",
                                    border:
                                        "1px solid #1b2b42",
                                    borderRadius:
                                        "6px",
                                    outline:
                                        "none",
                                    background:
                                        "#05090f",
                                    color:
                                        "#e6edf7",
                                    fontSize:
                                        "12px",
                                    transition:
                                        "border-color .15s ease, box-shadow .15s ease",
                                }}
                                onFocus={
                                    event => {
                                        event.currentTarget.style.borderColor =
                                            "#2563eb";
                                        event.currentTarget.style.boxShadow =
                                            "0 0 0 2px rgba(37,99,235,.10)";
                                    }
                                }
                                onBlur={
                                    event => {
                                        event.currentTarget.style.borderColor =
                                            "#1b2b42";
                                        event.currentTarget.style.boxShadow =
                                            "none";
                                    }
                                }
                            />

                        </div>

                    </div>


                    {/* PASSWORD */}

                    <div
                        style={{
                            marginBottom:
                                "14px",
                        }}
                    >

                        <label
                            htmlFor="login-password"
                            style={{
                                display:
                                    "block",
                                marginBottom:
                                    "7px",
                                color:
                                    "#8d9bb0",
                                fontSize:
                                    "11px",
                                fontWeight:
                                    600,
                            }}
                        >
                            Password
                        </label>


                        <div
                            style={{
                                position:
                                    "relative",
                            }}
                        >

                            <LockKeyhole
                                size={15}
                                strokeWidth={1.8}
                                style={{
                                    position:
                                        "absolute",
                                    left:
                                        "12px",
                                    top:
                                        "50%",
                                    transform:
                                        "translateY(-50%)",
                                    color:
                                        "#52647c",
                                    pointerEvents:
                                        "none",
                                }}
                            />

                            <input
                                id="login-password"
                                type="password"
                                placeholder="Enter your password"
                                value={
                                    password
                                }
                                onChange={
                                    event =>
                                        setPassword(
                                            event.target.value
                                        )
                                }
                                disabled={
                                    loading
                                }
                                required
                                autoComplete="current-password"
                                style={{
                                    width:
                                        "100%",
                                    height:
                                        "43px",
                                    boxSizing:
                                        "border-box",
                                    padding:
                                        "0 12px 0 38px",
                                    border:
                                        "1px solid #1b2b42",
                                    borderRadius:
                                        "6px",
                                    outline:
                                        "none",
                                    background:
                                        "#05090f",
                                    color:
                                        "#e6edf7",
                                    fontSize:
                                        "12px",
                                    transition:
                                        "border-color .15s ease, box-shadow .15s ease",
                                }}
                                onFocus={
                                    event => {
                                        event.currentTarget.style.borderColor =
                                            "#2563eb";
                                        event.currentTarget.style.boxShadow =
                                            "0 0 0 2px rgba(37,99,235,.10)";
                                    }
                                }
                                onBlur={
                                    event => {
                                        event.currentTarget.style.borderColor =
                                            "#1b2b42";
                                        event.currentTarget.style.boxShadow =
                                            "none";
                                    }
                                }
                            />

                        </div>

                    </div>


                    {/* ERROR */}

                    {error && (

                        <div
                            className="login-error"
                            style={{
                                display:
                                    "flex",
                                alignItems:
                                    "center",
                                gap:
                                    "8px",
                                marginBottom:
                                    "13px",
                                padding:
                                    "10px 11px",
                                border:
                                    "1px solid #54202b",
                                borderRadius:
                                    "6px",
                                background:
                                    "#170a10",
                                color:
                                    "#fda4af",
                                fontSize:
                                    "11px",
                                lineHeight:
                                    1.4,
                            }}
                        >

                            <span
                                style={{
                                    width:
                                        "5px",
                                    height:
                                        "5px",
                                    flexShrink:
                                        0,
                                    borderRadius:
                                        "50%",
                                    background:
                                        "#fb7185",
                                }}
                            />

                            <span>
                                {error}
                            </span>

                        </div>

                    )}


                    {/* SIGN IN */}

                    <button
                        type="submit"
                        disabled={
                            loading
                        }
                        style={{
                            width:
                                "100%",
                            height:
                                "43px",
                            display:
                                "flex",
                            alignItems:
                                "center",
                            justifyContent:
                                "center",
                            gap:
                                "8px",
                            border:
                                "1px solid #2563eb",
                            borderRadius:
                                "6px",
                            background:
                                loading
                                    ? "#123d91"
                                    : "#1551c7",
                            color:
                                "#ffffff",
                            cursor:
                                loading
                                    ? "not-allowed"
                                    : "pointer",
                            fontSize:
                                "12px",
                            fontWeight:
                                650,
                            transition:
                                "background .15s ease, border-color .15s ease, transform .1s ease",
                            opacity:
                                loading
                                    ? 0.8
                                    : 1,
                        }}
                        onMouseEnter={
                            event => {

                                if (
                                    !loading
                                ) {

                                    event.currentTarget.style.background =
                                        "#1d5ed8";

                                    event.currentTarget.style.borderColor =
                                        "#3b82f6";
                                }

                            }
                        }
                        onMouseLeave={
                            event => {

                                event.currentTarget.style.background =
                                    loading
                                        ? "#123d91"
                                        : "#1551c7";

                                event.currentTarget.style.borderColor =
                                    "#2563eb";
                            }
                        }
                    >

                        {loading ? (

                            <>
                                <span
                                    style={{
                                        width:
                                            "13px",
                                        height:
                                            "13px",
                                        border:
                                            "2px solid rgba(255,255,255,.35)",
                                        borderTopColor:
                                            "#ffffff",
                                        borderRadius:
                                            "50%",
                                        animation:
                                            "login-spin .7s linear infinite",
                                    }}
                                />

                                Signing in...
                            </>

                        ) : (

                            <>
                                Sign In

                                <ArrowRight
                                    size={15}
                                />
                            </>

                        )}

                    </button>

                </form>


                {/* ================================= */}
                {/* REGISTER */}
                {/* ================================= */}

                {onRegister && (
                    <div
                        style={{
                            marginTop: "16px",
                            textAlign: "center",
                            color: "#596a80",
                            fontSize: "11px",
                        }}
                    >
                        <span>
                            Don't have an account?{" "}
                        </span>

                        <button
                            type="button"
                            onClick={onRegister}
                            disabled={loading}
                            style={{
                                padding: 0,
                                border: 0,
                                outline: "none",
                                background: "transparent",
                                color: "#60a5fa",
                                font: "inherit",
                                fontWeight: 650,
                                cursor: loading
                                    ? "not-allowed"
                                    : "pointer",
                            }}
                        >
                            Create account
                        </button>
                    </div>
                )}

                {/* ================================= */}
                {/* FOOTER */}
                {/* ================================= */}

                <div
                    style={{
                        display:
                            "flex",
                        alignItems:
                            "center",
                        justifyContent:
                            "center",
                        gap:
                            "7px",
                        marginTop:
                            "24px",
                        paddingTop:
                            "18px",
                        borderTop:
                            "1px solid #111c2b",
                        color:
                            "#46566c",
                        fontSize:
                            "10px",
                    }}
                >

                    <Code2
                        size={12}
                    />

                    <span>
                        Real-time collaborative development
                    </span>

                </div>

            </div>


            {/* ================================= */}
            {/* ANIMATION */}
            {/* ================================= */}

            <style>
                {`
                    @keyframes login-spin {
                        from {
                            transform: rotate(0deg);
                        }

                        to {
                            transform: rotate(360deg);
                        }
                    }

                    .login-page input::placeholder {
                        color: #3e4d61;
                    }

                    .login-page button:disabled {
                        cursor: not-allowed;
                    }
                `}
            </style>

        </div>
    );
}