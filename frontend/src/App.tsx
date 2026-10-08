import {
    useCallback,
    useEffect,
    useRef,
    useState,
} from "react";

import {
    ArrowLeft,
    Bell,
    Check,
    Circle,
    Code2,
    FileCode2,
    FolderOpen,
    LogOut,
    MessageSquare,
    Save,
    Search,
    Send,
    Settings2,
    Users,
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
import {
    getProjectMembers,
    type Project,
    type ProjectRole,
} from "./project/projectApi";

import {
    useChat,
    type ChatMessage,
} from "./collaboration/useChat";

import {
    useCollaboration,
    type CollaborationMessage,
    type RoleChangedMessage,
    type WorkspaceMessage,
    type WorkspaceSnapshot,
} from "./collaboration/useCollaboration";

import {
    getWorkspace,
    getWorkspaceFileContent,
    importWorkspace,
} from "./workspace/workspaceApi";

import {
    clearWorkspace,
    createWorkspaceFile,
    createWorkspaceFolder,
    deleteWorkspaceFile,
    deleteWorkspaceFolder,
    getWorkspaceName,
    loadWorkspace,
    normalizeWorkspacePath,
    openLocalFolder,
    renameWorkspaceFile,
    renameWorkspaceFolder,
    saveProjectToDevice,
} from "./workspace/localWorkspace";

import "./App.css";

function makeExplorerFile(
    path: string,
    content = "",
): ExplorerFile {
    const normalized = normalizeWorkspacePath(path);
    const parts = normalized.split("/").filter(Boolean);

    return {
        path: normalized,
        name: parts[parts.length - 1] ?? normalized,
        content: content ?? "",
    };
}

function buildExplorerFiles(
    sourceFiles: Array<{
        path: string;
        content?: string;
    }> = [],
): ExplorerFile[] {
    return sourceFiles
        .map((file) =>
            makeExplorerFile(
                file.path,
                file.content ?? "",
            ),
        )
        .filter((file) => Boolean(file.path))
        .sort((a, b) => a.path.localeCompare(b.path));
}

function buildExplorerFolders(
    sourceFolders: Array<string | { path: string }> = [],
): string[] {
    const result = new Set<string>();

    for (const folder of sourceFolders) {
        const rawPath =
            typeof folder === "string"
                ? folder
                : folder.path;

        const normalized =
            normalizeWorkspacePath(rawPath);

        if (!normalized) {
            continue;
        }

        const parts = normalized
            .split("/")
            .filter(Boolean);

        let current = "";

        for (const part of parts) {
            current = current
                ? `${current}/${part}`
                : part;

            result.add(current);
        }
    }

    return Array.from(result).sort((a, b) =>
        a.localeCompare(b),
    );
}

function getParentFolders(path: string): string[] {
    const normalized = normalizeWorkspacePath(path);

    if (!normalized) {
        return [];
    }

    const parts = normalized
        .split("/")
        .filter(Boolean);

    parts.pop();

    const result: string[] = [];
    let current = "";

    for (const part of parts) {
        current = current
            ? `${current}/${part}`
            : part;

        result.push(current);
    }

    return result;
}

function getRoleLabel(
    role: ProjectRole | null,
): string {
    if (!role) {
        return "Loading";
    }

    return (
        role.charAt(0) +
        role.slice(1).toLowerCase()
    );
}

function getBreadcrumbParts(
    projectName: string,
    path: string | null,
): string[] {
    const normalized = path
        ? normalizeWorkspacePath(path)
        : "";

    return [
        projectName || "shared-workspace",
        ...(normalized
            ? normalized.split("/").filter(Boolean)
            : []),
    ];
}

function App() {
    const [authenticated, setAuthenticated] =
        useState<boolean>(
            Boolean(localStorage.getItem("token")),
        );

    const [authScreen, setAuthScreen] =
        useState<"login" | "register">("login");

    const [selectedProject, setSelectedProject] =
        useState<Project | null>(null);

    const [files, setFiles] =
        useState<ExplorerFile[]>([]);

    const [folders, setFolders] =
        useState<string[]>([]);

    const [selectedFile, setSelectedFile] =
        useState<ExplorerFile | null>(null);

    const [currentRole, setCurrentRole] =
        useState<ProjectRole | null>(null);

    const [activePanel, setActivePanel] =
        useState<
            "explorer" | "collaboration"
        >("explorer");

    const [chatMessages, setChatMessages] =
        useState<ChatMessage[]>([]);

    const [workspaceName, setWorkspaceName] =
        useState<string | null>(
            getWorkspaceName(),
        );

    const [workspaceSyncing, setWorkspaceSyncing] =
        useState(false);

    const [saving, setSaving] =
        useState(false);

    const [isDirty, setIsDirty] =
        useState(false);

    const [error, setError] =
        useState("");

    const filesRef =
        useRef<ExplorerFile[]>([]);

    const foldersRef =
        useRef<string[]>([]);

    const localWorkspaceActiveRef =
        useRef(false);

    const ownerImportedProjectRef =
        useRef<number | null>(null);

    const contentRequestIdRef =
        useRef(0);

    const token =
        localStorage.getItem("token");

    const username =
        localStorage.getItem("username") ??
        "anonymous";

    const syncFiles = useCallback(
        (nextFiles: ExplorerFile[]) => {
            const sorted = [...nextFiles].sort(
                (a, b) =>
                    a.path.localeCompare(b.path),
            );

            filesRef.current = sorted;
            setFiles(sorted);

            return sorted;
        },
        [],
    );

    const syncFolders = useCallback(
        (nextFolders: string[]) => {
            const sorted = [...new Set(nextFolders)]
                .filter(Boolean)
                .sort((a, b) =>
                    a.localeCompare(b),
                );

            foldersRef.current = sorted;
            setFolders(sorted);

            return sorted;
        },
        [],
    );

    const handleChatMessage =
        useCallback(
            (message: ChatMessage) => {
                if (
                    !selectedProject ||
                    message.projectId !==
                        selectedProject.id
                ) {
                    return;
                }

                setChatMessages((current) =>
                    [...current, message].slice(-100),
                );
            },
            [selectedProject],
        );

    const handleRemoteChange =
        useCallback(
            (message: CollaborationMessage) => {
                if (
                    !selectedProject ||
                    message.projectId !==
                        selectedProject.id ||
                    !message.filePath
                ) {
                    return;
                }

                const path =
                    normalizeWorkspacePath(
                        message.filePath,
                    );

                if (!path) {
                    return;
                }

                const nextFile =
                    makeExplorerFile(
                        path,
                        message.content ?? "",
                    );

                const exists =
                    filesRef.current.some(
                        (file) =>
                            file.path === path,
                    );

                syncFiles(
                    exists
                        ? filesRef.current.map(
                              (file) =>
                                  file.path === path
                                      ? nextFile
                                      : file,
                          )
                        : [
                              ...filesRef.current,
                              nextFile,
                          ],
                );

                setSelectedFile((current) =>
                    current?.path === path
                        ? nextFile
                        : current,
                );
            },
            [selectedProject, syncFiles],
        );

    const handleWorkspaceMessage =
        useCallback(
            (message: WorkspaceMessage) => {
                if (
                    !selectedProject ||
                    message.projectId !==
                        selectedProject.id ||
                    !message.filePath
                ) {
                    return;
                }

                const path =
                    normalizeWorkspacePath(
                        message.filePath,
                    );

                if (!path) {
                    return;
                }

                if (
                    message.type ===
                    "WORKSPACE_FILE"
                ) {
                    const nextFile =
                        makeExplorerFile(
                            path,
                            message.content ?? "",
                        );

                    const exists =
                        filesRef.current.some(
                            (file) =>
                                file.path === path,
                        );

                    syncFiles(
                        exists
                            ? filesRef.current.map(
                                  (file) =>
                                      file.path === path
                                          ? nextFile
                                          : file,
                              )
                            : [
                                  ...filesRef.current,
                                  nextFile,
                              ],
                    );

                    syncFolders(
                        buildExplorerFolders([
                            ...foldersRef.current,
                            ...getParentFolders(path),
                        ]),
                    );

                    return;
                }

                if (
                    message.type ===
                    "WORKSPACE_FOLDER"
                ) {
                    syncFolders(
                        buildExplorerFolders([
                            ...foldersRef.current,
                            path,
                        ]),
                    );

                    return;
                }

                if (
                    message.type ===
                    "WORKSPACE_RENAME"
                ) {
                    const oldPath = path;
                    const newPath =
                        normalizeWorkspacePath(
                            message.content ?? "",
                        );

                    if (!newPath) {
                        return;
                    }

                    const prefix =
                        `${oldPath}/`;

                    const nextFiles =
                        filesRef.current.map(
                            (file) => {
                                if (
                                    file.path ===
                                    oldPath
                                ) {
                                    return makeExplorerFile(
                                        newPath,
                                        file.content,
                                    );
                                }

                                if (
                                    file.path.startsWith(
                                        prefix,
                                    )
                                ) {
                                    return makeExplorerFile(
                                        `${newPath}${file.path.slice(
                                            oldPath.length,
                                        )}`,
                                        file.content,
                                    );
                                }

                                return file;
                            },
                        );

                    const nextFolders =
                        foldersRef.current.map(
                            (folder) => {
                                if (
                                    folder ===
                                    oldPath
                                ) {
                                    return newPath;
                                }

                                if (
                                    folder.startsWith(
                                        prefix,
                                    )
                                ) {
                                    return `${newPath}${folder.slice(
                                        oldPath.length,
                                    )}`;
                                }

                                return folder;
                            },
                        );

                    syncFiles(nextFiles);
                    syncFolders(nextFolders);

                    setSelectedFile((current) => {
                        if (!current) {
                            return current;
                        }

                        if (
                            current.path ===
                            oldPath
                        ) {
                            return makeExplorerFile(
                                newPath,
                                current.content,
                            );
                        }

                        if (
                            current.path.startsWith(
                                prefix,
                            )
                        ) {
                            return makeExplorerFile(
                                `${newPath}${current.path.slice(
                                    oldPath.length,
                                )}`,
                                current.content,
                            );
                        }

                        return current;
                    });

                    return;
                }

                if (
                    message.type ===
                    "WORKSPACE_DELETE"
                ) {
                    const nextFiles =
                        filesRef.current.filter(
                            (file) =>
                                file.path !== path,
                        );

                    syncFiles(nextFiles);

                    setSelectedFile((current) =>
                        current?.path === path
                            ? nextFiles[0] ?? null
                            : current,
                    );

                    return;
                }

                if (
                    message.type ===
                    "WORKSPACE_FOLDER_DELETE"
                ) {
                    const prefix = `${path}/`;

                    const nextFiles =
                        filesRef.current.filter(
                            (file) =>
                                file.path !== path &&
                                !file.path.startsWith(
                                    prefix,
                                ),
                        );

                    const nextFolders =
                        foldersRef.current.filter(
                            (folder) =>
                                folder !== path &&
                                !folder.startsWith(
                                    prefix,
                                ),
                        );

                    syncFiles(nextFiles);
                    syncFolders(nextFolders);

                    setSelectedFile((current) =>
                        current &&
                        (
                            current.path === path ||
                            current.path.startsWith(
                                prefix,
                            )
                        )
                            ? nextFiles[0] ?? null
                            : current,
                    );
                }
            },
            [
                selectedProject,
                syncFiles,
                syncFolders,
            ],
        );

    const handleWorkspaceSnapshot =
        useCallback(
            (snapshot: WorkspaceSnapshot) => {
                if (
                    !selectedProject ||
                    snapshot.projectId !==
                        selectedProject.id
                ) {
                    return;
                }

                const nextFiles =
                    buildExplorerFiles(
                        snapshot.files ?? [],
                    );

                const nextFolders =
                    buildExplorerFolders(
                        snapshot.folders ?? [],
                    );

                syncFiles(nextFiles);
                syncFolders(nextFolders);

                setSelectedFile((current) => {
                    if (!current) {
                        return nextFiles[0] ?? null;
                    }

                    return (
                        nextFiles.find(
                            (file) =>
                                file.path ===
                                current.path,
                        ) ??
                        nextFiles[0] ??
                        null
                    );
                });

                if (
                    !localWorkspaceActiveRef.current
                ) {
                    setWorkspaceName(
                        (current) =>
                            current ??
                            "Shared Workspace",
                    );
                }

                setWorkspaceSyncing(false);
                setError("");
            },
            [
                selectedProject,
                syncFiles,
                syncFolders,
            ],
        );

    const handleRoleChange =
        useCallback(
            (message: RoleChangedMessage) => {
                if (
                    !selectedProject ||
                    message.projectId !==
                        selectedProject.id
                ) {
                    return;
                }

                if (
                    message.username ===
                    username
                ) {
                    const role =
                        message.role as ProjectRole;

                    setCurrentRole(role);

                    if (role === "OWNER") {
                        ownerImportedProjectRef.current =
                            null;
                    }
                }
            },
            [selectedProject, username],
        );

    const {
        sendChange,
        sendWorkspaceFile,
        sendWorkspaceFolder,
        sendWorkspaceDelete,
        sendWorkspaceFolderDelete,
        sendWorkspaceRename,
        requestWorkspaceSnapshot,
        isConnected,
    } = useCollaboration({
        projectId:
            selectedProject?.id ?? null,
        filePath:
            selectedFile?.path ?? null,
        onRemoteChange:
            handleRemoteChange,
        onWorkspaceFile:
            handleWorkspaceMessage,
        onWorkspaceSnapshot:
            handleWorkspaceSnapshot,
        onRoleChange:
            handleRoleChange,
    });

    const { sendMessage } =
        useChat({
            projectId:
                selectedProject?.id ?? null,
            onMessage:
                handleChatMessage,
        });

    useEffect(() => {
        if (
            !selectedProject ||
            !token
        ) {
            setCurrentRole(null);
            return;
        }

        let cancelled = false;

        getProjectMembers(
            selectedProject.id,
            token,
        )
            .then((members) => {
                if (cancelled) {
                    return;
                }

                const me = members.find(
                    (member) =>
                        member.username ===
                        username,
                );

                setCurrentRole(
                    me?.role ?? null,
                );
            })
            .catch((err: unknown) => {
                if (cancelled) {
                    return;
                }

                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to load project members.",
                );
            });

        return () => {
            cancelled = true;
        };
    }, [
        selectedProject?.id,
        token,
        username,
    ]);

    useEffect(() => {
        contentRequestIdRef.current += 1;

        filesRef.current = [];
        foldersRef.current = [];

        setFiles([]);
        setFolders([]);
        setSelectedFile(null);
        setChatMessages([]);
        setCurrentRole(null);
        setSaving(false);
        setIsDirty(false);
        setWorkspaceSyncing(false);
        setWorkspaceName(getWorkspaceName());
        setError("");

        localWorkspaceActiveRef.current = false;
        ownerImportedProjectRef.current = null;
    }, [selectedProject?.id]);

    useEffect(() => {
        if (
            !selectedProject ||
            !token ||
            currentRole === "OWNER" ||
            localWorkspaceActiveRef.current
        ) {
            return;
        }

        let cancelled = false;

        setWorkspaceSyncing(true);

        getWorkspace(
            selectedProject.id,
            token,
        )
            .then((snapshot) => {
                if (!cancelled) {
                    handleWorkspaceSnapshot(
                        snapshot,
                    );
                }
            })
            .catch((err: unknown) => {
                if (cancelled) {
                    return;
                }

                setWorkspaceSyncing(false);
                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to load workspace.",
                );
            });

        return () => {
            cancelled = true;
        };
    }, [
        selectedProject?.id,
        currentRole,
        token,
        handleWorkspaceSnapshot,
    ]);

    useEffect(() => {
        if (
            !selectedProject ||
            currentRole === "OWNER" ||
            !isConnected
        ) {
            return;
        }

        setWorkspaceSyncing(true);
        requestWorkspaceSnapshot();
    }, [
        selectedProject?.id,
        currentRole,
        isConnected,
        requestWorkspaceSnapshot,
    ]);

    useEffect(() => {
        if (
            !selectedProject ||
            !selectedFile ||
            !token ||
            localWorkspaceActiveRef.current
        ) {
            return;
        }

        const path =
            normalizeWorkspacePath(
                selectedFile.path,
            );

        if (!path) {
            return;
        }

        const requestId =
            ++contentRequestIdRef.current;

        let cancelled = false;

        setWorkspaceSyncing(true);

        getWorkspaceFileContent(
            selectedProject.id,
            path,
            token,
        )
            .then((content) => {
                if (
                    cancelled ||
                    requestId !==
                        contentRequestIdRef.current
                ) {
                    return;
                }

                const nextFile =
                    makeExplorerFile(
                        path,
                        content,
                    );

                const nextFiles =
                    filesRef.current.some(
                        (file) =>
                            file.path === path,
                    )
                        ? filesRef.current.map(
                              (file) =>
                                  file.path === path
                                      ? nextFile
                                      : file,
                          )
                        : [
                              ...filesRef.current,
                              nextFile,
                          ];

                syncFiles(nextFiles);

                setSelectedFile(
                    nextFile,
                );

                setError("");
            })
            .catch((err: unknown) => {
                if (
                    cancelled ||
                    requestId !==
                        contentRequestIdRef.current
                ) {
                    return;
                }

                setError(
                    err instanceof Error
                        ? err.message
                        : `Failed to load "${path}".`,
                );
            })
            .finally(() => {
                if (
                    !cancelled &&
                    requestId ===
                        contentRequestIdRef.current
                ) {
                    setWorkspaceSyncing(false);
                }
            });

        return () => {
            cancelled = true;
        };
    }, [
        selectedProject?.id,
        selectedFile?.path,
        token,
        syncFiles,
    ]);

    const handleOpenFolder =
        useCallback(async () => {
            if (currentRole === "VIEWER") {
                setError(
                    "Viewers cannot open a workspace.",
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
                    await openLocalFolder(mode);

                const localWorkspace =
                    await loadWorkspace();

                const nextFiles =
                    buildExplorerFiles(
                        localWorkspace.files,
                    );

                const nextFolders =
                    buildExplorerFolders(
                        localWorkspace.folders,
                    );

                localWorkspaceActiveRef.current =
                    true;

                setWorkspaceName(name);
                syncFiles(nextFiles);
                syncFolders(nextFolders);
                setSelectedFile(
                    nextFiles[0] ?? null,
                );

                if (
                    currentRole === "OWNER" &&
                    selectedProject &&
                    token
                ) {
                    setWorkspaceSyncing(true);

                    const snapshot =
                        await importWorkspace(
                            selectedProject.id,
                            {
                                files: nextFiles.map(
                                    (file) => ({
                                        path:
                                            normalizeWorkspacePath(
                                                file.path,
                                            ),
                                        name: file.name,
                                        content:
                                            file.content,
                                    }),
                                ),
                                folders:
                                    nextFolders.map(
                                        (folder) =>
                                            normalizeWorkspacePath(
                                                folder,
                                            ),
                                    ),
                            },
                            token,
                        );

                    ownerImportedProjectRef.current =
                        selectedProject.id;

                    handleWorkspaceSnapshot(
                        snapshot,
                    );
                } else if (
                    currentRole !== "OWNER" &&
                    selectedProject &&
                    token
                ) {
                    setWorkspaceSyncing(true);

                    const snapshot =
                        await getWorkspace(
                            selectedProject.id,
                            token,
                        );

                    handleWorkspaceSnapshot(
                        snapshot,
                    );

                    const remoteFiles =
                        buildExplorerFiles(
                            snapshot.files ?? [],
                        );

                    if (remoteFiles.length > 0) {
                        syncFiles(
                            nextFiles.length > 0
                                ? nextFiles
                                : remoteFiles,
                        );
                    }
                }
            } catch (err: unknown) {
                if (
                    err instanceof DOMException &&
                    err.name === "AbortError"
                ) {
                    return;
                }

                setWorkspaceSyncing(false);
                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to open folder.",
                );
            }
        }, [
            currentRole,
            selectedProject,
            token,
            syncFiles,
            syncFolders,
            handleWorkspaceSnapshot,
        ]);

    const handleCreateFile =
        useCallback(async () => {
            if (currentRole === "VIEWER") {
                return;
            }

            if (
                !localWorkspaceActiveRef.current
            ) {
                setError(
                    "Open a local folder first.",
                );
                return;
            }

            const input = window.prompt(
                "Enter file path",
                "src/new-file.txt",
            );

            if (input === null) {
                return;
            }

            const path =
                normalizeWorkspacePath(
                    input.trim(),
                );

            if (!path) {
                setError("File path is required.");
                return;
            }

            if (
                filesRef.current.some(
                    (file) =>
                        file.path === path,
                ) ||
                foldersRef.current.some(
                    (folder) =>
                        folder === path,
                )
            ) {
                setError(
                    `"${path}" already exists.`,
                );
                return;
            }

            try {
                await createWorkspaceFile(
                    path,
                    "",
                );

                const nextFile =
                    makeExplorerFile(path, "");

                syncFiles([
                    ...filesRef.current,
                    nextFile,
                ]);

                syncFolders(
                    buildExplorerFolders([
                        ...foldersRef.current,
                        ...getParentFolders(path),
                    ]),
                );

                setSelectedFile(nextFile);
                setIsDirty(false);
                sendWorkspaceFile(path, "");
                setError("");
            } catch (err: unknown) {
                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to create file.",
                );
            }
        }, [
            currentRole,
            syncFiles,
            syncFolders,
            sendWorkspaceFile,
        ]);

    const handleCreateFolder =
        useCallback(async () => {
            if (currentRole === "VIEWER") {
                return;
            }

            if (
                !localWorkspaceActiveRef.current
            ) {
                setError(
                    "Open a local folder first.",
                );
                return;
            }

            const input = window.prompt(
                "Enter folder path",
                "src/components",
            );

            if (input === null) {
                return;
            }

            const path =
                normalizeWorkspacePath(
                    input.trim(),
                );

            if (!path) {
                setError(
                    "Folder path is required.",
                );
                return;
            }

            if (
                foldersRef.current.includes(path) ||
                filesRef.current.some(
                    (file) =>
                        file.path === path,
                )
            ) {
                setError(
                    `"${path}" already exists.`,
                );
                return;
            }

            try {
                await createWorkspaceFolder(path);

                syncFolders([
                    ...foldersRef.current,
                    path,
                ]);

                sendWorkspaceFolder(path);
                setError("");
            } catch (err: unknown) {
                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to create folder.",
                );
            }
        }, [
            currentRole,
            syncFolders,
            sendWorkspaceFolder,
        ]);

    const handleCodeChange =
        useCallback(
            (value: string) => {
                if (
                    currentRole === "VIEWER" ||
                    !selectedFile
                ) {
                    return;
                }

                const nextFile =
                    makeExplorerFile(
                        selectedFile.path,
                        value,
                    );

                syncFiles(
                    filesRef.current.map(
                        (file) =>
                            file.path ===
                            selectedFile.path
                                ? nextFile
                                : file,
                    ),
                );

                setSelectedFile(nextFile);
                setIsDirty(true);
                sendChange(value);
            },
            [
                currentRole,
                selectedFile,
                syncFiles,
                sendChange,
            ],
        );

    const handleDeleteFile =
        useCallback(
            async (file: ExplorerFile) => {
                if (
                    currentRole === "VIEWER"
                ) {
                    return;
                }

                if (
                    !localWorkspaceActiveRef.current
                ) {
                    setError(
                        "Open a local folder first.",
                    );
                    return;
                }

                if (
                    !window.confirm(
                        `Delete "${file.path}"?`,
                    )
                ) {
                    return;
                }

                try {
                    await deleteWorkspaceFile(
                        file.path,
                    );

                    const nextFiles =
                        filesRef.current.filter(
                            (item) =>
                                item.path !==
                                file.path,
                        );

                    syncFiles(nextFiles);

                    setSelectedFile(
                        (current) =>
                            current?.path ===
                            file.path
                                ? nextFiles[0] ??
                                  null
                                : current,
                    );

                    setIsDirty(false);
                    sendWorkspaceDelete(
                        file.path,
                    );
                    setError("");
                } catch (err: unknown) {
                    setError(
                        err instanceof Error
                            ? err.message
                            : "Failed to delete file.",
                    );
                }
            },
            [
                currentRole,
                syncFiles,
                sendWorkspaceDelete,
            ],
        );

    const handleDeleteFolder =
        useCallback(
            async (path: string) => {
                if (
                    currentRole === "VIEWER"
                ) {
                    return;
                }

                if (
                    !localWorkspaceActiveRef.current
                ) {
                    setError(
                        "Open a local folder first.",
                    );
                    return;
                }

                if (
                    !window.confirm(
                        `Delete folder "${path}" and everything inside it?`,
                    )
                ) {
                    return;
                }

                try {
                    await deleteWorkspaceFolder(
                        path,
                    );

                    const prefix = `${path}/`;

                    const nextFiles =
                        filesRef.current.filter(
                            (file) =>
                                file.path !== path &&
                                !file.path.startsWith(
                                    prefix,
                                ),
                        );

                    const nextFolders =
                        foldersRef.current.filter(
                            (folder) =>
                                folder !== path &&
                                !folder.startsWith(
                                    prefix,
                                ),
                        );

                    syncFiles(nextFiles);
                    syncFolders(nextFolders);

                    setSelectedFile((current) =>
                        current &&
                        (
                            current.path === path ||
                            current.path.startsWith(
                                prefix,
                            )
                        )
                            ? nextFiles[0] ?? null
                            : current,
                    );

                    setIsDirty(false);
                    sendWorkspaceFolderDelete(
                        path,
                    );
                    setError("");
                } catch (err: unknown) {
                    setError(
                        err instanceof Error
                            ? err.message
                            : "Failed to delete folder.",
                    );
                }
            },
            [
                currentRole,
                syncFiles,
                syncFolders,
                sendWorkspaceFolderDelete,
            ],
        );

    const handleRenameFile =
        useCallback(
            async (file: ExplorerFile) => {
                if (
                    currentRole === "VIEWER"
                ) {
                    return;
                }

                if (
                    !localWorkspaceActiveRef.current
                ) {
                    setError(
                        "Open a local folder first.",
                    );
                    return;
                }

                const nextName =
                    window.prompt(
                        `Rename file "${file.name}"`,
                        file.name,
                    )?.trim();

                if (!nextName) {
                    return;
                }

                const parent =
                    file.path
                        .split("/")
                        .slice(0, -1)
                        .join("/");

                const newPath =
                    normalizeWorkspacePath(
                        parent
                            ? `${parent}/${nextName}`
                            : nextName,
                    );

                if (
                    !newPath ||
                    newPath === file.path
                ) {
                    return;
                }

                if (
                    filesRef.current.some(
                        (item) =>
                            item.path ===
                            newPath,
                    ) ||
                    foldersRef.current.some(
                        (folder) =>
                            folder === newPath,
                    )
                ) {
                    setError(
                        `"${newPath}" already exists.`,
                    );
                    return;
                }

                try {
                    await renameWorkspaceFile(
                        file.path,
                        newPath,
                    );

                    const nextFiles =
                        filesRef.current.map(
                            (item) =>
                                item.path ===
                                file.path
                                    ? makeExplorerFile(
                                          newPath,
                                          item.content,
                                      )
                                    : item,
                        );

                    syncFiles(nextFiles);

                    setSelectedFile((current) =>
                        current?.path ===
                        file.path
                            ? makeExplorerFile(
                                  newPath,
                                  file.content,
                              )
                            : current,
                    );

                    sendWorkspaceRename(
                        file.path,
                        newPath,
                    );

                    setError("");
                } catch (err: unknown) {
                    setError(
                        err instanceof Error
                            ? err.message
                            : "Failed to rename file.",
                    );
                }
            },
            [
                currentRole,
                syncFiles,
                sendWorkspaceRename,
            ],
        );

    const handleRenameFolder =
        useCallback(
            async (oldPath: string) => {
                if (
                    currentRole === "VIEWER"
                ) {
                    return;
                }

                if (
                    !localWorkspaceActiveRef.current
                ) {
                    setError(
                        "Open a local folder first.",
                    );
                    return;
                }

                const currentName =
                    oldPath
                        .split("/")
                        .pop() ?? oldPath;

                const nextName =
                    window.prompt(
                        `Rename folder "${currentName}"`,
                        currentName,
                    )?.trim();

                if (!nextName) {
                    return;
                }

                const parent =
                    oldPath
                        .split("/")
                        .slice(0, -1)
                        .join("/");

                const newPath =
                    normalizeWorkspacePath(
                        parent
                            ? `${parent}/${nextName}`
                            : nextName,
                    );

                if (
                    !newPath ||
                    newPath === oldPath
                ) {
                    return;
                }

                const oldPrefix =
                    `${oldPath}/`;
                const newPrefix =
                    `${newPath}/`;

                if (
                    newPath.startsWith(
                        oldPrefix,
                    )
                ) {
                    setError(
                        "A folder cannot be moved inside itself.",
                    );
                    return;
                }

                if (
                    filesRef.current.some(
                        (file) =>
                            file.path ===
                                newPath ||
                            file.path.startsWith(
                                newPrefix,
                            ),
                    ) ||
                    foldersRef.current.some(
                        (folder) =>
                            folder === newPath ||
                            folder.startsWith(
                                newPrefix,
                            ),
                    )
                ) {
                    setError(
                        `"${newPath}" already exists or contains existing files.`,
                    );
                    return;
                }

                try {
                    await renameWorkspaceFolder(
                        oldPath,
                        newPath,
                    );

                    const nextFiles =
                        filesRef.current.map(
                            (file) =>
                                file.path ===
                                oldPath
                                    ? file
                                    : file.path.startsWith(
                                          oldPrefix,
                                      )
                                    ? makeExplorerFile(
                                          `${newPath}${file.path.slice(
                                              oldPath.length,
                                          )}`,
                                          file.content,
                                      )
                                    : file,
                        );

                    const nextFolders =
                        foldersRef.current.map(
                            (folder) =>
                                folder ===
                                oldPath
                                    ? newPath
                                    : folder.startsWith(
                                          oldPrefix,
                                      )
                                    ? `${newPath}${folder.slice(
                                          oldPath.length,
                                      )}`
                                    : folder,
                        );

                    syncFiles(nextFiles);
                    syncFolders(nextFolders);

                    setSelectedFile((current) => {
                        if (
                            !current ||
                            !current.path.startsWith(
                                oldPrefix,
                            )
                        ) {
                            return current;
                        }

                        return makeExplorerFile(
                            `${newPath}${current.path.slice(
                                oldPath.length,
                            )}`,
                            current.content,
                        );
                    });

                    sendWorkspaceRename(
                        oldPath,
                        newPath,
                    );

                    setError("");
                } catch (err: unknown) {
                    setError(
                        err instanceof Error
                            ? err.message
                            : "Failed to rename folder.",
                    );
                }
            },
            [
                currentRole,
                syncFiles,
                syncFolders,
                sendWorkspaceRename,
            ],
        );

    const handleSaveToDevice =
        useCallback(async () => {
            if (currentRole === "VIEWER") {
                setError(
                    "Viewers cannot save files.",
                );
                return;
            }

            if (
                filesRef.current.length === 0
            ) {
                setError(
                    "No files available to save.",
                );
                return;
            }

            if (saving) {
                return;
            }

            try {
                setSaving(true);
                setError("");

                const savedName =
                    await saveProjectToDevice(
                        filesRef.current.map(
                            (file) => ({
                                path: file.path,
                                content:
                                    file.content ?? "",
                            }),
                        ),
                    );

                localWorkspaceActiveRef.current =
                    true;

                setWorkspaceName(
                    savedName,
                );

                setIsDirty(false);
            } catch (err: unknown) {
                if (
                    err instanceof DOMException &&
                    err.name === "AbortError"
                ) {
                    return;
                }

                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to save workspace.",
                );
            } finally {
                setSaving(false);
            }
        }, [
            currentRole,
            saving,
        ]);

    useEffect(() => {
        const onKeyDown =
            (event: KeyboardEvent) => {
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
            onKeyDown,
        );

        return () =>
            window.removeEventListener(
                "keydown",
                onKeyDown,
            );
    }, [handleSaveToDevice]);

    const handleSendChat =
        useCallback(
            (content: string) => {
                const clean =
                    content.trim();

                if (clean) {
                    sendMessage(clean);
                }
            },
            [sendMessage],
        );

    const handleSelectFile =
        useCallback(
            (file: ExplorerFile) => {
                setError("");
                setSelectedFile(file);
                setIsDirty(false);
            },
            [],
        );

    const handleBack =
        useCallback(() => {
            contentRequestIdRef.current += 1;
            clearWorkspace();

            localWorkspaceActiveRef.current =
                false;
            ownerImportedProjectRef.current =
                null;

            setSelectedProject(null);
            setFiles([]);
            setFolders([]);
            setSelectedFile(null);
            setCurrentRole(null);
            setChatMessages([]);
            setWorkspaceName(null);
            setIsDirty(false);
            setError("");
        }, []);

    const handleLogout =
        useCallback(() => {
            handleBack();

            localStorage.removeItem("token");
            localStorage.removeItem(
                "username",
            );

            setAuthenticated(false);
            setAuthScreen("login");
        }, [handleBack]);

    if (!authenticated) {
        if (authScreen === "register") {
            return (
                <Register
                    onBackToLogin={() =>
                        setAuthScreen("login")
                    }
                />
            );
        }

        return (
            <Login
                onLogin={() =>
                    setAuthenticated(true)
                }
                onRegister={() =>
                    setAuthScreen("register")
                }
            />
        );
    }

    if (!selectedProject) {
        return (
            <Dashboard
                onOpenProject={
                    setSelectedProject
                }
            />
        );
    }

    return (
        <div className="app">
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
                    onClick={handleBack}
                    title="Back to projects"
                >
                    <ArrowLeft size={15} />
                    <span>Projects</span>
                </button>

                <div className="topbar-spacer" />

                <div className="topbar-workspace">
                    <div className="workspace-mode-pill">
                        <Code2 size={13} />
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
                            {getRoleLabel(
                                currentRole,
                            )}
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
                    onClick={() =>
                        void handleSaveToDevice()
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
                        <kbd>Ctrl S</kbd>
                    )}
                </button>

                <div className="topbar-divider" />

                <div className="user">
                    <div
                        className={`role-badge role-${(
                            currentRole ??
                            "viewer"
                        ).toLowerCase()}`}
                    >
                        <Circle
                            size={7}
                            fill="currentColor"
                        />
                        <span>
                            {getRoleLabel(
                                currentRole,
                            )}
                        </span>
                    </div>

                    <div className="user-avatar">
                        {username
                            .charAt(0)
                            .toUpperCase()}
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
                    <Search size={17} />
                </button>

                <button
                    type="button"
                    className="topbar-icon-button notification-button"
                    title="Notifications"
                    aria-label="Notifications"
                >
                    <Bell size={16} />
                    <span className="notification-dot" />
                </button>

                <button
                    type="button"
                    className="topbar-icon-button"
                    title="Settings"
                    aria-label="Settings"
                >
                    <Settings2 size={16} />
                </button>

                <button
                    type="button"
                    className="logout-button"
                    onClick={handleLogout}
                    title="Logout"
                >
                    <LogOut size={15} />
                </button>
            </header>

            {error && (
                <div className="error-banner">
                    <div className="error-content">
                        <X size={15} />
                        <span>{error}</span>
                    </div>

                    <button
                        type="button"
                        onClick={() =>
                            setError("")
                        }
                        title="Dismiss"
                        aria-label="Dismiss error"
                    >
                        <X size={15} />
                    </button>
                </div>
            )}

            <main className="workspace">
                <ActivityBar
                    activePanel={activePanel}
                    onPanelChange={
                        setActivePanel
                    }
                />

                <div
                    className={
                        activePanel === "explorer"
                            ? "workspace-panel explorer-panel-visible"
                            : "workspace-panel explorer-panel-hidden"
                    }
                >
                    <FileExplorer
                        files={files}
                        folders={folders}
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
                                {selectedFile?.name ??
                                    "Welcome"}
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
                                selectedFile.path,
                            ).map(
                                (
                                    part,
                                    index,
                                    parts,
                                ) => (
                                    <span
                                        key={`${part}-${index}`}
                                        className={
                                            index ===
                                            parts.length -
                                                1
                                                ? "breadcrumb-current"
                                                : "breadcrumb-part"
                                        }
                                    >
                                        {part}

                                        {index <
                                            parts.length -
                                                1 && (
                                            <span className="breadcrumb-separator">
                                                ›
                                            </span>
                                        )}
                                    </span>
                                ),
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

                                <h2>Code Break</h2>

                                <p>
                                    Select a file from
                                    the explorer to
                                    start coding.
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
                                    UTF-8
                                </span>

                                <span>
                                    Java 17
                                </span>

                                <span className="editor-status-prettier">
                                    <Check size={10} />
                                    Prettier • Ready
                                </span>
                            </div>
                        </div>
                    )}
                </section>

                <aside
                    className={
                        activePanel ===
                        "collaboration"
                            ? "collaboration-panel collaboration-panel-visible"
                            : "collaboration-panel collaboration-panel-hidden"
                    }
                >
                    <div className="collaboration-heading">
                        <div className="collaboration-heading-title">
                            <div className="collaboration-heading-icon">
                                <Users size={15} />
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
                                        Real-time
                                        conversation
                                    </span>
                                </div>
                            </div>

                            <div className="chat-online">
                                <span className="live-dot" />
                                Online
                            </div>
                        </div>

                        <div className="chat-messages">
                            {chatMessages.length ===
                            0 ? (
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
                                        Start a
                                        conversation
                                        with your
                                        team.
                                    </span>
                                </div>
                            ) : (
                                chatMessages.map(
                                    (
                                        message,
                                        index,
                                    ) => (
                                        <div
                                            key={`${message.username}-${index}`}
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

                                                {message.username ===
                                                    username && (
                                                    <span className="chat-you">
                                                        You
                                                    </span>
                                                )}
                                            </div>

                                            <div className="chat-content">
                                                {
                                                    message.content
                                                }
                                            </div>
                                        </div>
                                    ),
                                )
                            )}
                        </div>

                        <form
                            className="chat-input-container"
                            onSubmit={(event) => {
                                event.preventDefault();

                                const form =
                                    event.currentTarget;

                                const input =
                                    form.elements.namedItem(
                                        "message",
                                    ) as HTMLInputElement;

                                handleSendChat(
                                    input.value,
                                );

                                input.value = "";
                            }}
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
                                <Send size={14} />
                            </button>
                        </form>
                    </section>
                </aside>
            </main>

            <footer className="statusbar">
                <div className="status-left">
                    <span className="status-connection">
                        <span className="status-dot" />
                        {isConnected
                            ? "Connected • Live"
                            : "Connecting..."}
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
                        {getRoleLabel(
                            currentRole,
                        )}
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
