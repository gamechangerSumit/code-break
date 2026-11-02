import { useCallback, useEffect, useState } from "react";
import { Bell, Copy, History, Link2, RefreshCw, Sparkles } from "lucide-react";
import {
    createProjectInvitation, createSnapshot, listProjectActivity, listVersions,
    restoreVersion, requestAiAssistance, executeProjectCode, type ProjectActivity, type WorkspaceVersion,
} from "./projectToolsApi";
import "./ProjectToolsPanel.css";

interface Props { projectId: number; filePath: string | null; code: string; readOnly: boolean; }
type Tab = "history" | "activity" | "invite" | "ai" | "run" | "preview";

export default function ProjectToolsPanel({ projectId, filePath, code, readOnly }: Props) {
    const [tab, setTab] = useState<Tab>("history");
    const [versions, setVersions] = useState<WorkspaceVersion[]>([]);
    const [activity, setActivity] = useState<ProjectActivity[]>([]);
    const [inviteUrl, setInviteUrl] = useState("");
    const [aiPrompt, setAiPrompt] = useState("");
    const [aiAnswer, setAiAnswer] = useState("");
    const [stdin, setStdin] = useState("");
    const [runOutput, setRunOutput] = useState("");
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
    useEffect(() => {
        if (tab !== "activity") return;
        const timer = window.setInterval(() => { void refresh(); }, 15000);
        return () => window.clearInterval(timer);
    }, [tab, refresh]);

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
    const languageForFile = () => {
        const extension = filePath?.split(".").pop()?.toLowerCase();
        if (extension === "py") return "python";
        if (extension === "java") return "java";
        if (["cpp", "cc", "cxx"].includes(extension ?? "")) return "cpp";
        if (["js", "mjs", "cjs"].includes(extension ?? "")) return "javascript";
        return "";
    };
    const runCode = async () => {
        const language = languageForFile();
        if (!language || !code.trim()) return;
        setBusy(true); setError(""); setRunOutput("");
        try {
            const result = await executeProjectCode(projectId, token, { language, sourceCode: code, stdin });
            setRunOutput([result.status, result.stdout, result.stderr, result.compileOutput, result.message].filter(Boolean).join("\\n\\n"));
        } catch (e) { setError(e instanceof Error ? e.message : "Execution failed"); }
        finally { setBusy(false); }
    };
    const askAi = async () => {
        if (!aiPrompt.trim()) return;
        setBusy(true); setError(""); setAiAnswer("");
        try {
            const result = await requestAiAssistance(projectId, token, { prompt: aiPrompt, code, filePath: filePath ?? "" });
            setAiAnswer(result.answer);
        } catch (e) { setError(e instanceof Error ? e.message : "AI request failed"); }
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
            <button className={tab === "ai" ? "active" : ""} onClick={() => setTab("ai")}><Sparkles size={13}/> AI</button>
            <button className={tab === "run" ? "active" : ""} onClick={() => setTab("run")}><span>▶</span> Run</button>
            <button className={tab === "preview" ? "active" : ""} onClick={() => setTab("preview")}>Preview</button>
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
        {tab === "ai" && <div className="project-tools-body">
            <p className="project-tools-empty">Ask for explanations, debugging ideas, refactors or test suggestions. Configure AI_API_KEY on the server to enable this feature.</p>
            <textarea className="project-tools-ai-prompt" value={aiPrompt} onChange={event => setAiPrompt(event.target.value)} rows={3} maxLength={12000} placeholder="What should I improve or debug?" />
            <button disabled={busy || !aiPrompt.trim()} onClick={() => void askAi()}><Sparkles size={13}/> {busy ? "Thinking..." : "Ask AI"}</button>
            {aiAnswer && <pre className="project-tools-ai-answer">{aiAnswer}</pre>}
        </div>}
        {tab === "run" && <div className="project-tools-body">
            <p className="project-tools-empty">Runs code in a separately hosted, resource-limited Judge0-compatible sandbox. Supported here: Python, Java, C++ and JavaScript.</p>
            <small>{languageForFile() ? `Detected language: ${languageForFile()}` : "Open a .py, .java, .cpp or .js file to run it."}</small>
            <textarea className="project-tools-ai-prompt" value={stdin} onChange={event => setStdin(event.target.value)} rows={2} placeholder="Standard input (optional)" />
            <button disabled={busy || !languageForFile() || !code.trim() || readOnly} onClick={() => void runCode()}>{busy ? "Running..." : "▶ Run code"}</button>
            {runOutput && <pre className="project-tools-ai-answer">{runOutput}</pre>}
        </div>}
        {tab === "preview" && <div className="project-tools-body">
            <p className="project-tools-empty">HTML files render in a sandboxed preview. Scripts run in an isolated iframe without same-origin access.</p>
            {filePath?.toLowerCase().endsWith(".html") ? <iframe className="project-tools-preview" title="Live HTML preview" sandbox="allow-scripts" referrerPolicy="no-referrer" srcDoc={code} /> : <p className="project-tools-empty">Select an HTML file to preview it.</p>}
        </div>}
    </section>;
}
