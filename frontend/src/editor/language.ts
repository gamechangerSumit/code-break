const LANGUAGE_BY_EXTENSION: Record<string, { id: string; label: string }> = {
    ".c": { id: "c", label: "C" },
    ".cpp": { id: "cpp", label: "C++" },
    ".cs": { id: "csharp", label: "C#" },
    ".css": { id: "css", label: "CSS" },
    ".dart": { id: "dart", label: "Dart" },
    ".go": { id: "go", label: "Go" },
    ".html": { id: "html", label: "HTML" },
    ".java": { id: "java", label: "Java" },
    ".js": { id: "javascript", label: "JavaScript" },
    ".jsx": { id: "javascript", label: "JavaScript" },
    ".json": { id: "json", label: "JSON" },
    ".kt": { id: "kotlin", label: "Kotlin" },
    ".kts": { id: "kotlin", label: "Kotlin" },
    ".md": { id: "markdown", label: "Markdown" },
    ".php": { id: "php", label: "PHP" },
    ".py": { id: "python", label: "Python" },
    ".rb": { id: "ruby", label: "Ruby" },
    ".rs": { id: "rust", label: "Rust" },
    ".scss": { id: "scss", label: "SCSS" },
    ".sh": { id: "shell", label: "Shell" },
    ".sql": { id: "sql", label: "SQL" },
    ".swift": { id: "swift", label: "Swift" },
    ".ts": { id: "typescript", label: "TypeScript" },
    ".tsx": { id: "typescript", label: "TypeScript" },
    ".xml": { id: "xml", label: "XML" },
    ".yaml": { id: "yaml", label: "YAML" },
    ".yml": { id: "yaml", label: "YAML" },
};

function languageForPath(path: string) {
    const normalized = path.replaceAll("\\", "/").toLowerCase();
    const fileName = normalized.slice(normalized.lastIndexOf("/") + 1);

    if (fileName === "dockerfile" || fileName.startsWith("dockerfile.")) {
        return { id: "dockerfile", label: "Dockerfile" };
    }

    if (fileName === "makefile") {
        return { id: "makefile", label: "Makefile" };
    }

    const extension = fileName.includes(".")
        ? fileName.slice(fileName.lastIndexOf("."))
        : "";

    return LANGUAGE_BY_EXTENSION[extension] ?? {
        id: "plaintext",
        label: "Plain Text",
    };
}

export function getMonacoLanguage(path: string): string {
    return languageForPath(path).id;
}

export function getLanguageLabel(path: string): string {
    return languageForPath(path).label;
}
