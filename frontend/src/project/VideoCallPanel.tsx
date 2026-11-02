import { useCallback, useEffect, useRef, useState } from "react";
import { Client, type IMessage } from "@stomp/stompjs";
import { Mic, MicOff, Phone, PhoneOff, ScreenShare, Video, VideoOff } from "lucide-react";
import { WS_URL } from "../config";
import "./VideoCallPanel.css";

interface Props { projectId: number; username: string; }
interface Signal {
    projectId: number; type: string; fromUsername: string; toUsername?: string | null;
    sdp?: string; candidate?: string; sdpMid?: string | null; sdpMLineIndex?: number | null;
}
interface RemoteVideo { username: string; stream: MediaStream; }

export default function VideoCallPanel({ projectId, username }: Props) {
    const [connected, setConnected] = useState(false);
    const [inCall, setInCall] = useState(false);
    const [muted, setMuted] = useState(false);
    const [cameraOff, setCameraOff] = useState(false);
    const [sharing, setSharing] = useState(false);
    const [remoteVideos, setRemoteVideos] = useState<RemoteVideo[]>([]);
    const [error, setError] = useState("");
    const clientRef = useRef<Client | null>(null);
    const localStreamRef = useRef<MediaStream | null>(null);
    const screenStreamRef = useRef<MediaStream | null>(null);
    const peersRef = useRef<Map<string, RTCPeerConnection>>(new Map());
    const readyPeersRef = useRef<Set<string>>(new Set());
    const localVideoRef = useRef<HTMLVideoElement | null>(null);

    const send = useCallback((signal: Omit<Signal, "projectId" | "fromUsername">) => {
        const client = clientRef.current;
        if (!client?.connected) return;
        client.publish({ destination: "/app/call.signal", body: JSON.stringify({ ...signal, projectId }) });
    }, [projectId]);

    const closePeer = useCallback((peerName: string) => {
        peersRef.current.get(peerName)?.close();
        peersRef.current.delete(peerName);
        readyPeersRef.current.delete(peerName);
        setRemoteVideos(current => current.filter(item => item.username !== peerName));
    }, []);

    const createPeer = useCallback((peerName: string) => {
        const existing = peersRef.current.get(peerName);
        if (existing) return existing;
        const peer = new RTCPeerConnection({
            iceServers: [{ urls: "stun:stun.l.google.com:19302" }],
        });
        localStreamRef.current?.getTracks().forEach(track => peer.addTrack(track, localStreamRef.current!));
        peer.onicecandidate = event => {
            if (event.candidate) send({
                type: "ICE_CANDIDATE", toUsername: peerName,
                candidate: event.candidate.candidate, sdpMid: event.candidate.sdpMid,
                sdpMLineIndex: event.candidate.sdpMLineIndex,
            });
        };
        peer.ontrack = event => {
            const stream = event.streams[0];
            if (stream) setRemoteVideos(current => [
                ...current.filter(item => item.username !== peerName),
                { username: peerName, stream },
            ]);
        };
        peer.onconnectionstatechange = () => {
            if (["failed", "closed", "disconnected"].includes(peer.connectionState)) closePeer(peerName);
        };
        peersRef.current.set(peerName, peer);
        return peer;
    }, [closePeer, send]);

    const makeOffer = useCallback(async (peerName: string) => {
        const peer = createPeer(peerName);
        const offer = await peer.createOffer();
        await peer.setLocalDescription(offer);
        send({ type: "CALL_OFFER", toUsername: peerName, sdp: JSON.stringify(offer) });
    }, [createPeer, send]);

    useEffect(() => {
        const token = localStorage.getItem("token");
        if (!token) return;
        const client = new Client({
            brokerURL: WS_URL,
            connectHeaders: { Authorization: `Bearer ${token}` },
            reconnectDelay: 3000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            debug: () => {},
        });
        clientRef.current = client;
        client.onConnect = () => {
            setConnected(true);
            client.subscribe(`/topic/project/${projectId}/calls`, (message: IMessage) => {
                void handleSignal(JSON.parse(message.body) as Signal);
            });
            client.subscribe("/user/queue/calls", (message: IMessage) => {
                void handleSignal(JSON.parse(message.body) as Signal);
            });
        };
        client.onDisconnect = () => setConnected(false);
        client.onWebSocketClose = () => setConnected(false);
        client.activate();

        async function handleSignal(signal: Signal) {
            if (signal.projectId !== projectId || signal.fromUsername === username ||
                (signal.toUsername && signal.toUsername !== username)) return;
            const peerName = signal.fromUsername;
            if (signal.type === "CALL_READY") {
                readyPeersRef.current.add(peerName);
                if (localStreamRef.current && username.localeCompare(peerName) < 0 &&
                    !peersRef.current.has(peerName)) {
                    try { await makeOffer(peerName); }
                    catch { setError(`Could not start a call with ${peerName}`); }
                }
            } else if (signal.type === "CALL_OFFER" && signal.sdp) {
                try {
                    const peer = createPeer(peerName);
                    await peer.setRemoteDescription(JSON.parse(signal.sdp) as RTCSessionDescriptionInit);
                    const answer = await peer.createAnswer();
                    await peer.setLocalDescription(answer);
                    send({ type: "CALL_ANSWER", toUsername: peerName, sdp: JSON.stringify(answer) });
                } catch { setError(`Could not answer ${peerName}'s call`); }
            } else if (signal.type === "CALL_ANSWER" && signal.sdp) {
                const peer = peersRef.current.get(peerName);
                if (peer) await peer.setRemoteDescription(JSON.parse(signal.sdp) as RTCSessionDescriptionInit);
            } else if (signal.type === "ICE_CANDIDATE" && signal.candidate) {
                const peer = peersRef.current.get(peerName);
                if (peer) {
                    try { await peer.addIceCandidate({ candidate: signal.candidate, sdpMid: signal.sdpMid, sdpMLineIndex: signal.sdpMLineIndex }); }
                    catch { /* Candidate can arrive before remote description; connection may retry via later candidates. */ }
                }
            } else if (signal.type === "CALL_END") {
                closePeer(peerName);
            }
        }

        return () => {
            client.deactivate();
            clientRef.current = null;
            peersRef.current.forEach(peer => peer.close());
            peersRef.current.clear();
        };
    }, [projectId, username, closePeer, createPeer, makeOffer, send]);

    useEffect(() => {
        if (localVideoRef.current) localVideoRef.current.srcObject = localStreamRef.current;
    }, [inCall]);

    const startCall = async () => {
        setError("");
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: true });
            localStreamRef.current = stream;
            setInCall(true);
            if (localVideoRef.current) localVideoRef.current.srcObject = stream;
            send({ type: "CALL_READY" });
            readyPeersRef.current.forEach(peerName => {
                if (username.localeCompare(peerName) < 0 && !peersRef.current.has(peerName)) void makeOffer(peerName);
            });
        } catch (e) {
            setError(e instanceof Error ? e.message : "Camera/microphone permission was denied");
        }
    };

    const endCall = () => {
        send({ type: "CALL_END" });
        peersRef.current.forEach(peer => peer.close());
        peersRef.current.clear();
        readyPeersRef.current.clear();
        localStreamRef.current?.getTracks().forEach(track => track.stop());
        screenStreamRef.current?.getTracks().forEach(track => track.stop());
        localStreamRef.current = null;
        screenStreamRef.current = null;
        setRemoteVideos([]);
        setInCall(false);
        setSharing(false);
        setMuted(false);
        setCameraOff(false);
    };

    const toggleMute = () => {
        const next = !muted;
        localStreamRef.current?.getAudioTracks().forEach(track => { track.enabled = !next; });
        setMuted(next);
    };
    const toggleCamera = () => {
        const next = !cameraOff;
        localStreamRef.current?.getVideoTracks().forEach(track => { track.enabled = !next; });
        setCameraOff(next);
    };
    const shareScreen = async () => {
        try {
            if (sharing) {
                screenStreamRef.current?.getTracks().forEach(track => track.stop());
                screenStreamRef.current = null;
                const cameraTrack = localStreamRef.current?.getVideoTracks()[0];
                if (cameraTrack) peersRef.current.forEach(peer => {
                    const sender = peer.getSenders().find(item => item.track?.kind === "video");
                    if (sender) void sender.replaceTrack(cameraTrack);
                });
                setSharing(false);
                return;
            }
            const stream = await navigator.mediaDevices.getDisplayMedia({ video: true });
            screenStreamRef.current = stream;
            const screenTrack = stream.getVideoTracks()[0];
            peersRef.current.forEach(peer => {
                const sender = peer.getSenders().find(item => item.track?.kind === "video");
                if (sender) void sender.replaceTrack(screenTrack);
            });
            screenTrack.onended = () => { setSharing(false); };
            setSharing(true);
        } catch (e) { setError(e instanceof Error ? e.message : "Screen sharing failed"); }
    };

    return <section className="video-call-panel">
        <div className="video-call-heading"><Video size={14}/> <strong>Voice & video</strong><span className={connected ? "call-connected" : ""}>{connected ? "Signaling ready" : "Connecting..."}</span></div>
        {!inCall ? <button disabled={!connected} onClick={() => void startCall()}><Phone size={13}/> Join / start call</button> : <>
            <div className="video-call-grid">
                <div className="video-call-tile"><video ref={localVideoRef} autoPlay muted playsInline/><small>You {sharing ? "· Sharing screen" : ""}</small></div>
                {remoteVideos.map(remote => <RemoteTile key={remote.username} remote={remote}/>)}
            </div>
            <div className="video-call-controls">
                <button onClick={toggleMute} title={muted ? "Unmute" : "Mute"}>{muted ? <MicOff size={13}/> : <Mic size={13}/>}</button>
                <button onClick={toggleCamera} title={cameraOff ? "Enable camera" : "Disable camera"}>{cameraOff ? <VideoOff size={13}/> : <Video size={13}/>}</button>
                <button onClick={() => void shareScreen()} title="Share screen"><ScreenShare size={13}/>{sharing ? "Stop share" : "Share"}</button>
                <button className="call-end-button" onClick={endCall} title="End call"><PhoneOff size={13}/> End</button>
            </div>
        </>}
        {error && <p className="video-call-error">{error}</p>}
        <p className="video-call-note">Peer-to-peer mesh calling. For reliable calls across restrictive networks, configure a TURN relay in production.</p>
    </section>;
}

function RemoteTile({ remote }: { remote: RemoteVideo }) {
    const ref = useRef<HTMLVideoElement | null>(null);
    useEffect(() => { if (ref.current) ref.current.srcObject = remote.stream; }, [remote.stream]);
    return <div className="video-call-tile"><video ref={ref} autoPlay playsInline/><small>{remote.username}</small></div>;
}
