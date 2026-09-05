import Editor, {
    type OnMount,
} from "@monaco-editor/react";

import {
    useEffect,
    useRef,
} from "react";

interface CodeEditorProps {
    value: string;
    onChange: (value: string) => void;
    readOnly?: boolean;
}

export default function CodeEditor({
    value,
    onChange,
    readOnly = false,
}: CodeEditorProps) {

    const editorRef = useRef<
        Parameters<OnMount>[0] | null
    >(null);

    const lastValueRef = useRef<string>("");

    const safeValue =
        typeof value === "string"
            ? value
            : "";

    /**
     * Monaco editor mount
     */
    const handleEditorMount: OnMount = (
        editor
    ) => {
        editorRef.current = editor;

        lastValueRef.current = safeValue;

        /*
         * Important:
         * Parent se jo value aa rahi hai,
         * wahi Monaco ke model me set hoti hai.
         */
        if (
            editor.getValue() !== safeValue
        ) {
            editor.setValue(safeValue);
        }

        editor.focus();
    };

    /**
     * Parent -> Monaco synchronization
     *
     * Jab selected file change hoti hai
     * ya workspace se content load hota hai,
     * Monaco ko new value milni chahiye.
     */
    useEffect(() => {
        const editor = editorRef.current;

        if (!editor) {
            return;
        }

        const currentValue =
            editor.getValue();

        if (
            currentValue !== safeValue &&
            lastValueRef.current !== safeValue
        ) {
            const position =
                editor.getPosition();

            editor.setValue(safeValue);

            if (position) {
                editor.setPosition(position);
            }
        }

        lastValueRef.current = safeValue;
    }, [safeValue]);

    /**
     * Monaco -> Parent synchronization
     */
    const handleChange = (
        nextValue: string | undefined
    ) => {
        if (readOnly) {
            return;
        }

        const content =
            nextValue ?? "";

        lastValueRef.current =
            content;

        onChange(content);
    };

    return (
        <div className="code-editor-shell">

            <Editor
                height="100%"
                width="100%"
                language="java"
                theme="vs-dark"

                value={safeValue}

                onMount={
                    handleEditorMount
                }

                onChange={
                    handleChange
                }

                loading={
                    <div className="editor-loading">
                        <div className="editor-loading-spinner" />

                        <span>
                            Loading editor...
                        </span>
                    </div>
                }

                options={{
                    readOnly,

                    automaticLayout:
                        true,

                    /**
                     * Editor appearance
                     */
                    fontSize: 14,

                    lineHeight: 22,

                    fontFamily:
                        "JetBrains Mono, Consolas, Monaco, monospace",

                    fontLigatures:
                        true,

                    fontWeight:
                        "400",

                    /**
                     * Code behaviour
                     */
                    tabSize: 4,

                    insertSpaces:
                        true,

                    detectIndentation:
                        true,

                    wordWrap:
                        "off",

                    scrollBeyondLastLine:
                        false,

                    smoothScrolling:
                        true,

                    mouseWheelZoom:
                        true,

                    /**
                     * Cursor
                     */
                    cursorBlinking:
                        "smooth",

                    cursorSmoothCaretAnimation:
                        "on",

                    renderLineHighlight:
                        "all",

                    /**
                     * Minimap
                     */
                    minimap: {
                        enabled: true,
                        side: "right",
                        showSlider:
                            "mouseover",
                        renderCharacters:
                            true,
                    },

                    /**
                     * Folding
                     */
                    folding:
                        true,

                    foldingHighlight:
                        true,

                    showFoldingControls:
                        "mouseover",

                    /**
                     * Brackets / guides
                     */
                    bracketPairColorization: {
                        enabled:
                            true,
                    },

                    guides: {
                        indentation:
                            true,
                        bracketPairs:
                            true,
                        highlightActiveIndentation:
                            true,
                    },

                    /**
                     * Whitespace
                     */
                    renderWhitespace:
                        "selection",

                    renderControlCharacters:
                        false,

                    /**
                     * Editor spacing
                     */
                    padding: {
                        top: 14,
                        bottom: 20,
                    },

                    /**
                     * Scrollbars
                     */
                    scrollbar: {
                        verticalScrollbarSize:
                            10,

                        horizontalScrollbarSize:
                            10,

                        useShadows:
                            false,

                        alwaysConsumeMouseWheel:
                            false,
                    },

                    /**
                     * Overview ruler
                     */
                    overviewRulerLanes:
                        3,

                    hideCursorInOverviewRuler:
                        false,

                    /**
                     * Context menu
                     */
                    contextmenu:
                        true,

                    /**
                     * Selection / suggestions
                     */
                    suggest: {
                        showMethods:
                            true,
                        showFunctions:
                            true,
                        showVariables:
                            true,
                        showClasses:
                            true,
                    },

                    quickSuggestions:
                        true,

                    parameterHints: {
                        enabled:
                            true,
                    },

                    /**
                     * Performance
                     */
                    largeFileOptimizations:
                        true,

                    stopRenderingLineAfter:
                        10000,

                    /**
                     * Accessibility
                     */
                    accessibilitySupport:
                        "auto",

                    ariaLabel:
                        readOnly
                            ? "Read only code editor"
                            : "Code editor",
                }}
            />

            {readOnly && (
                <div className="editor-readonly-badge">
                    <span className="editor-readonly-dot" />

                    <span>
                        Read only
                    </span>
                </div>
            )}

        </div>
    );
}