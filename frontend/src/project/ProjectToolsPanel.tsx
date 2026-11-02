import { useCallback, useEffect, useState } from "react";
import { Bell, Clock3, Copy, History, Link2, RefreshCw } from "lucide-react";
import {
    createProjectInvitation, createSnapshot, listProjectActivity, listVersions,
    restoreVersion, type ProjectActivity, type WorkspaceVersion,
} from "./projectToolsApi";
import "./ProjectToolsPanel.css";

interface Props { projectId: number; filePath: string | null; readOnly: boolean; }
type Tab = "history" | "activity" | "invite";

export default function ProjectToolsPanel({ projectId, filePath, readOnly }: Props) {
    const [tab, setTab] = useState<Tab>("history");
    const [versions, setVersions] = useState<WorkspaceVersion[]>([]);
    const [activity, setActivity] = useState<ProjectActivity[]>([]);
    const [inviteUrl, setInviteUrl] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const token = localStorage.getItem("token") ?? "";

    const refresh = useCallback(async () => {
        if (!token) return;
        setError("");
        try {
            if (tab === "history" && filePath) setVersions(await listVersions(projectId, filePath, token));
            if (tab === "activity") setActivity(await listProjectActivity(projectId, token));
        } catch (e) { setError(e instanceof Error ? e.message : "Unable to load project tools"); }
    }, [projectId, filePath, tab, token]);

    useEffect(() => { void refresh(); }, [refresh]);

    const snapshot = async () => {
        if (!filePath) return;
        setBusy(true);
        try { await createSnapshot(projectId, filePath, token); await refresh(); }
        catch (e) { setError(e instanceof Error ? e.message : "Snapshot failed"); }
        finally { setBusy(false); }
    };
    const restore = async (version: WorkspaceVersion) => {
        if (!window.confirm(`Restore ${version.filePath} to version ${version.versionNumber}?`)) return;
        setBusy(true);
        try { await restoreVersion(projectId, version.id, token); await refresh(); window.location.reload(); }
        catch (e) { setError(e instanceof Error ? e.message : "Restore failed"); }
        finally { setBusy(false); }
    };
    const invite = async () => {
        setBusy(true);
        try {
            const result = await createProjectInvitation(projectId, token);
            setInviteUrl(`${window.location.origin}/invite/${result.token}`);
        } catch (e) { setError(e instanceof Error ? e.message : "Invitation failed"); }
        finally { setBusy(false); }
    };

    return <section className="project-tools">
        <div className="project-tools-tabs">
            <button className={tab === "history" ? "active" : ""} onClick={() => setTab("history")}><History size={13}/> History</button>
            <button className={tab === "activity" ? "active" : ""} onClick={() => setTab("activity")}><Bell size={13}/> Activity</button>
            <button className={tab === "invite" ? "active" : ""} onClick={() => setTab("invite")}><Link2 size={13}/> Invite</button>
        </div>
        {error && <p className="project-tools-error">{error}</p>}
        {tab === "history" && <div className="project-tools-body">
            <div className="project-tools-heading"><span>{filePath ?? "Select a file"}</span>
                <button disabled={!filePath || readOnly || busy} onClick={() => void snapshot()}><History size={12}/> Snapshot</button></div>
            {versions.length === 0 ? <p className="project-tools-empty">No saved snapshots for this file yet.</p> : versions.map(v =>
                <div className="project-tools-item" key={v.id}><div><strong>Version {v.versionNumber}</strong><small>{v.createdBy} · {new Date(v.createdAt).toLocaleString()}</small><small>{v.message}</small></div>
                    <button disabled={readOnly || busy} onClick={() => void restore(v)}>Restore</button></div>)}
        </div>}
        {tab === "activity" && <div className="project-tools-body">
            {activity.length === 0 ? <p className="project-tools-empty">No project activity yet.</p> : activity.map(item =>
                <div className="project-tools-item" key={item.id}><div><strong>{item.message}</strong><small>{item.actor} · {new Date(item.createdAt).toLocaleString()}</small></div></div>)}
            <button className="project-tools-refresh" onClick={() => void refresh()}><RefreshCw size={12}/> Refresh</button>
        </div>}
        {tab === "invite" && <div className="project-tools-body">
            <p className="project-tools-empty">Create a single-use invitation link. It expires after 7 days.</p>
            <button disabled={busy || readOnly} onClick={() => void invite()}><Link2 size={13}/> Create invitation</button>
            {inviteUrl && <div className="project-tools-invite"><input readOnly value={inviteUrl}/><button aria-label="Copy invite link" onClick={() => void navigator.clipboard.writeText(inviteUrl)}><Copy size={13}/></button></div>}
        </div>}
    </section>;
}
