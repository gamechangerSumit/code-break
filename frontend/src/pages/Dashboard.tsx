import {
    useEffect,
    useState,
} from "react";

import {
    Copy,
    Folder,
    FolderPlus,
    Link2,
    LogOut,
    Plus,
    RefreshCw,
    Trash2,
    Users,
    X,
    Zap,
} from "lucide-react";

import {
    getProjects,
    createProject,
    deleteProject,
} from "../project/projectApi";

import type {
    Project,
} from "../project/projectApi";


interface DashboardProps {
    onOpenProject: (
        project: Project
    ) => void;
}


export default function Dashboard({
    onOpenProject,
}: DashboardProps) {

    // =========================================
    // PROJECTS
    // =========================================

    const [
        projects,
        setProjects,
    ] = useState<Project[]>([]);

    const [
        loading,
        setLoading,
    ] = useState(true);

    const [
        error,
        setError,
    ] = useState("");


    // =========================================
    // CREATE PROJECT
    // =========================================

    const [
        showCreateForm,
        setShowCreateForm,
    ] = useState(false);

    const [
        projectName,
        setProjectName,
    ] = useState("");

    const [
        projectDescription,
        setProjectDescription,
    ] = useState("");

    const [
        creating,
        setCreating,
    ] = useState(false);


    // =========================================
    // JOIN PROJECT
    // =========================================

    const [
        showJoinForm,
        setShowJoinForm,
    ] = useState(false);

    const [
        joinCode,
        setJoinCode,
    ] = useState("");

    const [
        joining,
        setJoining,
    ] = useState(false);


    // =========================================
    // COPY JOIN CODE
    // =========================================

    const [
        copiedProjectId,
        setCopiedProjectId,
    ] = useState<number | null>(null);


    // =========================================
    // DELETE PROJECT
    // =========================================

    const [
        deletingProjectId,
        setDeletingProjectId,
    ] = useState<number | null>(null);


    // =========================================
    // TOKEN
    // =========================================

    const token =
        localStorage.getItem("token");


    // =========================================
    // LOAD PROJECTS
    // =========================================

    async function loadProjects() {

        if (!token) {
            setLoading(false);
            return;
        }

        try {

            setLoading(true);
            setError("");

            const data =
                await getProjects(token);

            console.log(
                "PROJECTS FROM API:",
                data
            );

            setProjects(data);

        } catch (err) {

            console.error(
                "Failed to load projects:",
                err
            );

            setError(
                err instanceof Error
                    ? err.message
                    : "Unable to load projects"
            );

        } finally {

            setLoading(false);
        }
    }


    // =========================================
    // INITIAL LOAD
    // =========================================

    useEffect(() => {

        loadProjects();

    }, [token]);


    // =========================================
    // CREATE PROJECT
    // =========================================

    async function handleCreateProject(
        event: React.FormEvent<HTMLFormElement>
    ) {

        event.preventDefault();

        if (!token) {

            setError(
                "You are not authenticated."
            );

            return;
        }

        const name =
            projectName.trim();

        const description =
            projectDescription.trim();

        if (!name) {

            setError(
                "Project name is required."
            );

            return;
        }

        setCreating(true);
        setError("");

        try {

            const project =
                await createProject(
                    name,
                    description,
                    token
                );

            console.log(
                "CREATED PROJECT:",
                project
            );

            setProjects(
                currentProjects => [
                    project,
                    ...currentProjects,
                ]
            );

            setProjectName("");
            setProjectDescription("");
            setShowCreateForm(false);

        } catch (err) {

            console.error(
                "Failed to create project:",
                err
            );

            setError(
                err instanceof Error
                    ? err.message
                    : "Failed to create project"
            );

        } finally {

            setCreating(false);
        }
    }


    // =========================================
    // JOIN PROJECT
    // =========================================

    async function handleJoinProject(
        event: React.FormEvent<HTMLFormElement>
    ) {

        event.preventDefault();

        if (!token) {

            setError(
                "You are not authenticated."
            );

            return;
        }

        const code =
            joinCode
                .trim()
                .toUpperCase();

        if (!code) {

            setError(
                "Join code is required."
            );

            return;
        }

        setJoining(true);
        setError("");

        try {

            const response =
                await fetch(
                    "http://localhost:8088/api/projects/join",
                    {
                        method: "POST",

                        headers: {
                            "Content-Type":
                                "application/json",

                            Authorization:
                                `Bearer ${token}`,
                        },

                        body: JSON.stringify({
                            joinCode: code,
                        }),
                    }
                );

            if (!response.ok) {

                let message =
                    "Failed to join project";

                try {

                    const data =
                        await response.json();

                    if (data.message) {
                        message =
                            data.message;
                    }

                    if (data.error) {
                        message =
                            data.error;
                    }

                } catch {
                    // Ignore JSON parsing error
                }

                throw new Error(message);
            }

            setJoinCode("");
            setShowJoinForm(false);

            await loadProjects();

        } catch (err) {

            console.error(
                "Failed to join project:",
                err
            );

            setError(
                err instanceof Error
                    ? err.message
                    : "Failed to join project"
            );

        } finally {

            setJoining(false);
        }
    }


    // =========================================
    // COPY JOIN CODE
    // =========================================

    async function handleCopyCode(
        event: React.MouseEvent<HTMLButtonElement>,
        project: Project
    ) {

        event.stopPropagation();

        if (!project.joinCode) {
            return;
        }

        try {

            await navigator.clipboard.writeText(
                project.joinCode
            );

            setCopiedProjectId(
                project.id
            );

            setTimeout(() => {

                setCopiedProjectId(
                    currentId =>
                        currentId === project.id
                            ? null
                            : currentId
                );

            }, 2000);

        } catch (err) {

            console.error(
                "Failed to copy join code:",
                err
            );

            setError(
                "Failed to copy join code"
            );
        }
    }


    // =========================================
    // DELETE PROJECT
    // =========================================

    async function handleDeleteProject(
        event: React.MouseEvent<HTMLButtonElement>,
        project: Project
    ) {

        event.stopPropagation();

        if (!token) {

            setError(
                "You are not authenticated."
            );

            return;
        }

        if (!project.joinCode) {

            setError(
                "Only the project owner can delete this project."
            );

            return;
        }

        const confirmed =
            window.confirm(
                `Are you sure you want to permanently delete "${project.name}"?\n\nThis will remove the project and all its members.`
            );

        if (!confirmed) {
            return;
        }

        try {

            setDeletingProjectId(
                project.id
            );

            setError("");

            await deleteProject(
                project.id,
                token
            );

            setProjects(
                currentProjects =>
                    currentProjects.filter(
                        currentProject =>
                            currentProject.id !==
                            project.id
                    )
            );

            setCopiedProjectId(
                currentId =>
                    currentId === project.id
                        ? null
                        : currentId
            );

        } catch (err) {

            console.error(
                "Failed to delete project:",
                err
            );

            setError(
                err instanceof Error
                    ? err.message
                    : "Failed to delete project"
            );

        } finally {

            setDeletingProjectId(
                null
            );
        }
    }


    // =========================================
    // LOGOUT
    // =========================================

    function handleLogout() {

        localStorage.removeItem(
            "token"
        );

        window.location.reload();
    }


    // =========================================
    // CLOSE CREATE FORM
    // =========================================

    function closeCreateForm() {

        if (creating) {
            return;
        }

        setShowCreateForm(false);
        setProjectName("");
        setProjectDescription("");
        setError("");
    }


    // =========================================
    // CLOSE JOIN FORM
    // =========================================

    function closeJoinForm() {

        if (joining) {
            return;
        }

        setShowJoinForm(false);
        setJoinCode("");
        setError("");
    }


    // =========================================
    // UI
    // =========================================

    return (

        <div
            className="dashboard"
            style={{
                minHeight: "100vh",
                background:
                    "#05070b",
                color:
                    "#e6edf7",
            }}
        >

            {/* ================================= */}
            {/* HEADER */}
            {/* ================================= */}

            <header
                className="dashboard-header"
                style={{
                    height: "64px",
                    padding:
                        "0 28px",
                    display:
                        "flex",
                    alignItems:
                        "center",
                    justifyContent:
                        "space-between",
                    borderBottom:
                        "1px solid #172033",
                    background:
                        "#070a10",
                }}
            >

                <div
                    className="logo"
                    style={{
                        display:
                            "flex",
                        alignItems:
                            "center",
                        gap: "10px",
                        fontSize:
                            "17px",
                        fontWeight:
                            700,
                        letterSpacing:
                            "-0.3px",
                    }}
                >

                    <span
                        style={{
                            width: "32px",
                            height: "32px",
                            display:
                                "flex",
                            alignItems:
                                "center",
                            justifyContent:
                                "center",
                            borderRadius:
                                "8px",
                            background:
                                "#0d1d3a",
                            border:
                                "1px solid #1d4ed8",
                            color:
                                "#60a5fa",
                        }}
                    >
                        <Zap
                            size={17}
                            strokeWidth={2.2}
                        />
                    </span>

                    <span>
                        Code
                        <span
                            style={{
                                color:
                                    "#3b82f6",
                            }}
                        >
                            {" "}Break
                        </span>
                    </span>

                </div>


                <button
                    type="button"
                    onClick={
                        handleLogout
                    }
                    style={{
                        height: "34px",
                        padding:
                            "0 13px",
                        display:
                            "flex",
                        alignItems:
                            "center",
                        gap: "7px",
                        border:
                            "1px solid #1b2638",
                        borderRadius:
                            "6px",
                        background:
                            "#0a0f18",
                        color:
                            "#8d9bb0",
                        cursor:
                            "pointer",
                        fontSize:
                            "12px",
                    }}
                >

                    <LogOut
                        size={14}
                    />

                    Logout

                </button>

            </header>


            {/* ================================= */}
            {/* CONTENT */}
            {/* ================================= */}

            <main
                className="dashboard-content"
                style={{
                    width:
                        "min(1180px, calc(100% - 48px))",
                    margin:
                        "0 auto",
                    padding:
                        "46px 0 70px",
                }}
            >

                {/* ================================= */}
                {/* TITLE */}
                {/* ================================= */}

                <div
                    className="projects-title"
                    style={{
                        display:
                            "flex",
                        alignItems:
                            "flex-end",
                        justifyContent:
                            "space-between",
                        gap:
                            "24px",
                        marginBottom:
                            "30px",
                    }}
                >

                    <div>

                        <div
                            style={{
                                display:
                                    "flex",
                                alignItems:
                                    "center",
                                gap:
                                    "8px",
                                marginBottom:
                                    "9px",
                                color:
                                    "#3b82f6",
                                fontSize:
                                    "11px",
                                fontWeight:
                                    700,
                                letterSpacing:
                                    "1.4px",
                                textTransform:
                                    "uppercase",
                            }}
                        >
                            <Folder
                                size={13}
                            />

                            Workspace
                        </div>

                        <h1
                            style={{
                                margin:
                                    0,
                                fontSize:
                                    "30px",
                                lineHeight:
                                    1.15,
                                letterSpacing:
                                    "-0.8px",
                                color:
                                    "#f1f5f9",
                            }}
                        >
                            My Projects
                        </h1>

                        <p
                            className="dashboard-subtitle"
                            style={{
                                margin:
                                    "8px 0 0",
                                color:
                                    "#68778c",
                                fontSize:
                                    "13px",
                            }}
                        >
                            Select a project to start coding.
                        </p>

                    </div>


                    <div
                        className="project-actions"
                        style={{
                            display:
                                "flex",
                            gap:
                                "8px",
                            flexWrap:
                                "wrap",
                        }}
                    >

                        <button
                            type="button"
                            onClick={() => {

                                setError("");
                                setShowJoinForm(
                                    false
                                );
                                setShowCreateForm(
                                    true
                                );

                            }}
                            style={{
                                height:
                                    "38px",
                                padding:
                                    "0 15px",
                                display:
                                    "flex",
                                alignItems:
                                    "center",
                                gap:
                                    "7px",
                                border:
                                    "1px solid #2563eb",
                                borderRadius:
                                    "6px",
                                background:
                                    "#1551c7",
                                color:
                                    "#fff",
                                cursor:
                                    "pointer",
                                fontSize:
                                    "12px",
                                fontWeight:
                                    600,
                            }}
                        >

                            <Plus
                                size={15}
                            />

                            New Project

                        </button>


                        <button
                            type="button"
                            onClick={() => {

                                setError("");
                                setShowCreateForm(
                                    false
                                );
                                setShowJoinForm(
                                    true
                                );

                            }}
                            style={{
                                height:
                                    "38px",
                                padding:
                                    "0 15px",
                                display:
                                    "flex",
                                alignItems:
                                    "center",
                                gap:
                                    "7px",
                                border:
                                    "1px solid #1d2c43",
                                borderRadius:
                                    "6px",
                                background:
                                    "#0b111b",
                                color:
                                    "#aebbd0",
                                cursor:
                                    "pointer",
                                fontSize:
                                    "12px",
                                fontWeight:
                                    600,
                            }}
                        >

                            <Link2
                                size={15}
                            />

                            Join Project

                        </button>

                    </div>

                </div>


                {/* ================================= */}
                {/* ERROR */}
                {/* ================================= */}

                {error && (

                    <div
                        className="login-error"
                        style={{
                            marginBottom:
                                "18px",
                            minHeight:
                                "42px",
                            padding:
                                "10px 12px",
                            display:
                                "flex",
                            alignItems:
                                "center",
                            justifyContent:
                                "space-between",
                            gap:
                                "12px",
                            border:
                                "1px solid #54202b",
                            borderRadius:
                                "6px",
                            background:
                                "#180b10",
                            color:
                                "#fda4af",
                            fontSize:
                                "12px",
                        }}
                    >

                        <span>
                            {error}
                        </span>

                        <button
                            type="button"
                            onClick={() =>
                                setError("")
                            }
                            style={{
                                border:
                                    "none",
                                background:
                                    "transparent",
                                color:
                                    "#fda4af",
                                cursor:
                                    "pointer",
                                padding:
                                    "2px",
                            }}
                        >
                            <X
                                size={15}
                            />
                        </button>

                    </div>

                )}


                {/* ================================= */}
                {/* JOIN FORM */}
                {/* ================================= */}

                {showJoinForm && (

                    <div
                        className="create-project-card"
                        style={{
                            marginBottom:
                                "24px",
                            padding:
                                "22px",
                            border:
                                "1px solid #1a2940",
                            borderRadius:
                                "8px",
                            background:
                                "#090e17",
                        }}
                    >

                        <div
                            style={{
                                display:
                                    "flex",
                                justifyContent:
                                    "space-between",
                                alignItems:
                                    "flex-start",
                                marginBottom:
                                    "20px",
                            }}
                        >

                            <div>

                                <div
                                    style={{
                                        display:
                                            "flex",
                                        alignItems:
                                            "center",
                                        gap:
                                            "9px",
                                        marginBottom:
                                            "6px",
                                        color:
                                            "#60a5fa",
                                    }}
                                >

                                    <Link2
                                        size={17}
                                    />

                                    <h2
                                        style={{
                                            margin:
                                                0,
                                            color:
                                                "#e8eef8",
                                            fontSize:
                                                "16px",
                                        }}
                                    >
                                        Join a Project
                                    </h2>

                                </div>

                                <p
                                    style={{
                                        margin:
                                            0,
                                        color:
                                            "#68778c",
                                        fontSize:
                                            "12px",
                                    }}
                                >
                                    Enter the project join code provided by the owner.
                                </p>

                            </div>

                            <button
                                type="button"
                                onClick={
                                    closeJoinForm
                                }
                                disabled={
                                    joining
                                }
                                style={{
                                    border:
                                        "none",
                                    background:
                                        "transparent",
                                    color:
                                        "#627087",
                                    cursor:
                                        "pointer",
                                }}
                            >
                                <X
                                    size={17}
                                />
                            </button>

                        </div>


                        <form
                            onSubmit={
                                handleJoinProject
                            }
                        >

                            <input
                                type="text"
                                placeholder="ENTER JOIN CODE"
                                value={
                                    joinCode
                                }
                                onChange={
                                    event =>
                                        setJoinCode(
                                            event.target.value
                                        )
                                }
                                disabled={
                                    joining
                                }
                                autoFocus
                                maxLength={8}
                                required
                                style={{
                                    width:
                                        "100%",
                                    height:
                                        "42px",
                                    boxSizing:
                                        "border-box",
                                    padding:
                                        "0 12px",
                                    border:
                                        "1px solid #1c2c43",
                                    borderRadius:
                                        "6px",
                                    outline:
                                        "none",
                                    background:
                                        "#05080d",
                                    color:
                                        "#dbeafe",
                                    fontSize:
                                        "12px",
                                    letterSpacing:
                                        "2px",
                                }}
                            />


                            <div
                                className="create-project-actions"
                                style={{
                                    display:
                                        "flex",
                                    justifyContent:
                                        "flex-end",
                                    gap:
                                        "8px",
                                    marginTop:
                                        "12px",
                                }}
                            >

                                <button
                                    type="button"
                                    onClick={
                                        closeJoinForm
                                    }
                                    disabled={
                                        joining
                                    }
                                    style={{
                                        height:
                                            "36px",
                                        padding:
                                            "0 13px",
                                        border:
                                            "1px solid #1b293d",
                                        borderRadius:
                                            "6px",
                                        background:
                                            "#0a0f17",
                                        color:
                                            "#8290a5",
                                        cursor:
                                            "pointer",
                                    }}
                                >
                                    Cancel
                                </button>

                                <button
                                    type="submit"
                                    disabled={
                                        joining ||
                                        !joinCode.trim()
                                    }
                                    style={{
                                        height:
                                            "36px",
                                        padding:
                                            "0 15px",
                                        border:
                                            "1px solid #2563eb",
                                        borderRadius:
                                            "6px",
                                        background:
                                            "#1551c7",
                                        color:
                                            "#fff",
                                        cursor:
                                            "pointer",
                                        fontSize:
                                            "12px",
                                        fontWeight:
                                            600,
                                    }}
                                >
                                    {joining
                                        ? "Joining..."
                                        : "Join Project"}
                                </button>

                            </div>

                        </form>

                    </div>

                )}


                {/* ================================= */}
                {/* CREATE FORM */}
                {/* ================================= */}

                {showCreateForm && (

                    <div
                        className="create-project-card"
                        style={{
                            marginBottom:
                                "24px",
                            padding:
                                "22px",
                            border:
                                "1px solid #1a2940",
                            borderRadius:
                                "8px",
                            background:
                                "#090e17",
                        }}
                    >

                        <div
                            style={{
                                display:
                                    "flex",
                                justifyContent:
                                    "space-between",
                                alignItems:
                                    "flex-start",
                                marginBottom:
                                    "20px",
                            }}
                        >

                            <div>

                                <div
                                    style={{
                                        display:
                                            "flex",
                                        alignItems:
                                            "center",
                                        gap:
                                            "9px",
                                        marginBottom:
                                            "6px",
                                        color:
                                            "#60a5fa",
                                    }}
                                >

                                    <FolderPlus
                                        size={17}
                                    />

                                    <h2
                                        style={{
                                            margin:
                                                0,
                                            color:
                                                "#e8eef8",
                                            fontSize:
                                                "16px",
                                        }}
                                    >
                                        Create Project
                                    </h2>

                                </div>

                                <p
                                    style={{
                                        margin:
                                            0,
                                        color:
                                            "#68778c",
                                        fontSize:
                                            "12px",
                                    }}
                                >
                                    Create a new collaborative workspace.
                                </p>

                            </div>

                            <button
                                type="button"
                                onClick={
                                    closeCreateForm
                                }
                                disabled={
                                    creating
                                }
                                style={{
                                    border:
                                        "none",
                                    background:
                                        "transparent",
                                    color:
                                        "#627087",
                                    cursor:
                                        "pointer",
                                }}
                            >
                                <X
                                    size={17}
                                />
                            </button>

                        </div>


                        <form
                            onSubmit={
                                handleCreateProject
                            }
                        >

                            <input
                                type="text"
                                placeholder="Project name"
                                value={
                                    projectName
                                }
                                onChange={
                                    event =>
                                        setProjectName(
                                            event.target.value
                                        )
                                }
                                disabled={
                                    creating
                                }
                                autoFocus
                                required
                                style={{
                                    width:
                                        "100%",
                                    height:
                                        "42px",
                                    boxSizing:
                                        "border-box",
                                    marginBottom:
                                        "9px",
                                    padding:
                                        "0 12px",
                                    border:
                                        "1px solid #1c2c43",
                                    borderRadius:
                                        "6px",
                                    outline:
                                        "none",
                                    background:
                                        "#05080d",
                                    color:
                                        "#e6edf7",
                                    fontSize:
                                        "12px",
                                }}
                            />


                            <textarea
                                placeholder="Description"
                                value={
                                    projectDescription
                                }
                                onChange={
                                    event =>
                                        setProjectDescription(
                                            event.target.value
                                        )
                                }
                                disabled={
                                    creating
                                }
                                style={{
                                    width:
                                        "100%",
                                    minHeight:
                                        "90px",
                                    boxSizing:
                                        "border-box",
                                    padding:
                                        "11px 12px",
                                    resize:
                                        "vertical",
                                    border:
                                        "1px solid #1c2c43",
                                    borderRadius:
                                        "6px",
                                    outline:
                                        "none",
                                    background:
                                        "#05080d",
                                    color:
                                        "#e6edf7",
                                    fontSize:
                                        "12px",
                                    fontFamily:
                                        "inherit",
                                }}
                            />


                            <div
                                className="create-project-actions"
                                style={{
                                    display:
                                        "flex",
                                    justifyContent:
                                        "flex-end",
                                    gap:
                                        "8px",
                                    marginTop:
                                        "12px",
                                }}
                            >

                                <button
                                    type="button"
                                    onClick={
                                        closeCreateForm
                                    }
                                    disabled={
                                        creating
                                    }
                                    style={{
                                        height:
                                            "36px",
                                        padding:
                                            "0 13px",
                                        border:
                                            "1px solid #1b293d",
                                        borderRadius:
                                            "6px",
                                        background:
                                            "#0a0f17",
                                        color:
                                            "#8290a5",
                                        cursor:
                                            "pointer",
                                    }}
                                >
                                    Cancel
                                </button>


                                <button
                                    type="submit"
                                    disabled={
                                        creating ||
                                        !projectName.trim()
                                    }
                                    style={{
                                        height:
                                            "36px",
                                        padding:
                                            "0 15px",
                                        border:
                                            "1px solid #2563eb",
                                        borderRadius:
                                            "6px",
                                        background:
                                            "#1551c7",
                                        color:
                                            "#fff",
                                        cursor:
                                            "pointer",
                                        fontSize:
                                            "12px",
                                        fontWeight:
                                            600,
                                    }}
                                >
                                    {creating
                                        ? "Creating..."
                                        : "Create Project"}
                                </button>

                            </div>

                        </form>

                    </div>

                )}


                {/* ================================= */}
                {/* LOADING */}
                {/* ================================= */}

                {loading && (

                    <div
                        className="empty-projects"
                        style={{
                            minHeight:
                                "300px",
                            display:
                                "flex",
                            flexDirection:
                                "column",
                            alignItems:
                                "center",
                            justifyContent:
                                "center",
                            border:
                                "1px solid #121d2d",
                            borderRadius:
                                "8px",
                            background:
                                "#080c13",
                        }}
                    >

                        <RefreshCw
                            size={20}
                            style={{
                                animation:
                                    "dashboard-spin 1s linear infinite",
                                color:
                                    "#3b82f6",
                                marginBottom:
                                    "12px",
                            }}
                        />

                        <p
                            style={{
                                margin:
                                    0,
                                color:
                                    "#68778c",
                                fontSize:
                                    "12px",
                            }}
                        >
                            Loading projects...
                        </p>

                    </div>

                )}


                {/* ================================= */}
                {/* PROJECT GRID */}
                {/* ================================= */}

                {!loading &&
                    projects.length > 0 && (

                        <div
                            className="project-grid"
                            style={{
                                display:
                                    "grid",
                                gridTemplateColumns:
                                    "repeat(auto-fill, minmax(270px, 1fr))",
                                gap:
                                    "14px",
                            }}
                        >

                            {projects.map(
                                project => {

                                    const isOwner =
                                        !!project.joinCode;

                                    const isDeleting =
                                        deletingProjectId ===
                                        project.id;

                                    const isCopied =
                                        copiedProjectId ===
                                        project.id;


                                    return (

                                        <div
                                            key={
                                                project.id
                                            }
                                            className="project-card"
                                            onClick={() => {

                                                if (
                                                    !isDeleting
                                                ) {

                                                    onOpenProject(
                                                        project
                                                    );

                                                }

                                            }}
                                            role="button"
                                            tabIndex={0}
                                            onKeyDown={
                                                event => {

                                                    if (
                                                        event.key ===
                                                            "Enter" ||
                                                        event.key ===
                                                            " "
                                                    ) {

                                                        event.preventDefault();

                                                        if (
                                                            !isDeleting
                                                        ) {

                                                            onOpenProject(
                                                                project
                                                            );

                                                        }

                                                    }

                                                }
                                            }
                                            style={{
                                                position:
                                                    "relative",
                                                minHeight:
                                                    "218px",
                                                padding:
                                                    "18px",
                                                boxSizing:
                                                    "border-box",
                                                display:
                                                    "flex",
                                                flexDirection:
                                                    "column",
                                                border:
                                                    "1px solid #17243a",
                                                borderRadius:
                                                    "8px",
                                                background:
                                                    "#080d15",
                                                cursor:
                                                    isDeleting
                                                        ? "default"
                                                        : "pointer",
                                                transition:
                                                    "border-color .15s ease, background .15s ease, transform .15s ease",
                                            }}
                                            onMouseEnter={
                                                event => {

                                                    if (
                                                        !isDeleting
                                                    ) {

                                                        event.currentTarget.style.borderColor =
                                                            "#2858a8";

                                                        event.currentTarget.style.background =
                                                            "#0a111d";

                                                        event.currentTarget.style.transform =
                                                            "translateY(-2px)";
                                                    }

                                                }
                                            }
                                            onMouseLeave={
                                                event => {

                                                    event.currentTarget.style.borderColor =
                                                        "#17243a";

                                                    event.currentTarget.style.background =
                                                        "#080d15";

                                                    event.currentTarget.style.transform =
                                                        "translateY(0)";
                                                }
                                            }
                                        >

                                            {/* TOP */}

                                            <div
                                                style={{
                                                    display:
                                                        "flex",
                                                    alignItems:
                                                        "center",
                                                    justifyContent:
                                                        "space-between",
                                                    marginBottom:
                                                        "18px",
                                                }}
                                            >

                                                <div
                                                    style={{
                                                        width:
                                                            "38px",
                                                        height:
                                                            "38px",
                                                        display:
                                                            "flex",
                                                        alignItems:
                                                            "center",
                                                        justifyContent:
                                                            "center",
                                                        borderRadius:
                                                            "7px",
                                                        background:
                                                            "#0c1c36",
                                                        border:
                                                            "1px solid #18396e",
                                                        color:
                                                            "#4f9cff",
                                                    }}
                                                >

                                                    <Folder
                                                        size={19}
                                                        strokeWidth={
                                                            1.8
                                                        }
                                                    />

                                                </div>


                                                <span
                                                    style={{
                                                        padding:
                                                            "4px 7px",
                                                        border:
                                                            "1px solid #172c4c",
                                                        borderRadius:
                                                            "4px",
                                                        background:
                                                            "#091322",
                                                        color:
                                                            "#5787bd",
                                                        fontSize:
                                                            "9px",
                                                        fontWeight:
                                                            700,
                                                        letterSpacing:
                                                            ".7px",
                                                        textTransform:
                                                            "uppercase",
                                                    }}
                                                >
                                                    {isOwner
                                                        ? "OWNER"
                                                        : "MEMBER"}
                                                </span>

                                            </div>


                                            {/* NAME */}

                                            <h2
                                                style={{
                                                    margin:
                                                        "0 0 7px",
                                                    color:
                                                        "#e8eef8",
                                                    fontSize:
                                                        "15px",
                                                    fontWeight:
                                                        650,
                                                    lineHeight:
                                                        1.3,
                                                    overflow:
                                                        "hidden",
                                                    textOverflow:
                                                        "ellipsis",
                                                    whiteSpace:
                                                        "nowrap",
                                                }}
                                            >
                                                {
                                                    project.name
                                                }
                                            </h2>


                                            {/* DESCRIPTION */}

                                            <p
                                                style={{
                                                    margin:
                                                        0,
                                                    color:
                                                        "#68778c",
                                                    fontSize:
                                                        "11px",
                                                    lineHeight:
                                                        1.6,
                                                    display:
                                                        "-webkit-box",
                                                    WebkitLineClamp:
                                                        2,
                                                    WebkitBoxOrient:
                                                        "vertical",
                                                    overflow:
                                                        "hidden",
                                                }}
                                            >
                                                {
                                                    project.description ||
                                                    "No description"
                                                }
                                            </p>


                                            {/* SPACER */}

                                            <div
                                                style={{
                                                    flex:
                                                        1,
                                                }}
                                            />


                                            {/* JOIN CODE */}

                                            {project.joinCode && (

                                                <div
                                                    onClick={
                                                        event =>
                                                            event.stopPropagation()
                                                    }
                                                    style={{
                                                        marginTop:
                                                            "16px",
                                                        padding:
                                                            "9px 10px",
                                                        border:
                                                            "1px solid #142640",
                                                        borderRadius:
                                                            "6px",
                                                        background:
                                                            "#070d16",
                                                    }}
                                                >

                                                    <div
                                                        style={{
                                                            display:
                                                                "flex",
                                                            alignItems:
                                                                "center",
                                                            justifyContent:
                                                                "space-between",
                                                            marginBottom:
                                                                "5px",
                                                        }}
                                                    >

                                                        <span
                                                            style={{
                                                                color:
                                                                    "#586b84",
                                                                fontSize:
                                                                    "9px",
                                                                textTransform:
                                                                    "uppercase",
                                                                letterSpacing:
                                                                    ".8px",
                                                            }}
                                                        >
                                                            Join Code
                                                        </span>

                                                        <Users
                                                            size={12}
                                                            color="#3b82f6"
                                                        />

                                                    </div>


                                                    <div
                                                        style={{
                                                            display:
                                                                "flex",
                                                            alignItems:
                                                                "center",
                                                            justifyContent:
                                                                "space-between",
                                                            gap:
                                                                "8px",
                                                        }}
                                                    >

                                                        <strong
                                                            style={{
                                                                color:
                                                                    "#8dbdf8",
                                                                fontSize:
                                                                    "13px",
                                                                letterSpacing:
                                                                    "2px",
                                                            }}
                                                        >
                                                            {
                                                                project.joinCode
                                                            }
                                                        </strong>


                                                        <button
                                                            type="button"
                                                            onClick={
                                                                event =>
                                                                    handleCopyCode(
                                                                        event,
                                                                        project
                                                                    )
                                                            }
                                                            disabled={
                                                                isDeleting
                                                            }
                                                            style={{
                                                                height:
                                                                    "27px",
                                                                padding:
                                                                    "0 8px",
                                                                display:
                                                                    "flex",
                                                                alignItems:
                                                                    "center",
                                                                gap:
                                                                    "5px",
                                                                border:
                                                                    "1px solid #1c3558",
                                                                borderRadius:
                                                                    "4px",
                                                                background:
                                                                    "#0b1728",
                                                                color:
                                                                    "#72aaf1",
                                                                cursor:
                                                                    "pointer",
                                                                fontSize:
                                                                    "9px",
                                                            }}
                                                        >

                                                            <Copy
                                                                size={11}
                                                            />

                                                            {
                                                                isCopied
                                                                    ? "Copied"
                                                                    : "Copy"
                                                            }

                                                        </button>

                                                    </div>

                                                </div>

                                            )}


                                            {/* DELETE */}

                                            {isOwner && (

                                                <div
                                                    onClick={
                                                        event =>
                                                            event.stopPropagation()
                                                    }
                                                    style={{
                                                        marginTop:
                                                            "10px",
                                                        display:
                                                            "flex",
                                                        justifyContent:
                                                            "flex-end",
                                                    }}
                                                >

                                                    <button
                                                        type="button"
                                                        onClick={
                                                            event =>
                                                                handleDeleteProject(
                                                                    event,
                                                                    project
                                                                )
                                                        }
                                                        disabled={
                                                            isDeleting
                                                        }
                                                        style={{
                                                            height:
                                                                "28px",
                                                            padding:
                                                                "0 9px",
                                                            display:
                                                                "flex",
                                                            alignItems:
                                                                "center",
                                                            gap:
                                                                "5px",
                                                            border:
                                                                "1px solid #3a1d27",
                                                            borderRadius:
                                                                "4px",
                                                            background:
                                                                "#110b10",
                                                            color:
                                                                "#9c6674",
                                                            cursor:
                                                                isDeleting
                                                                    ? "not-allowed"
                                                                    : "pointer",
                                                            fontSize:
                                                                "9px",
                                                            opacity:
                                                                isDeleting
                                                                    ? 0.55
                                                                    : 1,
                                                        }}
                                                    >

                                                        <Trash2
                                                            size={11}
                                                        />

                                                        {
                                                            isDeleting
                                                                ? "Deleting..."
                                                                : "Delete"
                                                        }

                                                    </button>

                                                </div>

                                            )}

                                        </div>

                                    );

                                }
                            )}

                        </div>

                    )}


                {/* ================================= */}
                {/* EMPTY */}
                {/* ================================= */}

                {!loading &&
                    projects.length === 0 &&
                    !showCreateForm &&
                    !showJoinForm && (

                        <div
                            className="empty-projects"
                            style={{
                                minHeight:
                                    "360px",
                                display:
                                    "flex",
                                flexDirection:
                                    "column",
                                alignItems:
                                    "center",
                                justifyContent:
                                    "center",
                                textAlign:
                                    "center",
                                border:
                                    "1px solid #142033",
                                borderRadius:
                                    "8px",
                                background:
                                    "#080c13",
                            }}
                        >

                            <div
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
                                    marginBottom:
                                        "14px",
                                    border:
                                        "1px solid #1b3a69",
                                    borderRadius:
                                        "8px",
                                    background:
                                        "#0b1930",
                                    color:
                                        "#4f9cff",
                                }}
                            >

                                <FolderPlus
                                    size={22}
                                />

                            </div>


                            <h2
                                style={{
                                    margin:
                                        "0 0 7px",
                                    color:
                                        "#dbe5f3",
                                    fontSize:
                                        "15px",
                                }}
                            >
                                No projects yet
                            </h2>


                            <p
                                style={{
                                    margin:
                                        "0 0 18px",
                                    color:
                                        "#617086",
                                    fontSize:
                                        "11px",
                                }}
                            >
                                Create a workspace or join an existing one.
                            </p>


                            <div
                                style={{
                                    display:
                                        "flex",
                                    gap:
                                        "8px",
                                    justifyContent:
                                        "center",
                                    flexWrap:
                                        "wrap",
                                }}
                            >

                                <button
                                    type="button"
                                    onClick={() =>
                                        setShowCreateForm(
                                            true
                                        )
                                    }
                                    style={{
                                        height:
                                            "36px",
                                        padding:
                                            "0 13px",
                                        display:
                                            "flex",
                                        alignItems:
                                            "center",
                                        gap:
                                            "6px",
                                        border:
                                            "1px solid #2563eb",
                                        borderRadius:
                                            "6px",
                                        background:
                                            "#1551c7",
                                        color:
                                            "#fff",
                                        cursor:
                                            "pointer",
                                        fontSize:
                                            "11px",
                                        fontWeight:
                                            600,
                                    }}
                                >

                                    <Plus
                                        size={14}
                                    />

                                    Create Project

                                </button>


                                <button
                                    type="button"
                                    onClick={() =>
                                        setShowJoinForm(
                                            true
                                        )
                                    }
                                    style={{
                                        height:
                                            "36px",
                                        padding:
                                            "0 13px",
                                        display:
                                            "flex",
                                        alignItems:
                                            "center",
                                        gap:
                                            "6px",
                                        border:
                                            "1px solid #1c304d",
                                        borderRadius:
                                            "6px",
                                        background:
                                            "#0a111d",
                                        color:
                                            "#91a4bd",
                                        cursor:
                                            "pointer",
                                        fontSize:
                                            "11px",
                                        fontWeight:
                                            600,
                                    }}
                                >

                                    <Link2
                                        size={14}
                                    />

                                    Join Project

                                </button>

                            </div>

                        </div>

                    )}

            </main>


            {/* ================================= */}
            {/* LOCAL ANIMATION */}
            {/* ================================= */}

            <style>
                {`
                    @keyframes dashboard-spin {
                        from {
                            transform: rotate(0deg);
                        }

                        to {
                            transform: rotate(360deg);
                        }
                    }

                    .dashboard input::placeholder,
                    .dashboard textarea::placeholder {
                        color: #3f4d61;
                    }

                    .dashboard input:focus,
                    .dashboard textarea:focus {
                        border-color: #2563eb !important;
                        box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.10);
                    }

                    .dashboard button {
                        font-family: inherit;
                    }

                    @media (max-width: 720px) {
                        .dashboard-content {
                            width: calc(100% - 28px) !important;
                            padding-top: 30px !important;
                        }

                        .projects-title {
                            align-items: flex-start !important;
                            flex-direction: column !important;
                        }
                    }
                `}
            </style>

        </div>
    );
}