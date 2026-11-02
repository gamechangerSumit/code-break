import { useEffect, useRef, useState } from "react";
import { Terminal } from "lucide-react";
import { executeProjectCode } from "./projectToolsApi";
import "./WorkspaceTerminal.css";

interface WorkspaceFile { path: string; content: string; }
interface Props { projectId: number; files: WorkspaceFile[]; readOnly: boolean; }
interface TerminalLine { id: number; command: string; output: string; }
const languageForPath = (path: string) => {
    const extension = path.split(".").pop()?.toLowerCase();
    if (extension === "py") return "python";
    if (extension === "java") return "java";
    if (["cpp", "cc", "cxx"].includes(extension ?? "")) return "cpp";
    if (["js", "mjs", "cjs"].includes(extension ?? "")) return "javascript";
    if (extension === "c") return "c";
    if (extension === "go") return "go";
    if (extension === "rs") return "rust";
    if (["sh", "bash"].includes(extension ?? "")) return "bash";
    return "";
};

export default function WorkspaceTerminal({ projectId, files, readOnly }: Props) {
    const [command, setCommand] = useState("");
    const [lines, setLines] = useState<TerminalLine[]>([
        { id: 0, command: "", output: "Code Break Workspace Terminal\nType 'help' to see supported commands. This is a restricted workspace console, not an operating-system shell." },
    ]);
    const [history, setHistory] = useState<string[]>([]);
    const [historyIndex, setHistoryIndex] = useState(-1);
    const [busy, setBusy] = useState(false);
    const outputRef = useRef<HTMLDivElement | null>(null);
    const token = localStorage.getItem("token") ?? "";

    useEffect(() => {
        outputRef.current?.scrollTo({ top: outputRef.current.scrollHeight, behavior: "smooth" });
    }, [lines]);

    const append = (cmd: string, output: string) => {
        setLines(current => [...current, { id: Date.now() + current.length, command: cmd, output }]);
    };

    const resolveFile = (value: string) => {
        const normalized = value.replace(/^\.\//, "").replace(/^\//, "");
        return files.find(file => file.path === normalized) ??
            files.find(file => file.path.split("/").pop() === normalized);
    };

    const execute = async (raw: string) => {
        const input = raw.trim();
        if (!input) return;
        setHistory(current => [...current, input]);
        setHistoryIndex(-1);
        setCommand("");
        const [name, ...args] = input.split(/\s+/);
        const arg = args.join(" ");
        if (name === "clear") { setLines([]); return; }
        if (name === "help") {
            append(input, "Commands:\n  pwd                  Show workspace directory\n  ls [directory]       List workspace files\n  cat <file>           Print a file\n  grep <text> <file>   Search for text in a file\n  run <file>           Execute a supported source file in the isolated runner\n  echo <text>          Print text\n  clear                Clear the terminal\n  help                 Show this help\n\nNo arbitrary OS commands, host filesystem access, or shell pipelines are available.");
            return;
        }
        if (name === "pwd") { append(input, "/workspace"); return; }
        if (name === "echo") { append(input, arg); return; }
        if (name === "ls") {
            const directory = arg.replace(/^\.\//, "").replace(/\/$/, "");
            const entries = files.map(file => file.path).filter(path => !directory || path.startsWith(directory + "/") || path === directory);
            append(input, entries.length ? [...new Set(entries)].sort().join("\n") : "No files found.");
            return;
        }
        if (name === "cat") {
            const file = resolveFile(arg);
            append(input, file ? (file.content.length > 12000 ? file.content.slice(0, 12000) + "\n… output truncated …" : file.content) : `File not found: ${arg}`);
            return;
        }
        if (name === "grep") {
            const split = arg.lastIndexOf(" ");
            if (split < 1) { append(input, "Usage: grep <text> <file>"); return; }
            const query = arg.slice(0, split);
            const file = resolveFile(arg.slice(split + 1));
            if (!file) { append(input, "File not found."); return; }
            const matches = file.content.split("\n").map((line, index) => ({ line, index: index + 1 }))
                .filter(item => item.line.includes(query));
            append(input, matches.length ? matches.map(item => `${item.index}: ${item.line}`).join("\n") : "No matches.");
            return;
        }
        if (name === "run") {
            if (readOnly) { append(input, "Execution requires an editor role."); return; }
            const file = resolveFile(arg);
            if (!file) { append(input, `File not found: ${arg}`); return; }
            const language = languageForPath(file.path);
            if (!language) { append(input, "Unsupported file type. Supported: Python, Java, C/C++, JavaScript, Go, Rust, Bash."); return; }
            setBusy(true);
            try {
                const result = await executeProjectCode(projectId, token, { language, sourceCode: file.content });
                append(input, [`Status: ${result.status}`, result.stdout, result.stderr, result.compileOutput, result.message].filter(Boolean).join("\n\n"));
            } catch (error) {
                append(input, error instanceof Error ? error.message : "Execution request failed.");
            } finally { setBusy(false); }
            return;
        }
        append(input, `Command not found: ${name}. Type 'help' for supported commands.`);
    };

    return <section className="workspace-terminal">
        <div className="workspace-terminal-title"><Terminal size={13}/> Workspace terminal <span>{busy ? "Running…" : "Restricted mode"}</span></div>
        <div className="workspace-terminal-output" ref={outputRef}>
            {lines.map(line => <div className="workspace-terminal-line" key={line.id}>
                {line.command && <div className="workspace-terminal-command"><b>$</b> {line.command}</div>}
                <pre>{line.output}</pre>
            </div>)}
        </div>
        <form className="workspace-terminal-input" onSubmit={event => { event.preventDefault(); void execute(command); }}>
            <b>$</b>
            <input autoComplete="off" spellCheck={false} value={command} onChange={event => setCommand(event.target.value)}
                onKeyDown={event => {
                    if (event.key === "ArrowUp") { event.preventDefault(); const next = history[history.length - 1 - Math.max(0, historyIndex)]; if (next) { setCommand(next); setHistoryIndex(current => Math.min(history.length - 1, current + 1)); } }
                    if (event.key === "ArrowDown") { event.preventDefault(); const nextIndex = historyIndex - 1; setHistoryIndex(nextIndex); setCommand(nextIndex < 0 ? "" : history[history.length - 1 - nextIndex] ?? ""); }
                }}
                placeholder="help, ls, cat src/App.tsx, run main.py…" disabled={busy}/>
            <button type="submit" disabled={busy || !command.trim()}>Run</button>
        </form>
    </section>;
}
