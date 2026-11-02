import { useEffect, useMemo, useState } from "react";
import { Clock3, GitCompareArrows, History, RotateCcw, Save, X } from "lucide-react";
import {
    createSnapshot,
    getSnapshot,
    listSnapshots,
    restoreSnapshot,
    type WorkspaceSnapshotDetail,
    type WorkspaceSnapshotSummary,
} from "./historyApi";

interface HistoryPanelProps {
    projectId: number;
    filePath: string;
    currentContent: string;
    token: string;
    readOnly: boolean;
    onClose: () => void;
    onRestored: (content: string) => void;
}

export default function HistoryPanel({
    projectId, filePath, currentContent, token, readOnly, onClose, onRestored,
}: HistoryPanelProps) {
    const [items, setItems] = useState<WorkspaceSnapshotSummary[]>([]);
    const [selected, setSelected] = useState<WorkspaceSnapshotDetail | null>(null);
    const [message, setMessage] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");

    const refresh = async () => {
        const result = await listSnapshots(projectId, filePath, token);
        setItems(result);
    };

    useEffect(() => {
        let active = true;
        listSnapshots(projectId, filePath, token)
            .then((result) => { if (active) setItems(result); })
            .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : "Could not load history"); });
        return () => { active = false; };
    }, [projectId, filePath, token]);

    const diff = useMemo(() => {
        if (!selected) return [];
        const before = selected.content.split("\n");
        const after = currentContent.split("\n");
        const max = Math.max(before.length, after.length);
        const lines: Array<{ kind: "same" | "removed" | "added"; text: string }> = [];
        for (let i = 0; i < max; i += 1) {
            if (before[i] === after[i]) {
                if (before[i] !== undefined) lines.push({ kind: "same", text: `  ${before[i]}` });
            } else {
                if (before[i] !== undefined) lines.push({ kind: "removed", text: `- ${before[i]}` });
                if (after[i] !== undefined) lines.push({ kind: "added", text: `+ ${after[i]}` });
            }
        }
        return lines;
    }, [selected, currentContent]);

    const create = async () => {
        setBusy(true); setError("");
        try {
            await createSnapshot(projectId, filePath, token, message.trim());
            setMessage("");
            await refresh();
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "Snapshot failed");
        } finally { setBusy(false); }
    };

    const inspect = async (id: number) => {
        setBusy(true); setError("");
        try { setSelected(await getSnapshot(projectId, id, token)); }
        catch (reason) { setError(reason instanceof Error ? reason.message : "Could not open snapshot"); }
        finally { setBusy(false); }
    };

    const restore = async () => {
        if (!selected || readOnly || !window.confirm(`Restore ${selected.path} to this snapshot?`)) return;
        setBusy(true); setError("");
        try {
            await restoreSnapshot(projectId, selected.id, token);
            onRestored(selected.content);
            await refresh();
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "Restore failed");
        } finally { setBusy(false); }
    };

    return (
        <div className="history-overlay" role="dialog" aria-modal="true" aria-label="File version history">
            <section className="history-panel">
                <header className="history-panel-header">
                    <div><History size={17} /><div><strong>Version history</strong><span>{filePath}</span></div></div>
                    <button type="button" onClick={onClose} aria-label="Close history"><X size={17} /></button>
                </header>
                {error && <p className="history-error">{error}</p>}
                <div className="history-create">
                    <input value={message} onChange={(event) => setMessage(event.target.value)} maxLength={240} placeholder="Snapshot note (optional)" disabled={readOnly || busy} />
                    <button type="button" onClick={() => void create()} disabled={readOnly || busy}><Save size={14} /> Save snapshot</button>
                </div>
                <div className="history-content">
                    <div className="history-list">
                        {items.length === 0 ? <p className="history-empty">No snapshots yet. Save one before making a risky change.</p> : items.map((item) => (
                            <button type="button" className={selected?.id === item.id ? "history-item selected" : "history-item"} key={item.id} onClick={() => void inspect(item.id)}>
                                <span><Clock3 size={13} /> {new Date(item.createdAt).toLocaleString()}</span>
                                <strong>{item.message || "Saved version"}</strong>
                                <small>{item.createdBy} · {item.contentLength.toLocaleString()} characters</small>
                            </button>
                        ))}
                    </div>
                    <div className="history-diff">
                        {selected ? <>
                            <div className="history-diff-header"><GitCompareArrows size={14} /> Snapshot #{selected.id} compared with current editor</div>
                            <pre>{diff.map((line, index) => <span className={`history-diff-${line.kind}`} key={index}>{line.text}\n</span>)}</pre>
                            <button type="button" className="history-restore" onClick={() => void restore()} disabled={readOnly || busy}><RotateCcw size={14} /> Restore this version</button>
                        </> : <p className="history-empty">Choose a snapshot to inspect its changes.</p>}
                    </div>
                </div>
            </section>
        </div>
    );
}
