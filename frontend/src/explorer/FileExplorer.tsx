import {
    useMemo,
    useState,
} from "react";

import {
    ChevronDown,
    ChevronRight,
    File,
    Folder,
    FolderOpen,
    MoreHorizontal,
    Package,
    Plus,
    RefreshCw,
    Trash2,
    Pencil,
} from "lucide-react";


// =====================================================
// TYPES
// =====================================================

export interface ExplorerFile {
    path: string;
    name: string;
    content: string;
}

interface FileExplorerProps {
    files: ExplorerFile[];
    folders: string[];
    selectedFile: string;
    workspaceName: string | null;

    onSelectFile: (
        file: ExplorerFile
    ) => void;

    onOpenFolder: () => void;

    onCreateFile: () => void;

    onCreateFolder: () => void;

    onDeleteFile: (
        file: ExplorerFile
    ) => void;

    onDeleteFolder: (
        path: string
    ) => void;

    onRenameFile: (
        file: ExplorerFile
    ) => void;

    onRenameFolder: (
        path: string
    ) => void;

    readOnly?: boolean;
}


// =====================================================
// TREE
// =====================================================

interface TreeNode {
    name: string;
    path: string;
    type: "file" | "folder";
    children: TreeNode[];
    file?: ExplorerFile;
}


// =====================================================
// FILE TYPE
// =====================================================

type FileKind =
    | "typescript"
    | "javascript"
    | "react"
    | "java"
    | "python"
    | "html"
    | "css"
    | "json"
    | "markdown"
    | "xml"
    | "yaml"
    | "docker"
    | "shell"
    | "git"
    | "image"
    | "package"
    | "text"
    | "unknown";


// =====================================================
// FILE TYPE DETECTION
// =====================================================

function getFileKind(
    name: string
): FileKind {

    const lower =
        name.toLowerCase();


    // -----------------------------------------------
    // SPECIAL FILES
    // -----------------------------------------------

    if (
        lower === "package.json" ||
        lower === "package-lock.json" ||
        lower === "pnpm-lock.yaml" ||
        lower === "yarn.lock"
    ) {
        return "package";
    }

    if (
        lower === "dockerfile" ||
        lower.startsWith("dockerfile.")
    ) {
        return "docker";
    }

    if (
        lower === ".gitignore" ||
        lower === ".gitattributes" ||
        lower === ".gitmodules"
    ) {
        return "git";
    }


    // -----------------------------------------------
    // REACT
    // -----------------------------------------------

    if (
        lower.endsWith(".tsx") ||
        lower.endsWith(".jsx")
    ) {
        return "react";
    }


    // -----------------------------------------------
    // TYPESCRIPT
    // -----------------------------------------------

    if (
        lower.endsWith(".ts") ||
        lower.endsWith(".d.ts")
    ) {
        return "typescript";
    }


    // -----------------------------------------------
    // JAVASCRIPT
    // -----------------------------------------------

    if (
        lower.endsWith(".js") ||
        lower.endsWith(".mjs") ||
        lower.endsWith(".cjs")
    ) {
        return "javascript";
    }


    // -----------------------------------------------
    // JAVA
    // -----------------------------------------------

    if (
        lower.endsWith(".java")
    ) {
        return "java";
    }


    // -----------------------------------------------
    // PYTHON
    // -----------------------------------------------

    if (
        lower.endsWith(".py") ||
        lower.endsWith(".pyw")
    ) {
        return "python";
    }


    // -----------------------------------------------
    // HTML
    // -----------------------------------------------

    if (
        lower.endsWith(".html") ||
        lower.endsWith(".htm")
    ) {
        return "html";
    }


    // -----------------------------------------------
    // CSS
    // -----------------------------------------------

    if (
        lower.endsWith(".css") ||
        lower.endsWith(".scss") ||
        lower.endsWith(".sass") ||
        lower.endsWith(".less")
    ) {
        return "css";
    }


    // -----------------------------------------------
    // JSON
    // -----------------------------------------------

    if (
        lower.endsWith(".json")
    ) {
        return "json";
    }


    // -----------------------------------------------
    // MARKDOWN
    // -----------------------------------------------

    if (
        lower.endsWith(".md") ||
        lower.endsWith(".mdx")
    ) {
        return "markdown";
    }


    // -----------------------------------------------
    // XML
    // -----------------------------------------------

    if (
        lower.endsWith(".xml") ||
        lower.endsWith(".svg")
    ) {
        return "xml";
    }


    // -----------------------------------------------
    // YAML
    // -----------------------------------------------

    if (
        lower.endsWith(".yaml") ||
        lower.endsWith(".yml")
    ) {
        return "yaml";
    }


    // -----------------------------------------------
    // SHELL
    // -----------------------------------------------

    if (
        lower.endsWith(".sh") ||
        lower.endsWith(".bash") ||
        lower.endsWith(".zsh")
    ) {
        return "shell";
    }


    // -----------------------------------------------
    // IMAGES
    // -----------------------------------------------

    if (
        lower.endsWith(".png") ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".gif") ||
        lower.endsWith(".webp") ||
        lower.endsWith(".ico")
    ) {
        return "image";
    }


    // -----------------------------------------------
    // TEXT
    // -----------------------------------------------

    if (
        lower.endsWith(".txt") ||
        lower.endsWith(".log")
    ) {
        return "text";
    }


    return "unknown";
}


// =====================================================
// FILE ICON
// =====================================================

function FileIcon({
    name,
}: {
    name: string;
}) {

    const kind =
        getFileKind(name);


    // =================================================
    // REACT / TSX
    // =================================================

    if (kind === "react") {

        return (
            <span
                className="vs-file-icon vs-file-icon-react"
                title="React"
            >
                <span>
                    ⚛
                </span>
            </span>
        );
    }


    // =================================================
    // TYPESCRIPT
    // =================================================

    if (kind === "typescript") {

        return (
            <span
                className="vs-file-icon vs-file-icon-typescript"
                title="TypeScript"
            >
                TS
            </span>
        );
    }


    // =================================================
    // JAVASCRIPT
    // =================================================

    if (kind === "javascript") {

        return (
            <span
                className="vs-file-icon vs-file-icon-javascript"
                title="JavaScript"
            >
                JS
            </span>
        );
    }


    // =================================================
    // JAVA
    // =================================================

    if (kind === "java") {

        return (
            <span
                className="vs-file-icon vs-file-icon-java"
                title="Java"
            >
                ☕
            </span>
        );
    }


    // =================================================
    // PYTHON
    // =================================================

    if (kind === "python") {

        return (
            <span
                className="vs-file-icon vs-file-icon-python"
                title="Python"
            >
                <span className="python-mark">
                    ●
                </span>
            </span>
        );
    }


    // =================================================
    // HTML
    // =================================================

    if (kind === "html") {

        return (
            <span
                className="vs-file-icon vs-file-icon-html"
                title="HTML"
            >
                &lt;/&gt;
            </span>
        );
    }


    // =================================================
    // CSS
    // =================================================

    if (kind === "css") {

        return (
            <span
                className="vs-file-icon vs-file-icon-css"
                title="CSS"
            >
                #
            </span>
        );
    }


    // =================================================
    // JSON
    // =================================================

    if (kind === "json") {

        return (
            <span
                className="vs-file-icon vs-file-icon-json"
                title="JSON"
            >
                {"{}"}
            </span>
        );
    }


    // =================================================
    // PACKAGE
    // =================================================

    if (kind === "package") {

        return (
            <span
                className="vs-file-icon vs-file-icon-package"
                title="Package"
            >
                <Package
                    size={14}
                    strokeWidth={2}
                />
            </span>
        );
    }


    // =================================================
    // MARKDOWN
    // =================================================

    if (kind === "markdown") {

        return (
            <span
                className="vs-file-icon vs-file-icon-markdown"
                title="Markdown"
            >
                M↓
            </span>
        );
    }


    // =================================================
    // XML / SVG
    // =================================================

    if (kind === "xml") {

        return (
            <span
                className="vs-file-icon vs-file-icon-xml"
                title="XML"
            >
                &lt;&gt;
            </span>
        );
    }


    // =================================================
    // YAML
    // =================================================

    if (kind === "yaml") {

        return (
            <span
                className="vs-file-icon vs-file-icon-yaml"
                title="YAML"
            >
                YML
            </span>
        );
    }


    // =================================================
    // DOCKER
    // =================================================

    if (kind === "docker") {

        return (
            <span
                className="vs-file-icon vs-file-icon-docker"
                title="Docker"
            >
                ◈
            </span>
        );
    }


    // =================================================
    // GIT
    // =================================================

    if (kind === "git") {

        return (
            <span
                className="vs-file-icon vs-file-icon-git"
                title="Git"
            >
                ◆
            </span>
        );
    }


    // =================================================
    // SHELL
    // =================================================

    if (kind === "shell") {

        return (
            <span
                className="vs-file-icon vs-file-icon-shell"
                title="Shell"
            >
                &gt;_
            </span>
        );
    }


    // =================================================
    // IMAGE
    // =================================================

    if (kind === "image") {

        return (
            <span
                className="vs-file-icon vs-file-icon-image"
                title="Image"
            >
                ▧
            </span>
        );
    }


    // =================================================
    // TEXT / UNKNOWN
    // =================================================

    return (
        <span
            className="vs-file-icon vs-file-icon-default"
            title="File"
        >
            <File
                size={14}
                strokeWidth={1.8}
            />
        </span>
    );
}


// =====================================================
// COMPONENT
// =====================================================

export default function FileExplorer({
    files,
    folders,
    selectedFile,
    workspaceName,
    onSelectFile,
    onOpenFolder,
    onCreateFile,
    onCreateFolder,
    onDeleteFile,
    onDeleteFolder,
    onRenameFile,
    onRenameFolder,
    readOnly = false,
}: FileExplorerProps) {

    const [
        expanded,
        setExpanded,
    ] = useState<Set<string>>(
        new Set([""])
    );


    // =================================================
    // BUILD TREE
    // =================================================

    const tree =
        useMemo<TreeNode[]>(
            () => {

                const root: TreeNode = {
                    name:
                        workspaceName ??
                        "WORKSPACE",

                    path:
                        "",

                    type:
                        "folder",

                    children:
                        [],
                };


                // =====================================
                // FOLDERS
                // =====================================

                for (
                    const folderPath
                    of folders
                ) {

                    const parts =
                        folderPath
                            .replaceAll(
                                "\\",
                                "/"
                            )
                            .split("/")
                            .filter(Boolean);

                    let current =
                        root;


                    for (
                        let i = 0;
                        i < parts.length;
                        i++
                    ) {

                        const part =
                            parts[i];

                        const path =
                            parts
                                .slice(
                                    0,
                                    i + 1
                                )
                                .join("/");


                        let child =
                            current.children.find(
                                node =>
                                    node.type ===
                                        "folder" &&
                                    node.path ===
                                        path
                            );


                        if (!child) {

                            child = {
                                name:
                                    part,

                                path,

                                type:
                                    "folder",

                                children:
                                    [],
                            };

                            current.children.push(
                                child
                            );
                        }


                        current =
                            child;
                    }
                }


                // =====================================
                // FILES
                // =====================================

                for (
                    const file
                    of files
                ) {

                    const parts =
                        file.path
                            .replaceAll(
                                "\\",
                                "/"
                            )
                            .split("/")
                            .filter(Boolean);

                    if (
                        parts.length === 0
                    ) {
                        continue;
                    }


                    const fileName =
                        parts.pop()!;

                    let current =
                        root;


                    for (
                        let i = 0;
                        i < parts.length;
                        i++
                    ) {

                        const part =
                            parts[i];

                        const path =
                            parts
                                .slice(
                                    0,
                                    i + 1
                                )
                                .join("/");


                        let child =
                            current.children.find(
                                node =>
                                    node.type ===
                                        "folder" &&
                                    node.path ===
                                        path
                            );


                        if (!child) {

                            child = {
                                name:
                                    part,

                                path,

                                type:
                                    "folder",

                                children:
                                    [],
                            };

                            current.children.push(
                                child
                            );
                        }


                        current =
                            child;
                    }


                    current.children.push({
                        name:
                            fileName,

                        path:
                            file.path,

                        type:
                            "file",

                        children:
                            [],

                        file,
                    });
                }


                // =====================================
                // SORT
                // =====================================

                const sortNodes = (
                    nodes: TreeNode[]
                ) => {

                    nodes.sort(
                        (
                            a,
                            b
                        ) => {

                            if (
                                a.type !==
                                b.type
                            ) {
                                return a.type ===
                                    "folder"
                                    ? -1
                                    : 1;
                            }

                            return a.name.localeCompare(
                                b.name,
                                undefined,
                                {
                                    numeric:
                                        true,
                                    sensitivity:
                                        "base",
                                }
                            );
                        }
                    );


                    for (
                        const node
                        of nodes
                    ) {
                        sortNodes(
                            node.children
                        );
                    }
                };


                sortNodes(
                    root.children
                );


                return [
                    root,
                ];

            },
            [
                files,
                folders,
                workspaceName,
            ]
        );


    // =================================================
    // TOGGLE FOLDER
    // =================================================

    const toggleFolder = (
        path: string
    ) => {

        setExpanded(
            current => {

                const next =
                    new Set(
                        current
                    );

                if (
                    next.has(path)
                ) {
                    next.delete(path);
                } else {
                    next.add(path);
                }

                return next;
            }
        );
    };


    // =================================================
    // EXPAND ALL
    // =================================================

    const expandAll = () => {

        setExpanded(
            new Set([
                "",
                ...folders,
            ])
        );
    };


    // =================================================
    // COLLAPSE ALL
    // =================================================

    const collapseAll = () => {

        setExpanded(
            new Set([
                "",
            ])
        );
    };


    // =================================================
    // RENDER NODE
    // =================================================

    const renderNode = (
        node: TreeNode,
        depth: number
    ): React.ReactNode => {

        const padding =
            7 +
            depth * 17;


        // =============================================
        // FILE
        // =============================================

        if (
            node.type ===
            "file"
        ) {

            const selected =
                node.path ===
                selectedFile;


            return (
                <div
                    key={
                        `file-${node.path}`
                    }
                    className={
                        selected
                            ? "explorer-tree-item file selected"
                            : "explorer-tree-item file"
                    }
                    style={{
                        paddingLeft:
                            `${padding}px`,
                    }}
                    onClick={() => {

                        if (
                            node.file
                        ) {
                            onSelectFile(
                                node.file
                            );
                        }
                    }}
                    title={
                        node.path
                    }
                >

                    <span
                        className="tree-spacer"
                    />


                    <span
                        className="tree-file-icon"
                    >
                        <FileIcon
                            name={
                                node.name
                            }
                        />
                    </span>


                    <span
                        className="tree-name"
                    >
                        {
                            node.name
                        }
                    </span>


                    {!readOnly && (
                        <div className="tree-node-actions">
                            <button
                                type="button"
                                className="tree-rename-button"
                                onClick={
                                    event => {
                                        event.stopPropagation();

                                        if (node.file) {
                                            onRenameFile(
                                                node.file
                                            );
                                        }
                                    }
                                }
                                title="Rename file"
                                aria-label={
                                    `Rename ${node.name}`
                                }
                            >
                                <Pencil size={12} />
                            </button>

                            <button
                                type="button"
                                className="tree-delete-button"
                                onClick={
                                    event => {

                                        event.stopPropagation();

                                        if (node.file) {
                                            onDeleteFile(
                                                node.file
                                            );
                                        }
                                    }
                                }
                                title="Delete file"
                                aria-label={
                                    `Delete ${node.name}`
                                }
                            >
                                <Trash2 size={13} />
                            </button>
                        </div>
                    )}

                </div>
            );
        }


        // =============================================
        // FOLDER
        // =============================================

        const isExpanded =
            expanded.has(
                node.path
            );


        const hasChildren =
            node.children.length >
            0;


        return (
            <div
                key={
                    `folder-${node.path}`
                }
            >

                <div
                    className="explorer-tree-item folder"
                    style={{
                        paddingLeft:
                            `${padding}px`,
                    }}
                    title={
                        node.path ||
                        workspaceName ||
                        "Workspace"
                    }
                >

                    <button
                        type="button"
                        className="tree-expand-button"
                        onClick={() =>
                            toggleFolder(
                                node.path
                            )
                        }
                        aria-label={
                            isExpanded
                                ? "Collapse folder"
                                : "Expand folder"
                        }
                    >

                        {
                            isExpanded
                                ? (
                                    <ChevronDown
                                        size={14}
                                    />
                                )
                                : (
                                    <ChevronRight
                                        size={14}
                                    />
                                )
                        }

                    </button>


                    <span
                        className="tree-folder-icon"
                    >

                        {
                            isExpanded
                                ? (
                                    <FolderOpen
                                        size={15}
                                        strokeWidth={1.8}
                                    />
                                )
                                : (
                                    <Folder
                                        size={15}
                                        strokeWidth={1.8}
                                    />
                                )
                        }

                    </span>


                    <span
                        className="tree-name"
                        onClick={() =>
                            toggleFolder(
                                node.path
                            )
                        }
                    >
                        {
                            node.name
                        }
                    </span>


                    {hasChildren && (
                        <span
                            className="tree-count"
                        >
                            {
                                node.children.length
                            }
                        </span>
                    )}


                    {!readOnly &&
                        node.path !== "" && (
                            <div className="tree-node-actions">
                                <button
                                    type="button"
                                    className="tree-rename-button"
                                    onClick={
                                        event => {

                                            event.stopPropagation();

                                            onRenameFolder(
                                                node.path
                                            );
                                        }
                                    }
                                    title="Rename folder"
                                    aria-label={
                                        `Rename ${node.name}`
                                    }
                                >
                                    <Pencil size={12} />
                                </button>

                                <button
                                    type="button"
                                    className="tree-delete-button"
                                    onClick={
                                        event => {

                                            event.stopPropagation();

                                            onDeleteFolder(
                                                node.path
                                            );
                                        }
                                    }
                                    title="Delete folder"
                                    aria-label={
                                        `Delete ${node.name}`
                                    }
                                >
                                    <Trash2 size={13} />
                                </button>
                            </div>
                        )}

                </div>


                {isExpanded && (
                    <div>
                        {
                            node.children.map(
                                child =>
                                    renderNode(
                                        child,
                                        depth + 1
                                    )
                            )
                        }
                    </div>
                )}

            </div>
        );
    };


    // =================================================
    // EMPTY STATE
    // =================================================

    const isEmpty =
        files.length === 0 &&
        folders.length === 0;


    // =================================================
    // UI
    // =================================================

    return (
        <aside className="file-explorer">

            {/* =========================================
                HEADER
            ========================================= */}

            <div className="explorer-header">

                <div
                    className="explorer-heading"
                >

                    <div
                        className="explorer-title"
                    >
                        EXPLORER
                    </div>

                    <div
                        className="explorer-subtitle"
                    >
                        {
                            workspaceName ??
                            "WORKSPACE"
                        }
                    </div>

                </div>


                <div
                    className="explorer-actions"
                >

                    {!readOnly && (
                        <button
                            type="button"
                            className="icon-button"
                            onClick={
                                onCreateFile
                            }
                            title="New file"
                            aria-label="New file"
                        >
                            <Plus
                                size={15}
                            />
                        </button>
                    )}


                    {!readOnly && (
                        <button
                            type="button"
                            className="icon-button"
                            onClick={
                                onCreateFolder
                            }
                            title="New folder"
                            aria-label="New folder"
                        >
                            <Folder
                                size={15}
                            />
                        </button>
                    )}


                    <button
                        type="button"
                        className="icon-button"
                        onClick={
                            onOpenFolder
                        }
                        disabled={
                            readOnly
                        }
                        title="Open local folder"
                        aria-label="Open local folder"
                    >
                        <Package
                            size={15}
                        />
                    </button>


                    <button
                        type="button"
                        className="icon-button"
                        onClick={
                            expandAll
                        }
                        title="Expand all"
                        aria-label="Expand all"
                    >
                        <ChevronDown
                            size={15}
                        />
                    </button>


                    <button
                        type="button"
                        className="icon-button"
                        onClick={
                            collapseAll
                        }
                        title="Collapse all"
                        aria-label="Collapse all"
                    >
                        <ChevronRight
                            size={15}
                        />
                    </button>


                    <button
                        type="button"
                        className="icon-button"
                        onClick={
                            expandAll
                        }
                        title="Refresh explorer"
                        aria-label="Refresh explorer"
                    >
                        <RefreshCw
                            size={14}
                        />
                    </button>

                </div>

            </div>


            {/* =========================================
                TREE
            ========================================= */}

            <div className="file-tree">

                {isEmpty ? (

                    <div
                        className="explorer-empty"
                    >

                        <div
                            className="explorer-empty-icon"
                        >
                            <Folder
                                size={30}
                                strokeWidth={1.4}
                            />
                        </div>


                        <p>
                            No files yet
                        </p>


                        <span>
                            Open a folder or create
                            your first file.
                        </span>


                        {!readOnly && (
                            <button
                                type="button"
                                className="explorer-empty-button"
                                onClick={
                                    onOpenFolder
                                }
                            >
                                Open Folder
                            </button>
                        )}

                    </div>

                ) : (

                    tree.map(
                        node =>
                            renderNode(
                                node,
                                0
                            )
                    )

                )}

            </div>


            {/* =========================================
                FOOTER
            ========================================= */}

            <div
                className="explorer-footer"
            >

                <span>
                    {files.length}
                    {" "}
                    {
                        files.length === 1
                            ? "file"
                            : "files"
                    }
                </span>

                <MoreHorizontal
                    size={14}
                />

            </div>


            {/* =========================================
                VS CODE STYLE FILE ICONS
            ========================================= */}

            <style>
                {`

                    .vs-file-icon {
                        width: 17px;
                        height: 17px;
                        min-width: 17px;

                        display: inline-flex;

                        align-items: center;
                        justify-content: center;

                        font-family:
                            Inter,
                            system-ui,
                            sans-serif;

                        font-size: 8px;
                        font-weight: 800;

                        line-height: 1;

                        letter-spacing: -0.4px;

                        user-select: none;
                    }


                    /* -----------------------------
                       TYPESCRIPT
                    ----------------------------- */

                    .vs-file-icon-typescript {
                        color: #3178c6;
                    }


                    /* -----------------------------
                       JAVASCRIPT
                    ----------------------------- */

                    .vs-file-icon-javascript {
                        color: #f7df1e;
                        font-size: 7px;
                    }


                    /* -----------------------------
                       REACT
                    ----------------------------- */

                    .vs-file-icon-react {
                        color: #61dafb;
                        font-size: 16px;
                        font-weight: 400;
                    }


                    /* -----------------------------
                       JAVA
                    ----------------------------- */

                    .vs-file-icon-java {
                        color: #f89820;
                        font-size: 12px;
                        filter:
                            saturate(0.85);
                    }


                    /* -----------------------------
                       PYTHON
                    ----------------------------- */

                    .vs-file-icon-python {
                        position: relative;
                        color: #4b8bbe;
                        font-size: 12px;
                    }

                    .python-mark {
                        position: relative;

                        width: 11px;
                        height: 8px;

                        border-radius:
                            4px 4px 4px 2px;

                        border:
                            1.5px solid #3776ab;
                    }

                    .python-mark::after {
                        content: "";

                        position: absolute;

                        width: 4px;
                        height: 4px;

                        right: -2px;
                        bottom: -2px;

                        border-radius: 50%;

                        background:
                            #ffd343;
                    }


                    /* -----------------------------
                       HTML
                    ----------------------------- */

                    .vs-file-icon-html {
                        color: #e44d26;
                        font-size: 8px;
                        letter-spacing: -1px;
                    }


                    /* -----------------------------
                       CSS
                    ----------------------------- */

                    .vs-file-icon-css {
                        color: #1572b6;
                        font-size: 14px;
                        font-weight: 700;
                    }


                    /* -----------------------------
                       JSON
                    ----------------------------- */

                    .vs-file-icon-json {
                        color: #cbcb41;
                        font-size: 8px;
                        letter-spacing: -1px;
                    }


                    /* -----------------------------
                       PACKAGE
                    ----------------------------- */

                    .vs-file-icon-package {
                        color: #cb3837;
                    }


                    /* -----------------------------
                       MARKDOWN
                    ----------------------------- */

                    .vs-file-icon-markdown {
                        color: #9da5b4;
                        font-size: 7px;
                        letter-spacing: -0.5px;
                    }


                    /* -----------------------------
                       XML
                    ----------------------------- */

                    .vs-file-icon-xml {
                        color: #e37933;
                        font-size: 9px;
                    }


                    /* -----------------------------
                       YAML
                    ----------------------------- */

                    .vs-file-icon-yaml {
                        color: #cb171e;
                        font-size: 6px;
                        letter-spacing: -0.5px;
                    }


                    /* -----------------------------
                       DOCKER
                    ----------------------------- */

                    .vs-file-icon-docker {
                        color: #2496ed;
                        font-size: 16px;
                    }


                    /* -----------------------------
                       GIT
                    ----------------------------- */

                    .vs-file-icon-git {
                        color: #f05032;
                        font-size: 13px;
                    }


                    /* -----------------------------
                       SHELL
                    ----------------------------- */

                    .vs-file-icon-shell {
                        color: #89e051;
                        font-size: 7px;
                        letter-spacing: -1px;
                    }


                    /* -----------------------------
                       IMAGE
                    ----------------------------- */

                    .vs-file-icon-image {
                        color: #a78bfa;
                        font-size: 15px;
                    }


                    /* -----------------------------
                       DEFAULT
                    ----------------------------- */

                    .vs-file-icon-default {
                        color: #8b98aa;
                    }


                    /* -----------------------------
                       FILE HOVER
                    ----------------------------- */

                    .explorer-tree-item.file:hover
                    .vs-file-icon {
                        filter:
                            brightness(1.12);
                    }


                    .tree-node-actions {
                        margin-left: auto;
                        display: flex;
                        align-items: center;
                        gap: 2px;
                        opacity: 0;
                        transition: opacity 120ms ease;
                    }

                    .explorer-tree-item:hover .tree-node-actions,
                    .explorer-tree-item:focus-within .tree-node-actions {
                        opacity: 1;
                    }

                    .tree-rename-button,
                    .tree-delete-button {
                        width: 22px;
                        height: 22px;
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        border: 0;
                        background: transparent;
                        color: #718096;
                        cursor: pointer;
                        padding: 0;
                    }

                    .tree-rename-button:hover {
                        color: #60a5fa;
                    }

                    .tree-delete-button:hover {
                        color: #f87171;
                    }

                    /* -----------------------------
                       SELECTED FILE
                    ----------------------------- */

                    .explorer-tree-item.file.selected
                    .vs-file-icon {
                        filter:
                            brightness(1.15);
                    }

                `}
            </style>

        </aside>
    );
}