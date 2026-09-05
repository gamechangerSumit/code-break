import {
    useCallback,
    useEffect,
    useRef,
} from "react";

import {
    Client,
    type IMessage,
} from "@stomp/stompjs";

const WS_URL = "ws://localhost:8088/ws";

export interface ChatMessage {
    projectId: number;
    username: string;
    content: string;
}

interface UseChatProps {
    projectId: number | null;

    onMessage: (
        message: ChatMessage
    ) => void;
}

interface PendingChatMessage {
    destination: string;
    body: string;
}

export function useChat({
    projectId,
    onMessage,
}: UseChatProps) {
    const clientRef =
        useRef<Client | null>(null);

    const projectIdRef =
        useRef<number | null>(projectId);

    const onMessageRef =
        useRef(onMessage);

    const pendingMessagesRef =
        useRef<PendingChatMessage[]>([]);

    const usernameRef =
        useRef(
            localStorage.getItem("username") ??
            "anonymous"
        );

    useEffect(() => {
        projectIdRef.current = projectId;
    }, [projectId]);

    useEffect(() => {
        onMessageRef.current = onMessage;
    }, [onMessage]);

    useEffect(() => {
        if (projectId === null) {
            clientRef.current = null;
            pendingMessagesRef.current = [];
            return;
        }

        const token =
            localStorage.getItem("token");

        if (!token) {
            console.error(
                "[CHAT] Authentication token missing."
            );
            return;
        }

        const currentProjectId = projectId;

        const client = new Client({
            brokerURL: WS_URL,

            connectHeaders: {
                Authorization: `Bearer ${token}`,
            },

            reconnectDelay: 3000,

            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,

            debug: () => {},

            onConnect: () => {
                if (
                    clientRef.current !== client
                ) {
                    return;
                }

                client.subscribe(
                    `/topic/project/${currentProjectId}/chat`,
                    (message: IMessage) => {
                        try {
                            const data =
                                JSON.parse(
                                    message.body
                                ) as ChatMessage;

                            if (
                                data.projectId !==
                                currentProjectId
                            ) {
                                return;
                            }

                            onMessageRef.current(
                                data
                            );
                        } catch (error) {
                            console.error(
                                "[CHAT] Invalid message:",
                                error
                            );
                        }
                    }
                );

                const pending =
                    pendingMessagesRef.current;

                pendingMessagesRef.current = [];

                for (
                    const pendingMessage of pending
                ) {
                    if (client.connected) {
                        client.publish({
                            destination:
                                pendingMessage.destination,
                            body:
                                pendingMessage.body,
                        });
                    }
                }
            },

            onWebSocketError: error => {
                console.error(
                    "[CHAT] WebSocket error:",
                    error
                );
            },

            onStompError: frame => {
                console.error(
                    "[CHAT] STOMP error:",
                    frame
                );
            },
        });

        clientRef.current = client;

        client.activate();

        return () => {
            if (
                clientRef.current === client
            ) {
                clientRef.current = null;
            }

            pendingMessagesRef.current = [];

            void client.deactivate();
        };
    }, [projectId]);

    const sendMessage =
        useCallback(
            (content: string) => {
                const currentProjectId =
                    projectIdRef.current;

                if (
                    currentProjectId === null
                ) {
                    return;
                }

                const clean =
                    content.trim();

                if (!clean) {
                    return;
                }

                const message: ChatMessage = {
                    projectId:
                        currentProjectId,

                    username:
                        usernameRef.current,

                    content:
                        clean,
                };

                const body =
                    JSON.stringify(message);

                const client =
                    clientRef.current;

                if (
                    client &&
                    client.connected
                ) {
                    client.publish({
                        destination: "/app/chat",
                        body,
                    });

                    return;
                }

                pendingMessagesRef.current.push({
                    destination: "/app/chat",
                    body,
                });
            },
            []
        );

    return {
        sendMessage,
    };
}