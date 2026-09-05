export interface LocalWorkspaceFile {
    path: string;
    content: string;
}

export interface LocalWorkspaceFolder {
    path: string;
}

export interface LoadedWorkspace {
    files: LocalWorkspaceFile[];
    folders: LocalWorkspaceFolder[];
}

interface DirectoryPickerWindow extends Window {
    showDirectoryPicker?: (
        options?: { mode?: "read" | "readwrite" }
    ) => Promise<FileSystemDirectoryHandle>;
}

let workspaceHandle: FileSystemDirectoryHandle | null = null;
let workspaceMode: "OWNER" | "COLLABORATOR" | null = null;
let workspaceName: string | null = null;

const IGNORED_DIRECTORIES = new Set([
    "node_modules", ".git", "target", "dist", "build", ".next",
    ".idea", ".vscode", "coverage", "__pycache__", ".gradle",
]);

const IGNORED_FILES = new Set([".DS_Store", "Thumbs.db"]);
const MAX_FILE_SIZE = 2 * 1024 * 1024;

export function isFileSystemAccessSupported(): boolean {
    if (typeof window === "undefined") return false;
    return typeof (window as DirectoryPickerWindow).showDirectoryPicker === "function";
}

export function normalizeWorkspacePath(path: string): string {
    return path
        .replaceAll("\\", "/")
        .replace(/^\/+/, "")
        .replace(/\/+$/, "")
        .split("/")
        .filter(part => part.length > 0 && part !== "." && part !== "..")
        .join("/");
}

export async function openLocalFolder(
    mode: "OWNER" | "COLLABORATOR"
): Promise<string> {
    if (!isFileSystemAccessSupported()) {
        throw new Error(
            "Local folder access is not supported. Please use Chrome or Edge."
        );
    }

    const picker =
        (window as DirectoryPickerWindow).showDirectoryPicker;

    if (!picker) {
        throw new Error("Folder picker is not available.");
    }

    let handle: FileSystemDirectoryHandle;

    try {
        handle = await picker.call(window, { mode: "readwrite" });
    } catch (error) {
        if (
            error instanceof DOMException &&
            error.name === "AbortError"
        ) {
            throw error;
        }
        throw new Error("Unable to open the selected folder.");
    }

    workspaceHandle = handle;
    workspaceMode = mode;
    workspaceName = handle.name;

    return handle.name;
}

export function getWorkspaceHandle(): FileSystemDirectoryHandle | null {
    return workspaceHandle;
}

export function getWorkspaceName(): string | null {
    return workspaceName;
}

export function getWorkspaceMode(): "OWNER" | "COLLABORATOR" | null {
    return workspaceMode;
}

export function hasWorkspace(): boolean {
    return workspaceHandle !== null;
}

export function clearWorkspace(): void {
    workspaceHandle = null;
    workspaceMode = null;
    workspaceName = null;
}

function requireWorkspace(): FileSystemDirectoryHandle {
    if (!workspaceHandle) {
        throw new Error(
            "No local workspace is connected. Please click Open Folder first."
        );
    }
    return workspaceHandle;
}

function getPathParts(path: string): string[] {
    const normalized = normalizeWorkspacePath(path);
    return normalized ? normalized.split("/").filter(Boolean) : [];
}

async function getParentDirectory(
    root: FileSystemDirectoryHandle,
    path: string,
    createDirectories: boolean
): Promise<{
    directory: FileSystemDirectoryHandle;
    name: string;
}> {
    const parts = getPathParts(path);

    if (parts.length === 0) {
        throw new Error("Invalid workspace path.");
    }

    const name = parts.pop();

    if (!name) {
        throw new Error("Invalid workspace path.");
    }

    let directory = root;

    for (const part of parts) {
        directory = await directory.getDirectoryHandle(part, {
            create: createDirectories,
        });
    }

    return { directory, name };
}

async function readDirectory(
    directory: FileSystemDirectoryHandle,
    currentPath: string,
    files: LocalWorkspaceFile[],
    folders: LocalWorkspaceFolder[]
): Promise<void> {
    for await (const entry of directory.values()) {
        if (
            entry.name.startsWith(".") &&
            entry.name !== ".env"
        ) {
            continue;
        }

        const path =
            currentPath
                ? `${currentPath}/${entry.name}`
                : entry.name;

        if (entry.kind === "directory") {
            if (IGNORED_DIRECTORIES.has(entry.name)) {
                continue;
            }

            folders.push({ path });

            await readDirectory(
                entry as FileSystemDirectoryHandle,
                path,
                files,
                folders
            );

            continue;
        }

        if (entry.kind !== "file") {
            continue;
        }

        if (IGNORED_FILES.has(entry.name)) {
            continue;
        }

        try {
            const file =
                await (entry as FileSystemFileHandle).getFile();

            if (file.size > MAX_FILE_SIZE) {
                continue;
            }

            const sample =
                new Uint8Array(
                    await file
                        .slice(0, Math.min(file.size, 8192))
                        .arrayBuffer()
                );

            if (sample.some(byte => byte === 0)) {
                continue;
            }

            files.push({
                path,
                content: await file.text(),
            });
        } catch {
            // Skip unreadable files.
        }
    }
}

export async function loadWorkspace(): Promise<LoadedWorkspace> {
    const root = requireWorkspace();

    const files: LocalWorkspaceFile[] = [];
    const folders: LocalWorkspaceFolder[] = [];

    await readDirectory(root, "", files, folders);

    files.sort((a, b) => a.path.localeCompare(b.path));
    folders.sort((a, b) => a.path.localeCompare(b.path));

    return { files, folders };
}

export async function writeWorkspaceFile(
    path: string,
    content: string
): Promise<void> {
    const root = requireWorkspace();
    const normalized = normalizeWorkspacePath(path);

    if (!normalized) {
        throw new Error("File path is required.");
    }

    const { directory, name } =
        await getParentDirectory(root, normalized, true);

    const fileHandle =
        await directory.getFileHandle(name, { create: true });

    const writable = await fileHandle.createWritable();

    try {
        await writable.write(content ?? "");
    } finally {
        await writable.close();
    }
}

export async function createWorkspaceFile(
    path: string,
    content = ""
): Promise<void> {
    const root = requireWorkspace();
    const normalized = normalizeWorkspacePath(path);

    if (!normalized) {
        throw new Error("File path is required.");
    }

    const { directory, name } =
        await getParentDirectory(root, normalized, true);

    try {
        await directory.getFileHandle(name);
        throw new Error(`File already exists: ${normalized}`);
    } catch (error) {
        if (
            error instanceof Error &&
            error.message.startsWith("File already exists:")
        ) {
            throw error;
        }

        if (
            !(
                error instanceof DOMException &&
                error.name === "NotFoundError"
            )
        ) {
            if (
                error instanceof Error &&
                !error.message.toLowerCase().includes("not found")
            ) {
                throw error;
            }
        }
    }

    await writeWorkspaceFile(normalized, content);
}

export async function createWorkspaceFolder(
    path: string
): Promise<void> {
    const root = requireWorkspace();
    const normalized = normalizeWorkspacePath(path);

    if (!normalized) {
        throw new Error("Folder path is required.");
    }

    let directory = root;

    for (const part of getPathParts(normalized)) {
        directory = await directory.getDirectoryHandle(part, {
            create: true,
        });
    }
}

export async function deleteWorkspaceFile(
    path: string
): Promise<void> {
    const root = requireWorkspace();

    const { directory, name } =
        await getParentDirectory(root, path, false);

    await directory.removeEntry(name);
}

export async function deleteWorkspaceFolder(
    path: string
): Promise<void> {
    const root = requireWorkspace();

    const { directory, name } =
        await getParentDirectory(root, path, false);

    await directory.removeEntry(name, { recursive: true });
}

export async function renameWorkspaceFile(
    oldPath: string,
    newPath: string
): Promise<void> {
    const root = requireWorkspace();

    const oldNormalized = normalizeWorkspacePath(oldPath);
    const newNormalized = normalizeWorkspacePath(newPath);

    if (!oldNormalized || !newNormalized) {
        throw new Error("Both old and new file paths are required.");
    }

    if (oldNormalized === newNormalized) {
        return;
    }

    const source =
        await getParentDirectory(root, oldNormalized, false);

    const destination =
        await getParentDirectory(root, newNormalized, true);

    try {
        await destination.directory.getFileHandle(destination.name);
        throw new Error(`File already exists: ${newNormalized}`);
    } catch (error) {
        if (
            error instanceof Error &&
            error.message.startsWith("File already exists:")
        ) {
            throw error;
        }

        if (
            !(
                error instanceof DOMException &&
                error.name === "NotFoundError"
            )
        ) {
            if (error instanceof Error) {
                throw error;
            }
        }
    }

    const file =
        await source.directory
            .getFileHandle(source.name)
            .then(handle => handle.getFile());

    const destinationHandle =
        await destination.directory.getFileHandle(
            destination.name,
            { create: true }
        );

    const writable =
        await destinationHandle.createWritable();

    try {
        await writable.write(await file.arrayBuffer());
    } finally {
        await writable.close();
    }

    await source.directory.removeEntry(source.name);
}

export async function renameWorkspaceFolder(
    oldPath: string,
    newPath: string
): Promise<void> {
    const root = requireWorkspace();

    const oldNormalized = normalizeWorkspacePath(oldPath);
    const newNormalized = normalizeWorkspacePath(newPath);

    if (!oldNormalized || !newNormalized) {
        throw new Error("Both old and new folder paths are required.");
    }

    if (oldNormalized === newNormalized) {
        return;
    }

    if (
        newNormalized.startsWith(`${oldNormalized}/`)
    ) {
        throw new Error(
            "A folder cannot be moved inside itself."
        );
    }

    const source =
        await getParentDirectory(root, oldNormalized, false);

    const sourceHandle =
        await source.directory.getDirectoryHandle(source.name);

    const destination =
        await getParentDirectory(root, newNormalized, true);

    try {
        await destination.directory.getDirectoryHandle(destination.name);
        throw new Error(`Folder already exists: ${newNormalized}`);
    } catch (error) {
        if (
            error instanceof Error &&
            error.message.startsWith("Folder already exists:")
        ) {
            throw error;
        }

        if (
            !(
                error instanceof DOMException &&
                error.name === "NotFoundError"
            )
        ) {
            if (error instanceof Error) {
                throw error;
            }
        }
    }

    const destinationHandle =
        await destination.directory.getDirectoryHandle(
            destination.name,
            { create: true }
        );

    const copyDirectory =
        async (
            from: FileSystemDirectoryHandle,
            to: FileSystemDirectoryHandle
        ): Promise<void> => {
            for await (const entry of from.values()) {
                if (
                    entry.name.startsWith(".") &&
                    entry.name !== ".env"
                ) {
                    continue;
                }

                if (entry.kind === "file") {
                    const file =
                        await (entry as FileSystemFileHandle).getFile();

                    const handle =
                        await to.getFileHandle(
                            entry.name,
                            { create: true }
                        );

                    const writable =
                        await handle.createWritable();

                    try {
                        await writable.write(
                            await file.arrayBuffer()
                        );
                    } finally {
                        await writable.close();
                    }
                } else if (entry.kind === "directory") {
                    if (
                        IGNORED_DIRECTORIES.has(entry.name)
                    ) {
                        continue;
                    }

                    const child =
                        await to.getDirectoryHandle(
                            entry.name,
                            { create: true }
                        );

                    await copyDirectory(
                        entry as FileSystemDirectoryHandle,
                        child
                    );
                }
            }
        };

    await copyDirectory(
        sourceHandle,
        destinationHandle
    );

    await source.directory.removeEntry(
        source.name,
        { recursive: true }
    );
}

export async function saveWorkspace(
    files: LocalWorkspaceFile[]
): Promise<void> {
    requireWorkspace();

    for (const file of files) {
        await writeWorkspaceFile(
            file.path,
            file.content ?? ""
        );
    }
}

export async function saveProjectToDevice(
    files: LocalWorkspaceFile[]
): Promise<string> {
    if (!workspaceHandle) {
        await openLocalFolder("COLLABORATOR");
    }

    await saveWorkspace(files);

    return workspaceName ?? "Local workspace";
}
