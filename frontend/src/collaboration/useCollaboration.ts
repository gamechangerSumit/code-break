import {
    useCallback,
    useEffect,
    useRef,
    useState,
} from "react";

import {
    Client,
    type IMessage,
    type StompSubscription,
} from "@stomp/stompjs";


// =====================================================
// TYPES
// =====================================================

export interface CollaborationMessage {
    type?: string;
    projectId: number;
    filePath: string;
    username?: string;
    content?: string;
}

export interface WorkspaceFileMetadata {
    path: string;
    name: string;
    size: number;
    version: number;
}

export interface WorkspaceFolderMetadata {
    path: string;
    name: string;
    parentPath: string | null;
}

export interface WorkspaceSnapshot {
    projectId: number;
    files: WorkspaceFileMetadata[];
    folders: WorkspaceFolderMetadata[];
}

export interface WorkspaceMessage {
    type?: string;
    projectId: number;
    filePath?: string;
    username?: string;
    content?: string;
}

export interface RoleChangedMessage {
    type?: string;
    projectId: number;
    memberId: number;
    username: string;
    role: string;
}


// =====================================================
// PROPS
// =====================================================

interface UseCollaborationProps {

    projectId:
        number | null;

    filePath:
        string | null;

    onRemoteChange?:
        (
            message:
                CollaborationMessage
        ) => void;

    onWorkspaceFile?:
        (
            message:
                WorkspaceMessage
        ) => void;

    onWorkspaceSnapshot?:
        (
            snapshot:
                WorkspaceSnapshot
        ) => void;

    onRoleChange?:
        (
            message:
                RoleChangedMessage
        ) => void;
}


// =====================================================
// HOOK
// =====================================================

export function useCollaboration(
    {
        projectId,
        filePath,
        onRemoteChange,
        onWorkspaceFile,
        onWorkspaceSnapshot,
        onRoleChange,
    }:
        UseCollaborationProps
) {

    // =================================================
    // CONNECTION
    // =================================================

    const [
        isConnected,
        setIsConnected,
    ] = useState(false);


    const [
        connectionVersion,
        setConnectionVersion,
    ] = useState(0);


    const clientRef =
        useRef<Client | null>(
            null
        );


    const subscriptionsRef =
        useRef<StompSubscription[]>(
            []
        );


    // =================================================
    // LATEST CALLBACK REFS
    // =================================================

    const onRemoteChangeRef =
        useRef(onRemoteChange);

    const onWorkspaceFileRef =
        useRef(onWorkspaceFile);

    const onWorkspaceSnapshotRef =
        useRef(onWorkspaceSnapshot);

    const onRoleChangeRef =
        useRef(onRoleChange);


    useEffect(
        () => {

            onRemoteChangeRef.current =
                onRemoteChange;

        },
        [
            onRemoteChange,
        ]
    );


    useEffect(
        () => {

            onWorkspaceFileRef.current =
                onWorkspaceFile;

        },
        [
            onWorkspaceFile,
        ]
    );


    useEffect(
        () => {

            onWorkspaceSnapshotRef.current =
                onWorkspaceSnapshot;

        },
        [
            onWorkspaceSnapshot,
        ]
    );


    useEffect(
        () => {

            onRoleChangeRef.current =
                onRoleChange;

        },
        [
            onRoleChange,
        ]
    );


    // =================================================
    // PROJECT REF
    // =================================================

    const projectIdRef =
        useRef<number | null>(
            projectId
        );


    useEffect(
        () => {

            projectIdRef.current =
                projectId;

        },
        [
            projectId,
        ]
    );


    // =================================================
    // REQUEST WORKSPACE SNAPSHOT REF
    // =================================================

    const requestWorkspaceSnapshot =
        useCallback(
            () => {

                const client =
                    clientRef.current;


                const currentProjectId =
                    projectIdRef.current;


                if (
                    !client ||
                    !client.connected ||
                    !currentProjectId
                ) {

                    console.log(
                        "[WS] Snapshot request skipped:",
                        {
                            connected:
                                client?.connected ??
                                false,

                            projectId:
                                currentProjectId,
                        }
                    );

                    return;
                }


                console.log(
                    "[WS] Requesting workspace snapshot:",
                    {
                        projectId:
                            currentProjectId,
                    }
                );


                client.publish({
                    destination:
                        "/app/workspace/request",

                    body:
                        JSON.stringify({
                            projectId:
                                currentProjectId,
                        }),
                });

            },
            []
        );


    // =================================================
    // CONNECT
    // =================================================

    useEffect(
        () => {

            if (!projectId) {

                setIsConnected(
                    false
                );

                return;
            }


            const token =
                localStorage.getItem(
                    "token"
                );


            if (!token) {

                console.warn(
                    "[WS] No JWT token available."
                );

                setIsConnected(
                    false
                );

                return;
            }


            console.log(
                "[WS] Creating STOMP client:",
                {
                    projectId,
                }
            );


            const client =
                new Client({

                    brokerURL:
                        "ws://localhost:8088/ws",

                    connectHeaders: {
                        Authorization:
                            `Bearer ${token}`,
                    },


                    reconnectDelay:
                        3000,


                    heartbeatIncoming:
                        10000,


                    heartbeatOutgoing:
                        10000,


                    debug:
                        message => {
                            console.log(
                                "[STOMP]",
                                message
                            );
                        },
                });


            clientRef.current =
                client;


            // =========================================
            // CONNECTED
            // =========================================

            client.onConnect =
                () => {

                    console.log(
                        "[WS] Connected:",
                        {
                            projectId,
                        }
                    );


                    setIsConnected(
                        true
                    );


                    setConnectionVersion(
                        version =>
                            version + 1
                    );


                    // =================================
                    // CLEAN OLD SUBSCRIPTIONS
                    // =================================

                    subscriptionsRef.current
                        .forEach(
                            subscription =>
                                subscription.unsubscribe()
                        );


                    subscriptionsRef.current =
                        [];


                    // =================================
                    // CODE CHANGES
                    // =================================

                    const codeSubscription =
                        client.subscribe(

                            `/topic/project/${projectId}/code`,

                            (
                                message:
                                    IMessage
                            ) => {

                                try {

                                    const payload =
                                        JSON.parse(
                                            message.body
                                        ) as CollaborationMessage;


                                    if (
                                        payload.projectId !==
                                        projectId
                                    ) {
                                        return;
                                    }


                                    /*
                                     * Ignore our own edit.
                                     * The local editor already contains it.
                                     */

                                    const currentUsername =
                                        localStorage.getItem(
                                            "username"
                                        );


                                    if (
                                        payload.username &&
                                        payload.username ===
                                            currentUsername
                                    ) {
                                        return;
                                    }


                                    onRemoteChangeRef.current?.(
                                        payload
                                    );

                                } catch (
                                    error
                                ) {

                                    console.error(
                                        "[WS] Invalid code message:",
                                        error
                                    );
                                }
                            }
                        );


                    subscriptionsRef.current.push(
                        codeSubscription
                    );


                    // =================================
                    // WORKSPACE EVENTS
                    // =================================

                    const workspaceSubscription =
                        client.subscribe(

                            `/topic/project/${projectId}/workspace`,

                            (
                                message:
                                    IMessage
                            ) => {

                                try {

                                    const payload =
                                        JSON.parse(
                                            message.body
                                        ) as WorkspaceMessage;


                                    if (
                                        payload.projectId !==
                                        projectId
                                    ) {
                                        return;
                                    }


                                    // =============================
                                    // COMPLETE SNAPSHOT
                                    // =============================

                                    if (
                                        payload.type ===
                                        "WORKSPACE_SYNC"
                                    ) {

                                        /*
                                         * Backward compatibility
                                         * with the old backend.
                                         */

                                        const legacy =
                                            payload as
                                                WorkspaceMessage & {
                                                    files?: {
                                                        path: string;
                                                        content: string;
                                                    }[];

                                                    folders?: string[];
                                                };


                                        const snapshot:
                                            WorkspaceSnapshot =
                                        {
                                            projectId,

                                            files:
                                                (
                                                    legacy.files ??
                                                    []
                                                ).map(
                                                    file => ({
                                                        path:
                                                            file.path,

                                                        name:
                                                            file.path
                                                                .split("/")
                                                                .pop() ??
                                                            file.path,

                                                        size:
                                                            (
                                                                file.content ??
                                                                ""
                                                            ).length,

                                                        version:
                                                            1,
                                                    })
                                                ),

                                            folders:
                                                (
                                                    legacy.folders ??
                                                    []
                                                ).map(
                                                    path => {

                                                        const normalized =
                                                            path
                                                                .replace(
                                                                    /\\/g,
                                                                    "/"
                                                                )
                                                                .replace(
                                                                    /^\/+/,
                                                                    ""
                                                                )
                                                                .replace(
                                                                    /\/+$/,
                                                                    ""
                                                                );


                                                        const parts =
                                                            normalized
                                                                .split("/")
                                                                .filter(
                                                                    Boolean
                                                                );


                                                        return {
                                                            path:
                                                                normalized,

                                                            name:
                                                                parts[
                                                                    parts.length -
                                                                    1
                                                                ] ??
                                                                normalized,

                                                            parentPath:
                                                                parts.length >
                                                                1
                                                                    ? parts
                                                                        .slice(
                                                                            0,
                                                                            -1
                                                                        )
                                                                        .join(
                                                                            "/"
                                                                        )
                                                                    : null,
                                                        };
                                                    }
                                                ),
                                        };


                                        onWorkspaceSnapshotRef.current?.(
                                            snapshot
                                        );

                                        return;
                                    }


                                    // =============================
                                    // NEW REFRESH EVENT
                                    // =============================

                                    if (
                                        payload.type ===
                                        "WORKSPACE_REFRESH"
                                    ) {

                                        const currentUsername =
                                            localStorage.getItem(
                                                "username"
                                            );


                                        /*
                                         * The owner already has the
                                         * local folder loaded.
                                         *
                                         * No need to request another
                                         * snapshot for the owner.
                                         */

                                        if (
                                            payload.username &&
                                            payload.username ===
                                                currentUsername
                                        ) {

                                            console.log(
                                                "[WS] Ignoring own workspace refresh."
                                            );

                                            return;
                                        }


                                        console.log(
                                            "[WS] Workspace refresh received:",
                                            {
                                                projectId,
                                                from:
                                                    payload.username,
                                            }
                                        );


                                        /*
                                         * Only metadata is requested.
                                         *
                                         * File contents stay in MinIO
                                         * and are fetched separately
                                         * when the collaborator selects
                                         * a file.
                                         */

                                        requestWorkspaceSnapshot();

                                        return;
                                    }


                                    // =============================
                                    // NORMAL WORKSPACE EVENT
                                    // =============================

                                    onWorkspaceFileRef.current?.(
                                        payload
                                    );

                                } catch (
                                    error
                                ) {

                                    console.error(
                                        "[WS] Invalid workspace message:",
                                        error
                                    );
                                }
                            }
                        );


                    subscriptionsRef.current.push(
                        workspaceSubscription
                    );


                    // =================================
                    // PRIVATE WORKSPACE SNAPSHOT
                    // =================================

                    const snapshotSubscription =
                        client.subscribe(

                            "/user/queue/workspace",

                            (
                                message:
                                    IMessage
                            ) => {

                                try {

                                    const snapshot =
                                        JSON.parse(
                                            message.body
                                        ) as WorkspaceSnapshot;


                                    if (
                                        snapshot.projectId !==
                                        projectId
                                    ) {
                                        return;
                                    }


                                    console.log(
                                        "[WS] Workspace snapshot received:",
                                        {
                                            projectId:
                                                snapshot.projectId,

                                            files:
                                                snapshot.files?.length ??
                                                0,

                                            folders:
                                                snapshot.folders?.length ??
                                                0,
                                        }
                                    );


                                    onWorkspaceSnapshotRef.current?.(
                                        snapshot
                                    );

                                } catch (
                                    error
                                ) {

                                    console.error(
                                        "[WS] Invalid snapshot:",
                                        error
                                    );
                                }
                            }
                        );


                    subscriptionsRef.current.push(
                        snapshotSubscription
                    );


                    // =================================
                    // MEMBER ROLE CHANGES
                    // =================================

                    const roleSubscription =
                        client.subscribe(

                            `/topic/project/${projectId}/members`,

                            (
                                message:
                                    IMessage
                            ) => {

                                try {

                                    const payload =
                                        JSON.parse(
                                            message.body
                                        ) as RoleChangedMessage;


                                    if (
                                        payload.projectId !==
                                        projectId
                                    ) {
                                        return;
                                    }


                                    onRoleChangeRef.current?.(
                                        payload
                                    );

                                } catch (
                                    error
                                ) {

                                    console.error(
                                        "[WS] Invalid role message:",
                                        error
                                    );
                                }
                            }
                        );


                    subscriptionsRef.current.push(
                        roleSubscription
                    );
                };


            // =========================================
            // DISCONNECTED
            // =========================================

            client.onDisconnect =
                () => {

                    console.log(
                        "[WS] Disconnected:",
                        {
                            projectId,
                        }
                    );


                    setIsConnected(
                        false
                    );
                };


            // =========================================
            // ERROR
            // =========================================

            client.onStompError =
                frame => {

                    console.error(
                        "[WS] STOMP broker error:",
                        frame.headers[
                            "message"
                        ],

                        frame.body
                    );
                };


            client.onWebSocketError =
                error => {

                    console.error(
                        "[WS] WebSocket error:",
                        error
                    );
                };


            // =========================================
            // ACTIVATE
            // =========================================

            client.activate();


            // =========================================
            // CLEANUP
            // =========================================

            return () => {

                console.log(
                    "[WS] Cleaning connection:",
                    {
                        projectId,
                    }
                );


                subscriptionsRef.current
                    .forEach(
                        subscription =>
                            subscription.unsubscribe()
                    );


                subscriptionsRef.current =
                    [];


                client.deactivate();


                if (
                    clientRef.current ===
                    client
                ) {

                    clientRef.current =
                        null;
                }


                setIsConnected(
                    false
                );
            };

        },
        [
            projectId,
        ]
    );


    // =================================================
    // SEND CODE CHANGE
    // =================================================

    const sendChange =
        useCallback(
            (
                content: string
            ) => {

                const client =
                    clientRef.current;


                const currentProjectId =
                    projectIdRef.current;


                if (
                    !client ||
                    !client.connected ||
                    !currentProjectId ||
                    !filePath
                ) {
                    return;
                }


                client.publish({

                    destination:
                        "/app/edit",

                    body:
                        JSON.stringify({

                            type:
                                "CODE_CHANGE",

                            projectId:
                                currentProjectId,

                            filePath,

                            content,
                        }),
                });

            },
            [
                filePath,
            ]
        );


    // =================================================
    // SEND WORKSPACE FILE
    // =================================================

    const sendWorkspaceFile =
        useCallback(
            (
                path: string,
                content: string
            ) => {

                const client =
                    clientRef.current;


                const currentProjectId =
                    projectIdRef.current;


                if (
                    !client ||
                    !client.connected ||
                    !currentProjectId
                ) {
                    return;
                }


                client.publish({

                    destination:
                        "/app/workspace",

                    body:
                        JSON.stringify({

                            type:
                                "WORKSPACE_FILE",

                            projectId:
                                currentProjectId,

                            filePath:
                                path,

                            content,
                        }),
                });

            },
            []
        );


    // =================================================
    // SEND WORKSPACE FOLDER
    // =================================================

    const sendWorkspaceFolder =
        useCallback(
            (
                path: string
            ) => {

                const client =
                    clientRef.current;


                const currentProjectId =
                    projectIdRef.current;


                if (
                    !client ||
                    !client.connected ||
                    !currentProjectId
                ) {
                    return;
                }


                client.publish({

                    destination:
                        "/app/workspace",

                    body:
                        JSON.stringify({

                            type:
                                "WORKSPACE_FOLDER",

                            projectId:
                                currentProjectId,

                            filePath:
                                path,
                        }),
                });

            },
            []
        );


    // =================================================
    // DELETE WORKSPACE FILE
    // =================================================

    const sendWorkspaceDelete =
        useCallback(
            (
                path: string
            ) => {

                const client =
                    clientRef.current;


                const currentProjectId =
                    projectIdRef.current;


                if (
                    !client ||
                    !client.connected ||
                    !currentProjectId
                ) {
                    return;
                }


                client.publish({

                    destination:
                        "/app/workspace",

                    body:
                        JSON.stringify({

                            type:
                                "WORKSPACE_DELETE",

                            projectId:
                                currentProjectId,

                            filePath:
                                path,
                        }),
                });

            },
            []
        );


    // =================================================
    // DELETE WORKSPACE FOLDER
    // =================================================

    const sendWorkspaceFolderDelete =
        useCallback(
            (
                path: string
            ) => {

                const client =
                    clientRef.current;


                const currentProjectId =
                    projectIdRef.current;


                if (
                    !client ||
                    !client.connected ||
                    !currentProjectId
                ) {
                    return;
                }


                client.publish({

                    destination:
                        "/app/workspace",

                    body:
                        JSON.stringify({

                            type:
                                "WORKSPACE_FOLDER_DELETE",

                            projectId:
                                currentProjectId,

                            filePath:
                                path,
                        }),
                });

            },
            []
        );


    // =================================================
    // RENAME WORKSPACE ITEM
    //
    // filePath = old path
    // content  = new path
    // This preserves compatibility with the current
    // backend workspace message contract.
    // =================================================

    const sendWorkspaceRename =
        useCallback(
            (
                oldPath: string,
                newPath: string
            ) => {

                const client =
                    clientRef.current;

                const currentProjectId =
                    projectIdRef.current;

                if (
                    !client ||
                    !client.connected ||
                    !currentProjectId
                ) {
                    return;
                }

                const cleanOldPath =
                    oldPath
                        .replaceAll("\\", "/")
                        .replace(/^\/+/, "")
                        .replace(/\/+$/, "");

                const cleanNewPath =
                    newPath
                        .replaceAll("\\", "/")
                        .replace(/^\/+/, "")
                        .replace(/\/+$/, "");

                if (
                    !cleanOldPath ||
                    !cleanNewPath
                ) {
                    return;
                }

                client.publish({
                    destination:
                        "/app/workspace",

                    body:
                        JSON.stringify({
                            type:
                                "WORKSPACE_RENAME",

                            projectId:
                                currentProjectId,

                            filePath:
                                cleanOldPath,

                            username:
                                localStorage.getItem(
                                    "username"
                                ) ?? "anonymous",

                            content:
                                cleanNewPath,
                        }),
                });
            },
            []
        );


    // =================================================
    // RETURN
    // =================================================

    return {

        sendChange,

        sendWorkspaceFile,

        sendWorkspaceFolder,

        sendWorkspaceDelete,

        sendWorkspaceFolderDelete,

        sendWorkspaceRename,

        requestWorkspaceSnapshot,

        isConnected,

        connectionVersion,
    };
}