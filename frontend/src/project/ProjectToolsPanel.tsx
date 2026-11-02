import { useCallback, useEffect, useState } from "react";
import { Bell, Copy, History, Link2, RefreshCw, Sparkles } from "lucide-react";
import {
    createProjectInvitation, createSnapshot, listProjectActivity, listVersions,
    restoreVersion, compareVersions, requestAiAssistance, executeProjectCode, connectGithub, listGithubRepositories, disconnectGithub, importGithubRepository, getGithubConnection, pushGithubWorkspace, type GithubRepository, type ProjectActivity, type WorkspaceVersion, type VersionComparison,
} from "./projectToolsApi";
import "./ProjectToolsPanel.css";
import VideoCallPanel from "./VideoCallPanel";
import WorkspaceTerminal from "./WorkspaceTerminal";

interface Props { projectId: number; filePath: string | null; code: string; files: Array<{ path: string; content: string }>; readOnly: boolean; username: string; }
type Tab = "history" | "activity" | "invite" | "ai" | "run" | "preview" | "github" | "call" | "terminal";

export default function ProjectToolsPanel({ projectId, filePath, code, files, readOnly, username }: Props) {
    const [tab, setTab] = useState<Tab>("history");
    const [versions, setVersions] = useState<WorkspaceVersion[]>([]);
    const [comparisonIds, setComparisonIds] = useState<number[]>([]);
    const [comparison, setComparison] = useState<VersionComparison | null>(null);
    const [activity, setActivity] = useState<ProjectActivity[]>([]);
    const [inviteUrl, setInviteUrl] = useState("");
    const [aiPrompt, setAiPrompt] = useState("");
    const [aiAnswer, setAiAnswer] = useState("");
    const [stdin, setStdin] = useState("");
    const [runOutput, setRunOutput] = useState("");
    const [githubToken, setGithubToken] = useState("");
    const [githubUser, setGithubUser] = useState("");
    const [githubRepos, setGithubRepos] = useState<GithubRepository[]>([]);
    const [githubImportStatus, setGithubImportStatus] = useState("");
    const [githubCommitMessage, setGithubCommitMessage] = useState("Update from Code Break");
    const [githubPushStatus, setGithubPushStatus] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const token = localStorage.getItem("token") ?? "";

    const refresh = useCallback(async () => {
        if (!token) return;
        setError("");
        try {
            if (tab === "history" && filePath) setVersions(await listVersions(projectId, filePath, token));
            if (tab === "activity") setActivity(await listProjectActivity(projectId, token));
            if (tab === "github") {
                try {
                    const connection = await getGithubConnection(token);
                    setGithubUser(connection.githubUsername);
                    setGithubRepos(await listGithubRepositories(token));
                } catch {
                    setGithubUser(""); setGithubRepos([]);
                }
            }
        } catch (e) { setError(e instanceof Error ? e.message : "Unable to load project tools"); }
    }, [projectId, filePath, tab, token]);

    useEffect(() => { void refresh(); }, [refresh]);
    useEffect(() => {
        if (tab !== "activity") return;
        const timer = window.setInterval(() => { void refresh(); }, 15000);
        return () => window.clearInterval(timer);
    }, [tab, refresh]);

    const selectForComparison = async (version: WorkspaceVersion) => {
        setComparison(null);
        if (comparisonIds.length === 0 || comparisonIds.length >= 2) {
            setComparisonIds([version.id]);
            return;
        }
        if (comparisonIds[0] === version.id) return;
        const next = [...comparisonIds, version.id];
        setComparisonIds(next);
        setBusy(true); setError("");
        try { setComparison(await compareVersions(projectId, next[0], next[1], token)); }
        catch (e) { setError(e instanceof Error ? e.message : "Could not compare versions"); }
        finally { setBusy(false); }
    };
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
        if (extension === "c") return "c";
        if (extension === "go") return "go";
        if (extension === "rs") return "rust";
        if (["sh", "bash"].includes(extension ?? "")) return "bash";
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
    const connectGithubAccount = async () => {
        if (!githubToken.trim()) return;
        setBusy(true); setError("");
        try {
            const connection = await connectGithub(githubToken, token);
            setGithubUser(connection.githubUsername); setGithubToken("");
            setGithubRepos(await listGithubRepositories(token));
        } catch (e) { setError(e instanceof Error ? e.message : "GitHub connection failed"); }
        finally { setBusy(false); }
    };
    const loadGithubRepos = async () => {
        setBusy(true); setError("");
        try { setGithubRepos(await listGithubRepositories(token)); }
        catch (e) { setError(e instanceof Error ? e.message : "Could not load GitHub repositories"); }
        finally { setBusy(false); }
    };
    const importRepository = async (repo: GithubRepository) => {
        setBusy(true); setError(""); setGithubImportStatus("");
        try {
            const result = await importGithubRepository(projectId, token, repo.fullName, repo.defaultBranch);
            setGithubImportStatus(`Imported ${result.importedFiles} files (${Math.round(result.bytes / 1024)} KB) from ${result.repository}. Refresh the explorer to view them.`);
        } catch (e) { setError(e instanceof Error ? e.message : "Repository import failed"); }
        finally { setBusy(false); }
    };
    const pushRepository = async (repo: GithubRepository) => {
        if (!window.confirm(`Push the current workspace files to ${repo.fullName} on ${repo.defaultBranch}?`)) return;
        setBusy(true); setError(""); setGithubPushStatus("");
        try {
            const result = await pushGithubWorkspace(projectId, token, repo.fullName, repo.defaultBranch, githubCommitMessage);
            setGithubPushStatus(`Pushed ${result.pushedFiles} files to ${result.repository} (${result.branch}) · ${result.commitSha.slice(0, 8)}`);
        } catch (e) { setError(e instanceof Error ? e.message : "GitHub push failed"); }
        finally { setBusy(false); }
    };
    const disconnectGithubAccount = async () => {
        setBusy(true);
        try { await disconnectGithub(token); setGithubUser(""); setGithubRepos([]); }
        catch (e) { setError(e instanceof Error ? e.message : "Disconnect failed"); }
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
            <button className={tab === "github" ? "active" : ""} onClick={() => setTab("github")}>GitHub</button>
            <button className={tab === "call" ? "active" : ""} onClick={() => setTab("call")}>Call</button>
            <button className={tab === "terminal" ? "active" : ""} onClick={() => setTab("terminal")}>Terminal</button>
        </div>
        {error && <p className="project-tools-error">{error}</p>}
        {tab === "history" && <div className="project-tools-body">
            <div className="project-tools-heading"><span>{filePath ?? "Select a file"}</span>
                <button disabled={!filePath || readOnly || busy} onClick={() => void snapshot()}><History size={12}/> Snapshot</button></div>
            {versions.length === 0 ? <p className="project-tools-empty">No saved snapshots for this file yet.</p> : versions.map(v =>
                <div className="project-tools-item" key={v.id}><div><strong>Version {v.versionNumber}</strong><small>{v.createdBy} · {new Date(v.createdAt).toLocaleString()}</small><small>{v.message}</small></div>
                    <div className="project-tools-version-actions">
                        <button disabled={busy} onClick={() => void selectForComparison(v)}>{comparisonIds.includes(v.id) ? "Selected" : "Compare"}</button>
                        <button disabled={readOnly || busy} onClick={() => void restore(v)}>Restore</button>
                    </div></div>)}
            {comparisonIds.length > 0 && <button className="project-tools-refresh" onClick={() => { setComparisonIds([]); setComparison(null); }}>Clear comparison</button>}
            {comparison && <div className="project-tools-diff">
                <div><strong>Version {comparison.left.versionNumber}</strong><pre>{comparison.leftContent}</pre></div>
                <div><strong>Version {comparison.right.versionNumber}</strong><pre>{comparison.rightContent}</pre></div>
            </div>}
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
            <small>{languageForFile() ? `Detected language: ${languageForFile()}` : "Open a supported source file (.py, .java, .cpp, .js, .c, .go, .rs or .sh)."}</small>
            <textarea className="project-tools-ai-prompt" value={stdin} onChange={event => setStdin(event.target.value)} rows={2} placeholder="Standard input (optional)" />
            <button disabled={busy || !languageForFile() || !code.trim() || readOnly} onClick={() => void runCode()}>{busy ? "Running..." : "▶ Run code"}</button>
            {runOutput && <pre className="project-tools-ai-answer">{runOutput}</pre>}
        </div>}
        {tab === "preview" && <div className="project-tools-body">
            <p className="project-tools-empty">HTML files render in a sandboxed preview. Scripts run in an isolated iframe without same-origin access.</p>
            {filePath?.toLowerCase().endsWith(".html") ? <iframe className="project-tools-preview" title="Live HTML preview" sandbox="allow-scripts" referrerPolicy="no-referrer" srcDoc={code} /> : <p className="project-tools-empty">Select an HTML file to preview it.</p>}
        </div>}
        {tab === "github" && <div className="project-tools-body">
            <p className="project-tools-empty">Connect a GitHub fine-grained personal access token. It is encrypted at rest on the backend; use minimum repository read permissions. Do not paste tokens into chat.</p>
            {!githubUser ? <>
                <input className="project-tools-ai-prompt" type="password" autoComplete="off" value={githubToken} onChange={event => setGithubToken(event.target.value)} placeholder="github_pat_..." />
                <button disabled={busy || !githubToken.trim()} onClick={() => void connectGithubAccount()}>Connect GitHub</button>
            </> : <>
                <strong>Connected as @{githubUser}</strong>
                <button disabled={busy} onClick={() => void loadGithubRepos()}><RefreshCw size={12}/> Load repositories</button>
                <button disabled={busy} onClick={() => void disconnectGithubAccount()}>Disconnect</button>
                <input className="project-tools-ai-prompt" value={githubCommitMessage} onChange={event => setGithubCommitMessage(event.target.value)} maxLength={200} placeholder="Commit message" />
                <p className="project-tools-empty">Push updates/adds workspace files. It does not delete files already present in the remote repository.</p>
                {githubRepos.map(repo => <div className="project-tools-repo-row" key={repo.fullName}>
                    <a className="project-tools-repo" href={repo.url} target="_blank" rel="noreferrer">{repo.fullName}{repo.isPrivate ? " · private" : ""}</a>
                    <button disabled={busy || readOnly} onClick={() => void importRepository(repo)}>Import</button>
                    <button disabled={busy || readOnly || !githubCommitMessage.trim()} onClick={() => void pushRepository(repo)}>Push</button>
                </div>)}
                {githubImportStatus && <p className="project-tools-empty">{githubImportStatus}</p>}
                {githubPushStatus && <p className="project-tools-empty">{githubPushStatus}</p>}
            </>}
        </div>}
        {tab === "call" && <VideoCallPanel projectId={projectId} username={username} />}
        {tab === "terminal" && <WorkspaceTerminal projectId={projectId} files={files} readOnly={readOnly} />}
    </section>;
}
