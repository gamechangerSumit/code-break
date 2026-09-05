import {
    useCallback,
    useEffect,
    useRef,
    useState,
} from "react";

import {
    Client,
    type IMessage,
} from "@stomp/stompjs";

import {
    getProjectMembers,
    changeMemberRole,
} from "./projectApi";

import type {
    ProjectMember,
    ProjectRole,
} from "./projectApi";

const WS_URL =
    "ws://localhost:8088/ws";

interface ProjectRoleChangedMessage {
    projectId: number;
    memberId: number;
    userId: number;
    username: string;
    role: ProjectRole;
}

interface MembersPanelProps {
    projectId: number;

    currentUsername: string;

    onCurrentRoleChange?: (
        role: ProjectRole
    ) => void;
}

function getInitial(
    username: string
): string {
    return (
        username
            .trim()
            .charAt(0)
            .toUpperCase() ||
        "?"
    );
}

function getRoleLabel(
    role: ProjectRole
): string {
    return (
        role.charAt(0) +
        role.slice(1).toLowerCase()
    );
}

export default function MembersPanel({
    projectId,
    currentUsername,
    onCurrentRoleChange,
}: MembersPanelProps) {

    const [
        members,
        setMembers,
    ] = useState<ProjectMember[]>([]);

    const [
        loading,
        setLoading,
    ] = useState(true);

    const [
        error,
        setError,
    ] = useState("");

    const [
        updatingMemberId,
        setUpdatingMemberId,
    ] = useState<number | null>(
        null
    );

    const roleCallbackRef =
        useRef(
            onCurrentRoleChange
        );

    useEffect(() => {

        roleCallbackRef.current =
            onCurrentRoleChange;

    }, [
        onCurrentRoleChange,
    ]);

    const token =
        localStorage.getItem(
            "token"
        );

    const loadMembers =
        useCallback(
            async () => {

                if (!token) {

                    setError(
                        "Authentication required."
                    );

                    setLoading(false);

                    return;
                }

                try {

                    setLoading(true);

                    setError("");

                    const data =
                        await getProjectMembers(
                            projectId,
                            token
                        );

                    setMembers(
                        data
                    );

                    const currentUser =
                        data.find(
                            member =>
                                member.username ===
                                currentUsername
                        );

                    if (currentUser) {

                        roleCallbackRef.current?.(
                            currentUser.role
                        );
                    }

                } catch (err) {

                    setError(
                        err instanceof Error
                            ? err.message
                            : "Failed to load members."
                    );

                } finally {

                    setLoading(false);

                }

            },
            [
                projectId,
                currentUsername,
                token,
            ]
        );

    useEffect(() => {

        void loadMembers();

    }, [
        loadMembers,
    ]);

    useEffect(() => {

        const wsToken =
            localStorage.getItem(
                "token"
            );

        if (!wsToken) {
            return;
        }

        const client =
            new Client({
                brokerURL:
                    WS_URL,

                connectHeaders: {
                    Authorization:
                        `Bearer ${wsToken}`,
                },

                reconnectDelay:
                    5000,

                debug:
                    () => {},

                onConnect: () => {

                    client.subscribe(
                        `/topic/project/${projectId}/members`,
                        (
                            message: IMessage
                        ) => {

                            try {

                                const data =
                                    JSON.parse(
                                        message.body
                                    ) as ProjectRoleChangedMessage;

                                if (
                                    data.projectId !==
                                    projectId
                                ) {
                                    return;
                                }

                                setMembers(
                                    current =>
                                        current.map(
                                            member =>
                                                member.id ===
                                                data.memberId
                                                    ? {
                                                        ...member,
                                                        role:
                                                            data.role,
                                                    }
                                                    : member
                                        )
                                );

                                if (
                                    data.username ===
                                    currentUsername
                                ) {

                                    roleCallbackRef.current?.(
                                        data.role
                                    );
                                }

                            } catch (err) {

                                console.error(
                                    "[ROLE WS] Invalid message:",
                                    err
                                );
                            }
                        }
                    );
                },

                onWebSocketError:
                    err => {

                        console.error(
                            "[ROLE WS] WebSocket error:",
                            err
                        );
                    },

                onStompError:
                    frame => {

                        console.error(
                            "[ROLE WS] STOMP error:",
                            frame
                        );
                    },
            });

        client.activate();

        return () => {

            void client.deactivate();

        };

    }, [
        projectId,
        currentUsername,
    ]);

    const currentMember =
        members.find(
            member =>
                member.username ===
                currentUsername
        );

    const isOwner =
        currentMember?.role ===
        "OWNER";

    const handleRoleChange =
        async (
            member: ProjectMember,
            newRole: ProjectRole
        ) => {

            if (!token) {
                return;
            }

            if (
                member.role ===
                newRole
            ) {
                return;
            }

            try {

                setUpdatingMemberId(
                    member.id
                );

                setError("");

                const updatedMember =
                    await changeMemberRole(
                        projectId,
                        member.id,
                        newRole,
                        token
                    );

                setMembers(
                    current =>
                        current.map(
                            currentMember =>
                                currentMember.id ===
                                updatedMember.id
                                    ? updatedMember
                                    : currentMember
                        )
                );

            } catch (err) {

                setError(
                    err instanceof Error
                        ? err.message
                        : "Failed to change member role."
                );

            } finally {

                setUpdatingMemberId(
                    null
                );
            }
        };

    return (
        <section className="members-panel">

            <div className="members-panel-header">

                <div className="members-title-block">

                    <div className="members-title-row">

                        <h2>
                            Members
                        </h2>

                        <span className="members-count">
                            {members.length}
                        </span>

                    </div>

                    <span className="members-subtitle">
                        People in this workspace
                    </span>

                </div>

            </div>

            {error && (
                <div className="members-error">
                    {error}
                </div>
            )}

            {loading ? (

                <div className="members-loading">

                    <span className="members-loading-dot" />

                    Loading members...

                </div>

            ) : (

                <div className="members-list">

                    {members.map(
                        member => {

                            const isCurrentUser =
                                member.username ===
                                currentUsername;

                            const memberIsOwner =
                                member.role ===
                                "OWNER";

                            return (
                                <div
                                    key={
                                        member.id
                                    }
                                    className={
                                        isCurrentUser
                                            ? "member-row member-row-current"
                                            : "member-row"
                                    }
                                >

                                    <div className="member-info">

                                        <div
                                            className={
                                                memberIsOwner
                                                    ? "member-avatar member-avatar-owner"
                                                    : "member-avatar"
                                            }
                                        >
                                            {
                                                getInitial(
                                                    member.username
                                                )
                                            }
                                        </div>

                                        <div className="member-details">

                                            <div className="member-username">

                                                <span>
                                                    {
                                                        member.username
                                                    }
                                                </span>

                                                {isCurrentUser && (
                                                    <span className="you-label">
                                                        You
                                                    </span>
                                                )}

                                            </div>

                                            <div className="member-role-text">

                                                {memberIsOwner
                                                    ? "Workspace owner"
                                                    : getRoleLabel(
                                                        member.role
                                                    )}

                                            </div>

                                        </div>

                                    </div>

                                    <div className="member-role-control">

                                        {memberIsOwner ? (

                                            <span className="owner-badge">
                                                Owner
                                            </span>

                                        ) : isOwner ? (

                                            <select
                                                value={
                                                    member.role
                                                }
                                                disabled={
                                                    updatingMemberId ===
                                                    member.id
                                                }
                                                onChange={
                                                    event =>
                                                        handleRoleChange(
                                                            member,
                                                            event
                                                                .target
                                                                .value as ProjectRole
                                                        )
                                                }
                                                aria-label={
                                                    `Change role for ${member.username}`
                                                }
                                            >

                                                <option value="VIEWER">
                                                    Viewer
                                                </option>

                                                <option value="EDITOR">
                                                    Editor
                                                </option>

                                            </select>

                                        ) : (

                                            <span className="role-badge">
                                                {
                                                    getRoleLabel(
                                                        member.role
                                                    )
                                                }
                                            </span>

                                        )}

                                    </div>

                                </div>
                            );
                        }
                    )}

                </div>
            )}

        </section>
    );
}