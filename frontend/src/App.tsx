import {
    useCallback,
    useEffect,
    useRef,
    useState,
} from "react";

import {
    ArrowLeft,
    Check,
    Circle,
    Cloud,
    CloudOff,
    Code2,
    FileCode2,
    FolderOpen,
    LogOut,
    MessageSquare,
    Save,
    Send,
    Search,
    Bell,
    Settings2,
    Users,
    Wifi,
    WifiOff,
    X,
    Zap,
} from "lucide-react";

import ActivityBar from "./components/ActivityBar";

import CodeEditor from "./editor/CodeEditor";

import FileExplorer, {
    type ExplorerFile,
} from "./explorer/FileExplorer";

import Login from "./pages/Login";
import Register from "./pages/Register";
import Dashboard from "./pages/Dashboard";

import MembersPanel from "./project/MembersPanel";

import type {
    Project,
    ProjectRole,
} from "./project/projectApi";

import {
    getProjectMembers,
} from "./project/projectApi";

import {
    useChat,
} from "./collaboration/useChat";

import type {
    ChatMessage,
} from "./collaboration/useChat";

import {
    useCollaboration,
} from "./collaboration/useCollaboration";

import type {
    CollaborationMessage,
    WorkspaceMessage,
    WorkspaceSnapshot,
    RoleChangedMessage,
} from "./collaboration/useCollaboration";

import {
    importWorkspace,
    getWorkspace,
    getWorkspaceFileContent,
} from "./workspace/workspaceApi";

import {
    openLocalFolder,
    loadWorkspace,
    getWorkspaceName,
    clearWorkspace,
    createWorkspaceFile,
    createWorkspaceFolder,
    deleteWorkspaceFile,
    deleteWorkspaceFolder,
    renameWorkspaceFile,
    renameWorkspaceFolder,
    normalizeWorkspacePath,
    saveProjectToDevice,
} from "./workspace/localWorkspace";

import "./App.css";


// =====================================================
// HELPERS
// =====================================================

function makeExplorerFile(
    path: string,
    content: string
): ExplorerFile {

    const normalized =
        normalizeWorkspacePath(path);

    const parts =
        normalized
            .split("/")
            .filter(Boolean);

    return {
        path: normalized,

        name:
            parts[parts.length - 1] ??
            normalized,

        content:
            content ?? "",
    };
}


// =====================================================
// BUILD FILES
// =====================================================

function buildExplorerFiles(
    sourceFiles:
        {
            path: string;
            content?: string;
            name?: string;
        }[]
): ExplorerFile[] {

    return (
        sourceFiles ?? []
    )
        .filter(
            file =>
                Boolean(
                    normalizeWorkspacePath(
                        file.path
                    )
                )
        )
        .map(
            file =>
                makeExplorerFile(
                    file.path,
                    file.content ?? ""
                )
        )
        .sort(
            (a, b) =>
                a.path.localeCompare(
                    b.path
                )
        );
}


// =====================================================
// BUILD FOLDERS
// =====================================================

function buildExplorerFolders(
    sourceFolders:
        (
            string |
            {
                path: string;
            }
        )[]
): string[] {

    const result =
        new Set<string>();

    for (
        const folder
        of sourceFolders ?? []
    ) {

        const rawPath =
            typeof folder === "string"
                ? folder
                : folder.path;

        const normalized =
            normalizeWorkspacePath(
                rawPath
            );

        if (!normalized) {
            continue;
        }

        const parts =
            normalized
                .split("/")
                .filter(Boolean);

        let current = "";

        for (
            const part
            of parts
        ) {

            current =
                current
                    ? `${current}/${part}`
                    : part;

            result.add(
                current
            );
        }
    }

    return Array.from(result).sort(
        (a, b) =>
            a.localeCompare(b)
    );
}


// =====================================================
// PARENT FOLDERS
// =====================================================

function getParentFolders(
    path: string
): string[] {

    const normalized =
        normalizeWorkspacePath(path);

    if (!normalized) {
        return [];
    }

    const parts =
        normalized
            .split("/")
            .filter(Boolean);

    parts.pop();

    const result: string[] = [];

    let current = "";

    for (
        const part
        of parts
    ) {

        current =
            current
                ? `${current}/${part}`
                : part;

        result.push(
            current
        );
    }

    return result;
}


// =====================================================
// BREADCRUMB PARTS
// =====================================================

function getBreadcrumbParts(
    projectName: string,
    path: string | null
): string[] {

    const normalized =
        path
            ? normalizeWorkspacePath(path)
            : "";

    return [
        projectName || "shared-workspace",
        ...(normalized
            ? normalized.split("/").filter(Boolean)
            : []),
    ];
}


// =====================================================
// ROLE LABEL
// =====================================================

function getRoleLabel(
    role: ProjectRole | null
) {

    if (!role) {
        return "Loading";
    }

    return (
        role.charAt(0) +
        role.slice(1).toLowerCase()
    );
}


// =====================================================
// APP
// =====================================================

function App() {

    // =================================================
    // AUTH
    // =================================================

    const [
        authenticated,
        setAuthenticated,
    ] = useState<boolean>(
        Boolean(
            localStorage.getItem("token")
        )
    );

    const [
        authScreen,
        setAuthScreen,
    ] = useState<"login" | "register">(
        "login"
    );


    // =================================================
    // PROJECT
    // =================================================

    const [
        selectedProject,
        setSelectedProject,
    ] = useState<Project | null>(
        null
    );


    // =================================================
    // FILES
    // =================================================

    const [
        files,
        setFiles,
    ] = useState<ExplorerFile[]>(
        []
    );

    const filesRef =
        useRef<ExplorerFile[]>(
            []
        );


    // =================================================
    // FOLDERS
    // =================================================

    const [
        folders,
        setFolders,
    ] = useState<string[]>(
        []
    );

    const foldersRef =
        useRef<string[]>(
            []
        );


    // =================================================
    // SELECTED FILE
    // =================================================

    const [
        selectedFile,
        setSelectedFile,
    ] = useState<ExplorerFile | null>(
        null
    );


    // =================================================
    // MEMBERS
    // =================================================

    const [
        ,
        setMembers,
    ] = useState<
        {
            id: number;
            userId: number;
            username: string;
            role: ProjectRole;
        }[]
    >([]);


    // =================================================
    // ROLE
    // =================================================

    const [
        currentRole,
        setCurrentRole,
    ] = useState<ProjectRole | null>(
        null
    );


    // =================================================
    // ACTIVITY PANEL
    // =================================================

    const [
        activePanel,
        setActivePanel,
    ] = useState<
        "explorer" | "collaboration"
    >("explorer");


    // =================================================
    // CHAT
    // =================================================

    const [
        chatMessages,
        setChatMessages,
    ] = useState<ChatMessage[]>(
        []
    );


    // =================================================
    // WORKSPACE
    // =================================================

    const [
        workspaceName,
        setWorkspaceName,
    ] = useState<string | null>(
        getWorkspaceName()
    );

    const [
        workspaceSyncing,
        setWorkspaceSyncing,
    ] = useState(false);

    const [
    saving,
    setSaving,
] = useState(false);

const [
    isDirty,
    setIsDirty,
] = useState(false);


    // =================================================
    // ERROR
    // =================================================

    const [
        error,
        setError,
    ] = useState("");


    // =================================================
    // LOCAL WORKSPACE ACTIVE
    // =================================================

    const localWorkspaceActiveRef =
        useRef(false);


    // =================================================
    // OWNER IMPORT TRACKING
    // =================================================

    const ownerImportedProjectRef =
        useRef<number | null>(
            null
        );


    // =================================================
    // CONTENT REQUEST TRACKING
    // =================================================

    const contentRequestIdRef =
        useRef(0);


    // =================================================
    // AUTH DATA
    // =================================================

    const token =
        localStorage.getItem(
            "token"
        );

    const username =
        localStorage.getItem(
            "username"
        ) ?? "anonymous";


    // =================================================
    // CHAT MESSAGE
    // =================================================

    const handleChatMessage =
        useCallback(
            (
                message:
                    ChatMessage
            ) => {

                if (!selectedProject) {
                    return;
                }

                if (
                    message.projectId !==
                    selectedProject.id
                ) {
                    return;
                }

                setChatMessages(
                    current =>
                        [
                            ...current,
                            message,
                        ].slice(-100)
                );
            },
            [
                selectedProject,
            ]
        );


    // =================================================
    // REMOTE CODE
    // =================================================

    const handleRemoteChange =
        useCallback(
            (
                message:
                    CollaborationMessage
            ) => {

                if (!selectedProject) {
                    return;
                }

                if (
                    message.projectId !==
                    selectedProject.id
                ) {
                    return;
                }

                if (
                    !message.filePath
                ) {
                    return;
                }

                const path =
                    normalizeWorkspacePath(
                        message.filePath
                    );

                if (!path) {
                    return;
                }

                const updated =
                    filesRef.current.map(
                        file =>
                            file.path === path
                                ? {
                                    ...file,
                                    content:
                                        message.content ??
                                        "",
                                }
                                : file
                    );

                filesRef.current =
                    updated;

                setFiles(
                    updated
                );

                setSelectedFile(
                    current =>
                        current &&
                        current.path === path
                            ? {
                                ...current,
                                content:
                                    message.content ??
                                    "",
                            }
                            : current
                );
            },
            [
                selectedProject,
            ]
        );


    // =================================================
    // REMOTE WORKSPACE MESSAGE
    // =================================================

    const handleWorkspaceMessage =
        useCallback(
            (
                message:
                    WorkspaceMessage
            ) => {

                if (!selectedProject) {
                    return;
                }

                if (
                    message.projectId !==
                    selectedProject.id
                ) {
                    return;
                }

                if (
                    !message.filePath
                ) {
                    return;
                }

                const path =
                    normalizeWorkspacePath(
                        message.filePath
                    );

                if (!path) {
                    return;
                }


                if (
                    message.type ===
                    "WORKSPACE_FILE"
                ) {

                    const updatedFile =
                        makeExplorerFile(
                            path,
                            message.content ?? ""
                        );

                    const exists =
                        filesRef.current.some(
                            file =>
                                file.path === path
                        );

                    const updated =
                        exists
                            ? filesRef.current.map(
                                file =>
                                    file.path === path
                                        ? updatedFile
                                        : file
                            )
                            : [
                                ...filesRef.current,
                                updatedFile,
                            ];

                    updated.sort(
                        (a, b) =>
                            a.path.localeCompare(
                                b.path
                            )
                    );

                    filesRef.current =
                        updated;

                    setFiles(
                        updated
                    );


                    const parents =
                        getParentFolders(
                            path
                        );

                    if (
                        parents.length > 0
                    ) {

                        const updatedFolders =
                            buildExplorerFolders([
                                ...foldersRef.current,
                                ...parents,
                            ]);

                        foldersRef.current =
                            updatedFolders;

                        setFolders(
                            updatedFolders
                        );
                    }

                    return;
                }


                if (
                    message.type ===
                    "WORKSPACE_FOLDER"
                ) {

                    const updatedFolders =
                        buildExplorerFolders([
                            ...foldersRef.current,
                            path,
                        ]);

                    foldersRef.current =
                        updatedFolders;

                    setFolders(
                        updatedFolders
                    );

                    return;
                }


                if (
                    message.type ===
                    "WORKSPACE_RENAME"
                ) {

                    const oldPath =
                        normalizeWorkspacePath(
                            message.filePath
                        );

                    const newPath =
                        normalizeWorkspacePath(
                            message.content ?? ""
                        );

                    if (!oldPath || !newPath) {
                        return;
                    }

                    const updatedFiles =
                        filesRef.current.map(
                            file => {
                                if (file.path === oldPath) {
                                    return makeExplorerFile(
                                        newPath,
                                        file.content ?? ""
                                    );
                                }

                                return file;
                            }
                        );

                    const filePrefix = `${oldPath}/`;

                    const renamedFiles =
                        updatedFiles.map(
                            file => {
                                if (file.path.startsWith(filePrefix)) {
                                    const nextPath =
                                        `${newPath}${file.path.slice(oldPath.length)}`;

                                    return makeExplorerFile(
                                        nextPath,
                                        file.content ?? ""
                                    );
                                }

                                return file;
                            }
                        );

                    const renamedFolders =
                        foldersRef.current.map(
                            folder => {
                                if (folder === oldPath) {
                                    return newPath;
                                }

                                if (folder.startsWith(filePrefix)) {
                                    return `${newPath}${folder.slice(oldPath.length)}`;
                                }

                                return folder;
                            }
                        );

                    renamedFiles.sort(
                        (a, b) =>
                            a.path.localeCompare(b.path)
                    );

                    renamedFolders.sort(
                        (a, b) =>
                            a.localeCompare(b)
                    );

                    filesRef.current = renamedFiles;
                    foldersRef.current = renamedFolders;

                    setFiles(renamedFiles);
                    setFolders(renamedFolders);

                    setSelectedFile(
                        current => {
                            if (!current) {
                                return current;
                            }

                            if (current.path === oldPath) {
                                return makeExplorerFile(
                                    newPath,
                                    current.content ?? ""
                                );
                            }

                            if (current.path.startsWith(filePrefix)) {
                                const nextPath =
                                    `${newPath}${current.path.slice(oldPath.length)}`;

                                return makeExplorerFile(
                                    nextPath,
                                    current.content ?? ""
                                );
                            }

                            return current;
                        }
                    );

                    return;
                }


                if (
                    message.type ===
                    "WORKSPACE_DELETE"
                ) {

                    const updated =
                        filesRef.current.filter(
                            file =>
                                file.path !== path
                        );

                    filesRef.current =
                        updated;

                    setFiles(
                        updated
                    );

                    setSelectedFile(
                        current =>
                            current?.path === path
                                ? updated[0] ?? null
                                : current
                    );

                    return;
                }


                if (
                    message.type ===
                    "WORKSPACE_FOLDER_DELETE"
                ) {

                    const prefix =
                        `${path}/`;


                    const updatedFiles =
                        filesRef.current.filter(
                            file =>
                                file.path !== path &&
                                !file.path.startsWith(
                                    prefix
                                )
                        );


                    const updatedFolders =
                        foldersRef.current.filter(
                            folder =>
                                folder !== path &&
                                !folder.startsWith(
                                    prefix
                                )
                        );


                    filesRef.current =
                        updatedFiles;

                    foldersRef.current =
                        updatedFolders;


                    setFiles(
                        updatedFiles
                    );

                    setFolders(
                        updatedFolders
                    );


                    setSelectedFile(
                        current =>
                            current &&
                            (
                                current.path === path ||
                                current.path.startsWith(
                                    prefix
                                )
                            )
                                ? updatedFiles[0] ?? null
                                : current
                    );
                }
            },
            [
                selectedProject,
            ]
        );


    // =================================================
    // SNAPSHOT
    // =================================================

    const handleWorkspaceSnapshot =
        useCallback(
            (
                snapshot:
                    WorkspaceSnapshot
            ) => {

                if (!selectedProject) {
                    return;
                }

                if (
                    snapshot.projectId !==
                    selectedProject.id
                ) {
                    return;
                }


                if (
                    currentRole === "OWNER" &&
                    localWorkspaceActiveRef.current
                ) {

                    setWorkspaceSyncing(
                        false
                    );

                    return;
                }


                const snapshotFiles =
                    buildExplorerFiles(
                        snapshot.files ?? []
                    );

                const snapshotFolders =
                    buildExplorerFolders(
                        snapshot.folders ?? []
                    );


                filesRef.current =
                    snapshotFiles;

                foldersRef.current =
                    snapshotFolders;


                setFiles(
                    snapshotFiles
                );

                setFolders(
                    snapshotFolders
                );


                if (
                    !localWorkspaceActiveRef.current
                ) {

                    setWorkspaceName(
                        current =>
                            current ??
                            "Shared Workspace"
                    );
                }


                setSelectedFile(
                    current => {

                        if (current) {

                            const same =
                                snapshotFiles.find(
                                    file =>
                                        file.path ===
                                        current.path
                                );

                            if (same) {
                                return same;
                            }
                        }

                        return (
                            snapshotFiles[0] ??
                            null
                        );
                    }
                );


                setWorkspaceSyncing(
                    false
                );

                setError("");
            },
            [
                selectedProject,
                currentRole,
            ]
        );


    // =================================================
    // ROLE CHANGE
    // =================================================

    const handleRoleChange =
        useCallback(
            (
                message:
                    RoleChangedMessage
            ) => {

                if (!selectedProject) {
                    return;
                }

                if (
                    message.projectId !==
                    selectedProject.id
                ) {
                    return;
                }


                const role =
                    message.role as ProjectRole;


                setMembers(
                    current =>
                        current.map(
                            member =>
                                member.id ===
                                message.memberId
                                    ? {
                                        ...member,
                                        role,
                                    }
                                    : member
                        )
                );


                if (
                    message.username ===
                    username
                ) {

                    setCurrentRole(
                        role
                    );


                    if (
                        role ===
                        "OWNER"
                    ) {

                        ownerImportedProjectRef.current =
                            null;
                    }
                }
            },
            [
                selectedProject,
                username,
            ]
        );


    // =================================================
    // COLLABORATION
    // =================================================

    const {
        sendChange,
        sendWorkspaceFile,
        sendWorkspaceFolder,
        sendWorkspaceDelete,
        sendWorkspaceFolderDelete,
        sendWorkspaceRename,
        requestWorkspaceSnapshot,
        isConnected,
    } =
        useCollaboration({
            projectId:
                selectedProject?.id ??
                null,

            filePath:
                selectedFile?.path ??
                null,

            onRemoteChange:
                handleRemoteChange,

            onWorkspaceFile:
                handleWorkspaceMessage,

            onWorkspaceSnapshot:
                handleWorkspaceSnapshot,

            onRoleChange:
                handleRoleChange,
        });


    // =================================================
    // CHAT
    // =================================================

    const {
        sendMessage,
    } =
        useChat({
            projectId:
                selectedProject?.id ??
                null,

            onMessage:
                handleChatMessage,
        });


    // =================================================
    // LOAD MEMBERS
    // =================================================

    useEffect(
        () => {

            if (
                !selectedProject ||
                !token
            ) {

                setMembers([]);

                setCurrentRole(
                    null
                );

                return;
            }


            let cancelled =
                false;


            getProjectMembers(
                selectedProject.id,
                token
            )
                .then(data => {

                    if (
                        cancelled
                    ) {
                        return;
                    }


                    setMembers(
                        data
                    );


                    const currentMember =
                        data.find(
                            member =>
                                member.username ===
                                username
                        );


                    setCurrentRole(
                        currentMember?.role ??
                        null
                    );

                })
                .catch(err => {

                    if (
                        cancelled
                    ) {
                        return;
                    }


                    console.error(
                        "[APP] Failed to load members:",
                        err
                    );


                    setError(
                        err instanceof Error
                            ? err.message
                            : "Failed to load project members."
                    );
                });


            return () => {
                cancelled = true;
            };

        },
        [
            selectedProject?.id,
            token,
            username,
        ]
    );


    // =================================================
    // PROJECT RESET
    // =================================================

    useEffect(
        () => {

            contentRequestIdRef.current += 1;

            setFiles([]);

            setFolders([]);

            setSelectedFile(
                null
            );

            setMembers([]);

            setCurrentRole(
                null
            );

            setChatMessages([]);

            setSaving(
                false
            );

            setIsDirty(
                false
            );

            setWorkspaceSyncing(
                false
            );

            setWorkspaceName(
                getWorkspaceName()
            );

            setActivePanel(
                "explorer"
            );


            filesRef.current =
                [];

            foldersRef.current =
                [];


            localWorkspaceActiveRef.current =
                false;


            ownerImportedProjectRef.current =
                null;


            setError("");

        },
        [
            selectedProject?.id,
        ]
    );


    // =================================================
    // INITIAL REST WORKSPACE LOAD
    // =================================================

    useEffect(
        () => {

            if (
                !selectedProject ||
                !token
            ) {
                return;
            }


            if (
                currentRole ===
                "OWNER"
            ) {
                return;
            }


            if (
                localWorkspaceActiveRef.current
            ) {
                return;
            }


            let cancelled =
                false;


            setWorkspaceSyncing(
                true
            );


            getWorkspace(
                selectedProject.id,
                token
            )
                .then(
                    snapshot => {

                        if (
                            cancelled
                        ) {
                            return;
                        }


                        handleWorkspaceSnapshot(
                            snapshot
                        );
                    }
                )
                .catch(
                    err => {

                        if (
                            cancelled
                        ) {
                            return;
                        }


                        console.error(
                            "[APP] Failed to load workspace:",
                            err
                        );


                        setWorkspaceSyncing(
                            false
                        );


                        setError(
                            err instanceof Error
                                ? err.message
                                : "Failed to load workspace."
                        );
                    }
                );


            return () => {
                cancelled = true;
            };

        },
        [
            selectedProject?.id,
            currentRole,
            token,
            handleWorkspaceSnapshot,
        ]
    );


    // =================================================
    // OWNER COMPLETE REST IMPORT
    // =================================================

    const importOwnerWorkspace =
        useCallback(
            async () => {

                if (!selectedProject) {
                    return;
                }


                if (
                    currentRole !==
                    "OWNER"
                ) {
                    return;
                }


                if (
                    !localWorkspaceActiveRef.current
                ) {
                    return;
                }


                if (!token) {

                    setError(
                        "Authentication token is missing."
                    );

                    return;
                }


                if (
                    ownerImportedProjectRef.current ===
                    selectedProject.id
                ) {
                    return;
                }


                const workspaceFiles =
                    filesRef.current;


                const workspaceFolders =
                    foldersRef.current;


                setWorkspaceSyncing(
                    true
                );


                try {

                    const response =
                        await importWorkspace(
                            selectedProject.id,

                            {
                                files:
                                    workspaceFiles.map(
                                        file => ({
                                            path:
                                                normalizeWorkspacePath(
                                                    file.path
                                                ),

                                            name:
                                                file.name,

                                            content:
                                                file.content ??
                                                "",
                                        })
                                    ),

                                folders:
                                    workspaceFolders.map(
                                        folder =>
                                            normalizeWorkspacePath(
                                                folder
                                            )
                                    ),
                            },

                            token
                        );


                    ownerImportedProjectRef.current =
                        selectedProject.id;


                    setWorkspaceSyncing(
                        false
                    );

                    setError("");

                    console.log(
                        "[APP] Owner import complete:",
                        {
                            projectId:
                                response.projectId,
                            files:
                                response.files?.length ??
                                0,
                            folders:
                                response.folders?.length ??
                                0,
                        }
                    );

                } catch (err) {

                    ownerImportedProjectRef.current =
                        null;


                    setWorkspaceSyncing(
                        false
                    );


                    setError(
                        err instanceof Error
                            ? err.message
                            : "Failed to import workspace."
                    );
                }

            },
            [
                selectedProject,
                currentRole,
                token,
            ]
        );


    // =================================================
    // OWNER AUTO IMPORT
    // =================================================

    useEffect(
        () => {

            if (!selectedProject) {
                return;
            }


            if (
                currentRole !==
                "OWNER"
            ) {
                return;
            }


            if (
                !localWorkspaceActiveRef.current
            ) {
                return;
            }


            void importOwnerWorkspace();

        },
        [
            selectedProject?.id,
            currentRole,
            importOwnerWorkspace,
        ]
    );


    // =================================================
    // NON OWNER SNAPSHOT REQUEST
    // =================================================

    useEffect(
        () => {

            if (!selectedProject) {
                return;
            }


            if (
                currentRole ===
                "OWNER"
            ) {
                return;
            }


            if (!isConnected) {
                return;
            }


            setWorkspaceSyncing(
                true
            );


            requestWorkspaceSnapshot();

        },
        [
            selectedProject?.id,
            currentRole,
            isConnected,
            requestWorkspaceSnapshot,
        ]
    );


    // =================================================
    // LOAD SELECTED FILE CONTENT
    // =================================================

    useEffect(
        () => {

            if (
                !selectedProject ||
                !selectedFile ||
                !token
            ) {
                return;
            }


            if (
                localWorkspaceActiveRef.current
            ) {
                return;
            }


            const path =
                normalizeWorkspacePath(
                    selectedFile.path
                );


            if (!path) {
                return;
            }


            const requestId =
                ++contentRequestIdRef.current;


            let cancelled =
                false;


            setWorkspaceSyncing(
                true
            );


            getWorkspaceFileContent(
                selectedProject.id,
                path,
                token
            )
                .then(
                    content => {

                        if (
                            cancelled ||
                            requestId !==
                            contentRequestIdRef.current
                        ) {
                            return;
                        }


                        const updatedFile =
                            makeExplorerFile(
                                path,
                                content
                            );


                        const exists =
                            filesRef.current.some(
                                file =>
                                    file.path === path
                            );


                        const updated =
                            exists
                                ? filesRef.current.map(
                                    file =>
                                        file.path === path
                                            ? updatedFile
                                            : file
                                )
                                : [
                                    ...filesRef.current,
                                    updatedFile,
                                ];


                        updated.sort(
                            (a, b) =>
                                a.path.localeCompare(
                                    b.path
                                )
                        );


                        filesRef.current =
                            updated;


                        setFiles(
                            updated
                        );


                        setSelectedFile(
                            current =>
                                current &&
                                current.path === path
                                    ? updatedFile
                                    : current
                        );


                        setError("");
                    }
                )
                .catch(
                    err => {

                        if (
                            cancelled ||
                            requestId !== contentRequestIdRef.current
                        ) {
                            return;
                        }


                        setError(
                            err instanceof Error
                                ? err.message
                                : `Failed to load "${path}".`
                        );
                    }
                )
                .finally(
                    () => {

                        if (
                            cancelled ||
                            requestId !== contentRequestIdRef.current
                        ) {
                            return;
                        }


                        setWorkspaceSyncing(
                            false
                        );
                    }
                );


            return () => {

                cancelled =
                    true;
            };

        },
        [
            selectedProject?.id,
            selectedFile?.path,
            token,
        ]
    );


    // =================================================
    // OPEN LOCAL FOLDER
    // =================================================

    const handleOpenFolder =
        async () => {

            if (
                currentRole ===
                "VIEWER"
            ) {

                setError(
                    "Viewers cannot open a workspace."
                );

                return;
            }


            try {

                setError("");


                const mode =
                    currentRole === "OWNER"
                        ? "OWNER"
                        : "COLLABORATOR";


                const name =
                    await openLocalFolder(
                        mode
                    );


                localWorkspaceActiveRef.current =
                    currentRole === "OWNER";


                setWorkspaceName(
                    name
                );


                const localWorkspace =
                    await loadWorkspace();


                const explorerFiles =
                    buildExplorerFiles(
                        localWorkspace.files ?? []
                    );


                const explorerFolders =
                    buildExplorerFolders(
                        localWorkspace.folders ?? []
                    );


                if (
                    currentRole ===
                    "OWNER"
                ) {

                    filesRef.current =
                        explorerFiles;

                    foldersRef.current =
                        explorerFolders;


                    setFiles(
                        explorerFiles
                    );

                    setFolders(
                        explorerFolders
                    );

                    setSelectedFile(
                        explorerFiles[0] ??
                        null
                    );


                    ownerImportedProjectRef.current =
                        null;


                    if (
                        selectedProject &&
                        token
                    ) {

                        setWorkspaceSyncing(
                            true
                        );


                        try {

                            const response =
                                await importWorkspace(
                                    selectedProject.id,

                                    {
                                        files:
                                            explorerFiles.map(
                                                file => ({
                                                    path:
                                                        normalizeWorkspacePath(
                                                            file.path
                                                        ),

                                                    name:
                                                        file.name,

                                                    content:
                                                        file.content ??
                                                        "",
                                                })
                                            ),

                                        folders:
                                            explorerFolders.map(
                                                folder =>
                                                    normalizeWorkspacePath(
                                                        folder
                                                    )
                                            ),
                                    },

                                    token
                                );


                            ownerImportedProjectRef.current =
                                selectedProject.id;


                            setWorkspaceSyncing(
                                false
                            );

                            setError("");

                            console.log(
                                "[APP] Owner initial import complete:",
                                response
                            );

                        } catch (err) {

                            ownerImportedProjectRef.current =
                                null;


                            setWorkspaceSyncing(
                                false
                            );


                            setError(
                                err instanceof Error
                                    ? err.message
                                    : "Failed to import workspace."
                            );
                        }
                    }

                } else {

                    if (
                        selectedProject &&
                        token
                    ) {

                        setWorkspaceSyncing(
                            true
                        );


                        try {

                            const snapshot =
                                await getWorkspace(
                                    selectedProject.id,
                                    token
                                );


                            handleWorkspaceSnapshot(
                                snapshot
                            );

                        } catch (err) {

                            setWorkspaceSyncing(
                                false
                            );


                            setError(
                                err instanceof Error
                                    ? err.message
                                    : "Failed to load shared workspace."
                            );
                        }
                    }
                }

            } catch (err) {

                if (
                    err instanceof DOMException &&
                    err.name ===
                        "AbortError"
                ) {
                    return;
                }


                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to open folder."
                );
            }
        };


    // =================================================
    // CREATE FILE
    // =================================================

    const handleCreateFile =
        async () => {

            if (
                currentRole ===
                "VIEWER"
            ) {
                return;
            }


            if (
                !localWorkspaceActiveRef.current
            ) {

                setError(
                    "Open a local folder first."
                );

                return;
            }


            const input =
                window.prompt(
                    "Enter file path",
                    "src/new-file.txt"
                );


            if (
                input === null
            ) {
                return;
            }


            const path =
                normalizeWorkspacePath(
                    input.trim()
                );


            if (!path) {

                setError(
                    "File path is required."
                );

                return;
            }


            try {

                await createWorkspaceFile(
                    path,
                    ""
                );


                const newFile =
                    makeExplorerFile(
                        path,
                        ""
                    );


                const updated =
                    filesRef.current.some(
                        file =>
                            file.path === path
                    )
                        ? filesRef.current
                        : [
                            ...filesRef.current,
                            newFile,
                        ];


                updated.sort(
                    (a, b) =>
                        a.path.localeCompare(
                            b.path
                        )
                );


                filesRef.current =
                    updated;


                setFiles(
                    updated
                );

                setSelectedFile(
                    newFile
                );


                const updatedFolders =
                    buildExplorerFolders([
                        ...foldersRef.current,
                        ...getParentFolders(
                            path
                        ),
                    ]);


                foldersRef.current =
                    updatedFolders;

                setFolders(
                    updatedFolders
                );


                sendWorkspaceFile(
                    path,
                    ""
                );

            } catch (err) {

                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to create file."
                );
            }
        };


    // =================================================
    // CREATE FOLDER
    // =================================================

    const handleCreateFolder =
        async () => {

            if (
                currentRole ===
                "VIEWER"
            ) {
                return;
            }


            if (
                !localWorkspaceActiveRef.current
            ) {

                setError(
                    "Open a local folder first."
                );

                return;
            }


            const input =
                window.prompt(
                    "Enter folder path",
                    "src/components"
                );


            if (
                input === null
            ) {
                return;
            }


            const path =
                normalizeWorkspacePath(
                    input.trim()
                );


            if (!path) {

                setError(
                    "Folder path is required."
                );

                return;
            }


            try {

                await createWorkspaceFolder(
                    path
                );


                const updatedFolders =
                    buildExplorerFolders([
                        ...foldersRef.current,
                        path,
                    ]);


                foldersRef.current =
                    updatedFolders;

                setFolders(
                    updatedFolders
                );


                sendWorkspaceFolder(
                    path
                );

            } catch (err) {

                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to create folder."
                );
            }
        };


    // =================================================
    // CODE CHANGE
    // =================================================

    const handleCodeChange =
        (
            value: string
        ) => {

            if (
                currentRole ===
                "VIEWER"
            ) {
                return;
            }


            if (!selectedFile) {
                return;
            }


            const path =
                selectedFile.path;


            const updatedFile =
                makeExplorerFile(
                    path,
                    value
                );


            const updated =
                filesRef.current.map(
                    file =>
                        file.path === path
                            ? updatedFile
                            : file
                );


            filesRef.current =
                updated;

            setFiles(
                updated
            );

            setSelectedFile(
                updatedFile
            );


            setIsDirty(
                true
            );

            sendChange(
                value
            );
        };


    // =================================================
    // DELETE FILE
    // =================================================

    const handleDeleteFile =
        async (
            file: ExplorerFile
        ) => {

            if (
                currentRole ===
                "VIEWER"
            ) {
                return;
            }


            if (
                !localWorkspaceActiveRef.current
            ) {

                setError(
                    "Open a local folder first."
                );

                return;
            }


            if (
                !window.confirm(
                    `Delete "${file.path}"?`
                )
            ) {
                return;
            }


            try {

                await deleteWorkspaceFile(
                    file.path
                );


                const updated =
                    filesRef.current.filter(
                        current =>
                            current.path !==
                            file.path
                    );


                filesRef.current =
                    updated;

                setFiles(
                    updated
                );


                setSelectedFile(
                    current =>
                        current?.path ===
                        file.path
                            ? updated[0] ?? null
                            : current
                );


                sendWorkspaceDelete(
                    file.path
                );

            } catch (err) {

                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to delete file."
                );
            }
        };


    // =================================================
    // DELETE FOLDER
    // =================================================

    const handleDeleteFolder =
        async (
            path: string
        ) => {

            if (
                currentRole ===
                "VIEWER"
            ) {
                return;
            }


            if (
                !localWorkspaceActiveRef.current
            ) {

                setError(
                    "Open a local folder first."
                );

                return;
            }


            if (
                !window.confirm(
                    `Delete folder "${path}" and everything inside it?`
                )
            ) {
                return;
            }


            try {

                await deleteWorkspaceFolder(
                    path
                );


                const prefix =
                    `${path}/`;


                const updatedFiles =
                    filesRef.current.filter(
                        file =>
                            file.path !== path &&
                            !file.path.startsWith(
                                prefix
                            )
                    );


                const updatedFolders =
                    foldersRef.current.filter(
                        folder =>
                            folder !== path &&
                            !folder.startsWith(
                                prefix
                            )
                    );


                filesRef.current =
                    updatedFiles;

                foldersRef.current =
                    updatedFolders;


                setFiles(
                    updatedFiles
                );

                setFolders(
                    updatedFolders
                );


                setSelectedFile(
                    current =>
                        current &&
                        (
                            current.path === path ||
                            current.path.startsWith(
                                prefix
                            )
                        )
                            ? updatedFiles[0] ?? null
                            : current
                );


                sendWorkspaceFolderDelete(
                    path
                );

            } catch (err) {

                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to delete folder."
                );
            }
        };
        // =================================================
    // RENAME FILE
    // =================================================

    const handleRenameFile =
        async (
            file: ExplorerFile
        ) => {

            if (currentRole === "VIEWER") {
                return;
            }

            if (!localWorkspaceActiveRef.current) {
                setError("Open a local folder first.");
                return;
            }

            const currentName = file.name;
            const input = window.prompt(
                `Rename file "${currentName}"`,
                currentName
            );

            if (input === null) {
                return;
            }

            const nextName = input.trim();

            if (!nextName) {
                setError("File name is required.");
                return;
            }

            const parts = file.path.split("/");
            parts.pop();

            const newPath =
                normalizeWorkspacePath(
                    parts.length > 0
                        ? `${parts.join("/")}/${nextName}`
                        : nextName
                );

            if (!newPath || newPath === file.path) {
                return;
            }

            if (filesRef.current.some(item => item.path === newPath)) {
                setError(`A file already exists at "${newPath}".`);
                return;
            }

            if (foldersRef.current.some(folder => folder === newPath)) {
                setError(`A folder already exists at "${newPath}".`);
                return;
            }

            try {
                await renameWorkspaceFile(
                    file.path,
                    newPath
                );

                const updated =
                    filesRef.current
                        .map(current =>
                            current.path === file.path
                                ? makeExplorerFile(
                                    newPath,
                                    current.content ?? ""
                                )
                                : current
                        )
                        .sort((a, b) => a.path.localeCompare(b.path));

                filesRef.current = updated;
                setFiles(updated);

                setSelectedFile(current =>
                    current?.path === file.path
                        ? makeExplorerFile(
                            newPath,
                            current.content ?? ""
                        )
                        : current
                );

                sendWorkspaceRename(
                    file.path,
                    newPath
                );

                setError("");
            } catch (err) {
                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to rename file."
                );
            }
        };


    // =================================================
    // RENAME FOLDER
    // =================================================

    const handleRenameFolder =
        async (
            oldPath: string
        ) => {

            if (currentRole === "VIEWER") {
                return;
            }

            if (!localWorkspaceActiveRef.current) {
                setError("Open a local folder first.");
                return;
            }

            const parts = oldPath.split("/");
            const currentName = parts.pop() ?? oldPath;

            const input = window.prompt(
                `Rename folder "${currentName}"`,
                currentName
            );

            if (input === null) {
                return;
            }

            const nextName = input.trim();

            if (!nextName) {
                setError("Folder name is required.");
                return;
            }

            const parent = parts.join("/");
            const newPath =
                normalizeWorkspacePath(
                    parent
                        ? `${parent}/${nextName}`
                        : nextName
                );

            if (!newPath || newPath === oldPath) {
                return;
            }

            const oldPrefix = `${oldPath}/`;
            const newPrefix = `${newPath}/`;

            if (
                newPath.startsWith(oldPrefix)
            ) {
                setError("A folder cannot be moved inside itself.");
                return;
            }

            if (
                filesRef.current.some(
                    file =>
                        file.path === newPath ||
                        file.path.startsWith(newPrefix)
                )
            ) {
                setError(`A file already exists at or inside "${newPath}".`);
                return;
            }

            if (
                foldersRef.current.some(
                    folder =>
                        folder === newPath ||
                        folder.startsWith(newPrefix)
                )
            ) {
                setError(`A folder already exists at or inside "${newPath}".`);
                return;
            }

            try {
                await renameWorkspaceFolder(
                    oldPath,
                    newPath
                );

                const updatedFiles =
                    filesRef.current
                        .map(file => {
                            if (file.path === oldPath) {
                                return file;
                            }

                            if (file.path.startsWith(oldPrefix)) {
                                return makeExplorerFile(
                                    `${newPath}${file.path.slice(oldPath.length)}`,
                                    file.content ?? ""
                                );
                            }

                            return file;
                        })
                        .sort((a, b) => a.path.localeCompare(b.path));

                const updatedFolders =
                    foldersRef.current
                        .map(folder => {
                            if (folder === oldPath) {
                                return newPath;
                            }

                            if (folder.startsWith(oldPrefix)) {
                                return `${newPath}${folder.slice(oldPath.length)}`;
                            }

                            return folder;
                        })
                        .sort((a, b) => a.localeCompare(b));

                filesRef.current = updatedFiles;
                foldersRef.current = updatedFolders;

                setFiles(updatedFiles);
                setFolders(updatedFolders);

                setSelectedFile(current => {
                    if (!current) {
                        return current;
                    }

                    if (current.path.startsWith(oldPrefix)) {
                        return makeExplorerFile(
                            `${newPath}${current.path.slice(oldPath.length)}`,
                            current.content ?? ""
                        );
                    }

                    return current;
                });

                sendWorkspaceRename(
                    oldPath,
                    newPath
                );

                setError("");
            } catch (err) {
                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to rename folder."
                );
            }
        };


    // =================================================
// SAVE TO DEVICE
// =================================================

const handleSaveToDevice =
    useCallback(
        async () => {

            if (
                currentRole ===
                "VIEWER"
            ) {

                setError(
                    "Viewers cannot save files."
                );

                return;
            }


            if (
                files.length === 0
            ) {

                setError(
                    "No files available to save."
                );

                return;
            }


            if (
                saving
            ) {
                return;
            }


            try {

                setSaving(
                    true
                );

                setError("");


                const savedName =
                    await saveProjectToDevice(
                        files.map(
                            file => ({
                                path:
                                    file.path,

                                content:
                                    file.content ??
                                    "",
                            })
                        )
                    );


                /*
                 * Saving to the local workspace
                 * succeeded.
                 */

                localWorkspaceActiveRef.current =
                    true;


                setWorkspaceName(
                    savedName
                );


                setIsDirty(
                    false
                );


                setError("");

            } catch (
                err
            ) {

                /*
                 * User cancelled the
                 * folder picker.
                 */

                if (
                    err instanceof DOMException &&
                    err.name ===
                        "AbortError"
                ) {

                    return;
                }


                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to save workspace."
                );

            } finally {

                setSaving(
                    false
                );
            }

        },
        [
            currentRole,
            files,
            saving,
        ]
    );
    // =================================================
// KEYBOARD SAVE
// =================================================

useEffect(
    () => {

        const handleKeyboardSave =
            (
                event: KeyboardEvent
            ) => {

                if (
                    (
                        event.ctrlKey ||
                        event.metaKey
                    ) &&
                    event.key.toLowerCase() ===
                        "s"
                ) {

                    event.preventDefault();

                    void handleSaveToDevice();
                }
            };


        window.addEventListener(
            "keydown",
            handleKeyboardSave
        );


        return () => {

            window.removeEventListener(
                "keydown",
                handleKeyboardSave
            );
        };

    },
    [
        handleSaveToDevice,
    ]
);
        


    // =================================================
    // CHAT SEND
    // =================================================

    const handleSendChat =
        (
            content: string
        ) => {

            const clean =
                content.trim();


            if (!clean) {
                return;
            }


            sendMessage(
                clean
            );
        };


    // =================================================
    // LOGOUT
    // =================================================

    const handleLogout =
        () => {

            contentRequestIdRef.current += 1;

            clearWorkspace();


            localWorkspaceActiveRef.current =
                false;


            ownerImportedProjectRef.current =
                null;


            localStorage.removeItem(
                "token"
            );

            localStorage.removeItem(
                "username"
            );


            setAuthenticated(
                false
            );

            setAuthScreen(
                "login"
            );

            setSelectedProject(
                null
            );

            setFiles([]);

            setFolders([]);

            setSelectedFile(
                null
            );

            setMembers([]);

            setCurrentRole(
                null
            );

            setChatMessages([]);

            setWorkspaceName(
                null
            );

            setError("");
        };


    // =================================================
    // BACK
    // =================================================

    const handleBack =
        () => {

            contentRequestIdRef.current += 1;

            clearWorkspace();


            localWorkspaceActiveRef.current =
                false;


            ownerImportedProjectRef.current =
                null;


            setSelectedProject(
                null
            );

            setFiles([]);

            setFolders([]);

            setSelectedFile(
                null
            );

            setMembers([]);

            setCurrentRole(
                null
            );

            setChatMessages([]);

            setWorkspaceName(
                null
            );

            setError("");
        };


    // =================================================
    // LOGIN
    // =================================================

    if (!authenticated) {

        if (authScreen === "register") {

            return (
                <Register
                    onBackToLogin={() =>
                        setAuthScreen(
                            "login"
                        )
                    }
                />
            );
        }

        return (
            <Login
                onLogin={() =>
                    setAuthenticated(
                        true
                    )
                }

                onRegister={() =>
                    setAuthScreen(
                        "register"
                    )
                }
            />
        );
    }


    // =================================================
    // DASHBOARD
    // =================================================

    if (!selectedProject) {

        return (
            <Dashboard
                onOpenProject={
                    project =>
                        setSelectedProject(
                            project
                        )
                }
            />
        );
    }


    // =================================================
    // SELECT FILE
    // =================================================

    const handleSelectFile =
        (
            file:
                ExplorerFile
        ) => {

            setError("");

            setSelectedFile(
                file
            );
        };


    // =================================================
    // UI
    // =================================================

    return (
        <div className="app">

            {/* =================================================
                TOPBAR
            ================================================= */}

            <header className="topbar">

                <div className="topbar-brand">

                    <div className="brand-mark">
                        <Code2
                            size={15}
                            strokeWidth={2.4}
                        />
                    </div>

                    <div className="brand-copy">

                        <span className="brand-name">
                            Code Break
                        </span>

                        <span className="brand-divider">
                            /
                        </span>

                        <span className="brand-project">
                            {selectedProject.name}
                        </span>

                    </div>

                </div>


                <button
                    type="button"
                    className="back-button"
                    onClick={
                        handleBack
                    }
                    title="Back to projects"
                >
                    <ArrowLeft
                        size={15}
                    />

                    <span>
                        Projects
                    </span>
                </button>


                <div className="topbar-spacer" />


                <div className="topbar-workspace">

                    <div className="workspace-mode-pill">

                        <Code2
                            size={13}
                        />

                        <span>
                            Shared Workspace
                        </span>

                    </div>

                    <div className="workspace-live-pill">

                        <span className="live-dot" />

                        <span>
                            {isConnected
                                ? "Live"
                                : "Connecting"}
                        </span>

                    </div>

                    <div className="workspace-role-pill">

                        <Circle
                            size={7}
                            fill="currentColor"
                        />

                        <span>
                            {getRoleLabel(currentRole)}
                        </span>

                    </div>

                </div>

                <button
    type="button"
    className={
        isDirty
            ? "topbar-save-button topbar-save-button-dirty"
            : "topbar-save-button"
    }
    onClick={
        () => {
            void handleSaveToDevice();
        }
    }
    disabled={
        currentRole === "VIEWER" ||
        saving ||
        files.length === 0
    }
    title={
        currentRole === "VIEWER"
            ? "Viewers cannot save"
            : "Save workspace (Ctrl+S)"
    }
>

    {saving ? (
        <span className="save-spinner" />
    ) : (
        <Save
            size={14}
            strokeWidth={2}
        />
    )}

    <span>
        {saving
            ? "Saving..."
            : "Save"}
    </span>

    {!saving && (
        <kbd>
            Ctrl S
        </kbd>
    )}

</button>


                <div className="topbar-divider" />


                <div className="user">

                    <div
                        className={
                            `role-badge role-${(
                                currentRole ??
                                "viewer"
                            ).toLowerCase()}`
                        }
                    >

                        <Circle
                            size={7}
                            fill="currentColor"
                        />

                        <span>
                            {
                                getRoleLabel(
                                    currentRole
                                )
                            }
                        </span>

                    </div>


                    <div className="user-avatar">
                        {
                            username
                                .charAt(0)
                                .toUpperCase()
                        }
                    </div>


                    <span className="user-name">
                        {username}
                    </span>

                </div>


                <button
                    type="button"
                    className="topbar-icon-button"
                    title="Search"
                    aria-label="Search"
                >
                    <Search
                        size={17}
                    />
                </button>

                <button
                    type="button"
                    className="topbar-icon-button notification-button"
                    title="Notifications"
                    aria-label="Notifications"
                >
                    <Bell
                        size={16}
                    />

                    <span className="notification-dot" />
                </button>

                <button
                    type="button"
                    className="topbar-icon-button"
                    title="Settings"
                    aria-label="Settings"
                >
                    <Settings2
                        size={16}
                    />
                </button>


                <button
                    type="button"
                    className="logout-button"
                    onClick={
                        handleLogout
                    }
                    title="Logout"
                >
                    <LogOut
                        size={15}
                    />
                </button>

            </header>


            {/* =================================================
                ERROR
            ================================================= */}

            {error && (
                <div className="error-banner">

                    <div className="error-content">

                        <X
                            size={15}
                        />

                        <span>
                            {error}
                        </span>

                    </div>


                    <button
                        type="button"
                        onClick={() =>
                            setError("")
                        }
                        title="Dismiss"
                        aria-label="Dismiss error"
                    >
                        <X
                            size={15}
                        />
                    </button>

                </div>
            )}


            {/* =================================================
                MAIN WORKSPACE
            ================================================= */}

            <main className="workspace">

                {/* =============================================
                    ACTIVITY BAR
                ============================================= */}

                <ActivityBar
                    activePanel={
                        activePanel
                    }

                    onPanelChange={
                        setActivePanel
                    }
                />


                {/* =============================================
                    EXPLORER
                ============================================= */}

                <div className="workspace-panel explorer-panel-visible">

                    <FileExplorer
                        files={
                            files
                        }

                        folders={
                            folders
                        }

                        selectedFile={
                            selectedFile?.path ??
                            ""
                        }

                        workspaceName={
                            workspaceName
                        }

                        onSelectFile={
                            handleSelectFile
                        }

                        onOpenFolder={
                            handleOpenFolder
                        }

                        onCreateFile={
                            handleCreateFile
                        }

                        onCreateFolder={
                            handleCreateFolder
                        }

                        onDeleteFile={
                            handleDeleteFile
                        }

                        onDeleteFolder={
                            handleDeleteFolder
                        }

                        onRenameFile={
                            handleRenameFile
                        }

                        onRenameFolder={
                            handleRenameFolder
                        }

                        readOnly={
                            currentRole ===
                            "VIEWER"
                        }
                    />

                </div>


                {/* =============================================
                    EDITOR
                ============================================= */}

                <section className="editor-container">

                    <div className="editor-tab">

                        <div className="editor-tab-left">

                            {selectedFile ? (
                                <FileCode2
                                    size={14}
                                    className="editor-tab-icon"
                                />
                            ) : (
                                <Code2
                                    size={14}
                                    className="editor-tab-icon"
                                />
                            )}


                            <span className="editor-tab-name">
                                {
                                    selectedFile?.name ??
                                    "Welcome"
                                }
                            </span>


                            {selectedFile && (
                                <span className="editor-tab-path">
                                    {
                                        selectedFile.path
                                    }
                                </span>
                            )}

                        </div>


 {selectedFile && (
    <div className="editor-tab-right">

        <span
            className={
                isDirty
                    ? "editor-save-state editor-save-state-dirty"
                    : "editor-save-state"
            }
        >

            {saving ? (
                <>
                    <Save
                        size={12}
                    />

                    Saving...
                </>
            ) : isDirty ? (
                <>
                    <Circle
                        size={7}
                        fill="currentColor"
                    />

                    Unsaved changes
                </>
            ) : (
                <>
                    <Check
                        size={12}
                    />

                    Saved
                </>
            )}

        </span>

    </div>
)}

                    </div>


                    {selectedFile && (
                        <div className="editor-breadcrumb">

                            {getBreadcrumbParts(
                                selectedProject.name,
                                selectedFile.path
                            ).map(
                                (part, index, parts) => (
                                    <span
                                        key={`${part}-${index}`}
                                        className={
                                            index === parts.length - 1
                                                ? "breadcrumb-current"
                                                : "breadcrumb-part"
                                        }
                                    >
                                        {part}

                                        {index < parts.length - 1 && (
                                            <span className="breadcrumb-separator">
                                                ›
                                            </span>
                                        )}
                                    </span>
                                )
                            )}

                        </div>
                    )}

                    <div className="editor">

                        {selectedFile ? (

                            <CodeEditor
                                value={
                                    selectedFile.content
                                }

                                onChange={
                                    handleCodeChange
                                }

                                readOnly={
                                    currentRole ===
                                    "VIEWER"
                                }
                            />

                        ) : (

                            <div className="empty-editor">

                                <div className="empty-editor-logo">

                                    <Zap
                                        size={30}
                                        strokeWidth={1.6}
                                    />

                                </div>


                                <h2>
                                    Code Break
                                </h2>


                                <p>
                                    Select a file from the
                                    explorer to start coding.
                                </p>


                                <div className="empty-editor-hint">

                                    <span>
                                        <FolderOpen
                                            size={13}
                                        />

                                        Open a workspace
                                    </span>

                                    <span>
                                        <Code2
                                            size={13}
                                        />

                                        Start building
                                    </span>

                                </div>

                            </div>
                        )}

                    </div>

                    {selectedFile && (
                        <div className="editor-statusbar">

                            <div className="editor-status-left">

                                <span className="editor-status-connected">
                                    <span className="status-dot" />

                                    {isConnected
                                        ? "Connected • Live"
                                        : "Connecting..."}
                                </span>

                                <span>
                                    0 Problems
                                </span>

                                <span>
                                    0 Warnings
                                </span>

                                <span>
                                    main*
                                </span>

                            </div>

                            <div className="editor-status-right">

                                <span>
                                    Ln 1, Col 1
                                </span>

                                <span>
                                    Spaces: 4
                                </span>

                                <span>
                                    UTF-8
                                </span>

                                <span>
                                    Java 17
                                </span>

                                <span className="editor-status-prettier">
                                    <Check
                                        size={10}
                                    />

                                    Prettier • Ready
                                </span>

                            </div>

                        </div>
                    )}

                </section>


                {/* =============================================
                    COLLABORATION
                ============================================= */}

                <aside className="collaboration-panel collaboration-panel-visible">

                    <div className="collaboration-heading">

                        <div className="collaboration-heading-title">

                            <div className="collaboration-heading-icon">
                                <Users
                                    size={15}
                                />
                            </div>

                            <div>

                                <h2>
                                    Collaboration
                                </h2>

                                <span>
                                    Team workspace
                                </span>

                            </div>

                        </div>


                        <div className="collaboration-live">

                            <span className="live-dot" />

                            Live

                        </div>

                    </div>


                    <div className="members-panel-wrapper">

                        <MembersPanel
                            projectId={
                                selectedProject.id
                            }

                            currentUsername={
                                username
                            }

                            onCurrentRoleChange={
                                setCurrentRole
                            }
                        />

                    </div>


                    <section className="chat-panel">

                        <div className="chat-header">

                            <div className="chat-title">

                                <div className="chat-icon">
                                    <MessageSquare
                                        size={15}
                                    />
                                </div>


                                <div>

                                    <h3>
                                        Team Chat
                                    </h3>

                                    <span>
                                        Real-time conversation
                                    </span>

                                </div>

                            </div>


                            <div className="chat-online">
                                <span className="live-dot" />
                                Online
                            </div>

                        </div>


                        <div className="chat-messages">

                            {chatMessages.length === 0 ? (

                                <div className="chat-empty">

                                    <div className="chat-empty-icon">
                                        <MessageSquare
                                            size={21}
                                        />
                                    </div>


                                    <p>
                                        No messages yet
                                    </p>


                                    <span>
                                        Start a conversation
                                        with your team.
                                    </span>

                                </div>

                            ) : (

                                chatMessages.map(
                                    (
                                        message,
                                        index
                                    ) => (

                                        <div
                                            key={
                                                `${message.username}-${index}`
                                            }
                                            className={
                                                message.username ===
                                                username
                                                    ? "chat-message own"
                                                    : "chat-message"
                                            }
                                        >

                                            <div className="chat-message-top">

                                                <span className="chat-username">
                                                    {
                                                        message.username
                                                    }
                                                </span>


                                                {
                                                    message.username ===
                                                    username && (
                                                        <span className="chat-you">
                                                            You
                                                        </span>
                                                    )
                                                }

                                            </div>


                                            <div className="chat-content">

                                                {
                                                    message.content
                                                }

                                            </div>

                                        </div>
                                    )
                                )
                            )}

                        </div>


                        <form
                            className="chat-input-container"
                            onSubmit={
                                event => {

                                    event.preventDefault();


                                    const form =
                                        event.currentTarget;


                                    const input =
                                        form.elements.namedItem(
                                            "message"
                                        ) as HTMLInputElement;


                                    handleSendChat(
                                        input.value
                                    );


                                    input.value =
                                        "";
                                }
                            }
                        >

                            <input
                                name="message"
                                type="text"
                                placeholder="Message your team..."
                                autoComplete="off"
                            />


                            <button
                                type="submit"
                                className="chat-send-button"
                                title="Send message"
                                aria-label="Send message"
                            >
                                <Send
                                    size={14}
                                />
                            </button>

                        </form>

                    </section>

                </aside>

            </main>


            {/* =================================================
                STATUS BAR
            ================================================= */}

            <footer className="statusbar">

                <div className="status-left">

                    <span className="status-connection">

                        <span className="status-dot" />

                        {
                            isConnected
                                ? "Connected • Live"
                                : "Connecting..."
                        }

                    </span>

                    <span>
                        {files.length} files
                    </span>

                    <span>
                        {folders.length} folders
                    </span>

                </div>


                <div className="status-right">

                    <span>
                        {workspaceSyncing
                            ? "Syncing workspace..."
                            : "Workspace healthy"}
                    </span>

                    <span>
                        {getRoleLabel(currentRole)}
                    </span>

                    <span className="status-brand">
                        Code Break
                    </span>

                </div>

            </footer>

        </div>
    );
}


export default App;