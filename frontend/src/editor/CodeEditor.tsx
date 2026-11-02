import Editor, {
    type OnMount,
} from "@monaco-editor/react";

import {
    useEffect,
    useRef,
} from "react";

import type {
    editor,
    IDisposable,
} from "monaco-editor";


export interface CodeEditorChange {
    rangeOffset: number;
    rangeLength: number;
    text: string;
}

export interface RemoteCursor {
    username: string;
    lineNumber: number;
    column: number;
    selectionStartLine: number;
    selectionStartColumn: number;
    selectionEndLine: number;
    selectionEndColumn: number;
    color: string;
}


interface CodeEditorProps {

    value: string;

    onChange: (
        value: string,
        changes: CodeEditorChange[]
    ) => void;

    readOnly?: boolean;

    remoteCursors?: RemoteCursor[];

    onCursorChange?: (cursor: {
        lineNumber: number;
        column: number;
        selectionStartLine: number;
        selectionStartColumn: number;
        selectionEndLine: number;
        selectionEndColumn: number;
    }) => void;
}


export default function CodeEditor({
    value,
    onChange,
    readOnly = false,
    remoteCursors = [],
    onCursorChange,
}: CodeEditorProps) {

    const editorRef =
        useRef<editor.IStandaloneCodeEditor | null>(
            null
        );


    const changeSubscriptionRef =
        useRef<IDisposable | null>(
            null
        );

    const cursorSubscriptionsRef = useRef<IDisposable[]>([]);
    const decorationsRef = useRef<string[]>([]);


    const suppressChangeRef =
        useRef(false);


    const latestValueRef =
        useRef<string>(
            typeof value === "string"
                ? value
                : ""
        );


    const safeValue =
        typeof value === "string"
            ? value
            : "";


    // =====================================================
    // LATEST CALLBACK
    // =====================================================

    const onChangeRef =
        useRef(onChange);


    useEffect(
        () => {

            onChangeRef.current =
                onChange;

        },
        [
            onChange,
        ]
    );


    // =====================================================
    // MOUNT
    // =====================================================

    const handleEditorMount: OnMount = (
        editorInstance
    ) => {

        editorRef.current =
            editorInstance;


        latestValueRef.current =
            safeValue;


        suppressChangeRef.current =
            true;


        if (
            editorInstance.getValue() !==
            safeValue
        ) {

            editorInstance.setValue(
                safeValue
            );
        }


        suppressChangeRef.current =
            false;


        // =============================================
        // MONACO DELTA EVENTS
        // =============================================

        changeSubscriptionRef.current =
            editorInstance.onDidChangeModelContent(
                event => {

                    if (
                        suppressChangeRef.current
                    ) {
                        return;
                    }


                    if (readOnly) {
                        return;
                    }


                    const nextValue =
                        editorInstance.getValue();


                    latestValueRef.current =
                        nextValue;


                    const changes: CodeEditorChange[] =
                        event.changes.map(
                            change => ({
                                rangeOffset:
                                    change.rangeOffset,

                                rangeLength:
                                    change.rangeLength,

                                text:
                                    change.text,
                            })
                        );


                    onChangeRef.current(
                        nextValue,
                        changes
                    );
                }
            );


        const publishCursor = () => {
            const position = editorInstance.getPosition();
            if (!position) {
                return;
            }
            const selection = editorInstance.getSelection();
            onCursorChangeRef.current?.({
                lineNumber: position.lineNumber,
                column: position.column,
                selectionStartLine: selection?.startLineNumber ?? position.lineNumber,
                selectionStartColumn: selection?.startColumn ?? position.column,
                selectionEndLine: selection?.endLineNumber ?? position.lineNumber,
                selectionEndColumn: selection?.endColumn ?? position.column,
            });
        };

        cursorSubscriptionsRef.current = [
            editorInstance.onDidChangeCursorPosition(publishCursor),
            editorInstance.onDidChangeCursorSelection(publishCursor),
        ];

        editorInstance.focus();
        publishCursor();
    };


    // =====================================================
    // PARENT -> MONACO
    // =====================================================

    useEffect(
        () => {

            const editorInstance =
                editorRef.current;


            if (!editorInstance) {
                return;
            }


            const currentValue =
                editorInstance.getValue();


            if (
                currentValue ===
                safeValue
            ) {

                latestValueRef.current =
                    safeValue;

                return;
            }


            /*
             * Parent value changed because of:
             *
             * - selecting another file
             * - loading workspace content
             * - receiving remote content
             * - role/workspace refresh
             *
             * This is NOT a user edit.
             *
             * Therefore suppress Monaco's change callback.
             */

            suppressChangeRef.current =
                true;


            const position =
                editorInstance.getPosition();


            const selection =
                editorInstance.getSelection();


            editorInstance.setValue(
                safeValue
            );


            if (position) {
                editorInstance.setPosition(
                    position
                );
            }


            if (selection) {
                editorInstance.setSelection(
                    selection
                );
            }


            latestValueRef.current =
                safeValue;


            suppressChangeRef.current =
                false;

        },
        [
            safeValue,
        ]
    );


    // =====================================================
    // REMOTE CURSOR DECORATIONS
    // =====================================================

    useEffect(() => {
        const editorInstance = editorRef.current;
        if (!editorInstance) {
            return;
        }

        const decorations = remoteCursors.map((cursor, index) => {
            const color = /^#[0-9a-fA-F]{6}$/.test(cursor.color)
                ? cursor.color
                : "#60a5fa";
            const hasSelection =
                cursor.selectionStartLine !== cursor.selectionEndLine ||
                cursor.selectionStartColumn !== cursor.selectionEndColumn;
            const startLine = Math.max(1, cursor.selectionStartLine || cursor.lineNumber);
            const startColumn = Math.max(1, cursor.selectionStartColumn || cursor.column);
            const endLine = Math.max(1, cursor.selectionEndLine || cursor.lineNumber);
            const endColumn = Math.max(1, cursor.selectionEndColumn || cursor.column);

            return {
                range: hasSelection
                    ? {
                        startLineNumber: startLine,
                        startColumn,
                        endLineNumber: endLine,
                        endColumn,
                    }
                    : {
                        startLineNumber: Math.max(1, cursor.lineNumber),
                        startColumn: Math.max(1, cursor.column),
                        endLineNumber: Math.max(1, cursor.lineNumber),
                        endColumn: Math.max(1, cursor.column),
                    },
                options: {
                    className: hasSelection
                        ? `remote-cursor-selection remote-cursor-selection-${index}`
                        : undefined,
                    before: hasSelection
                        ? undefined
                        : {
                            content: "\u200b",
                            inlineClassName: `remote-cursor-caret remote-cursor-caret-${index}`,
                        },
                    after: {
                        content: ` ${cursor.username}`,
                        inlineClassName: `remote-cursor-label remote-cursor-label-${index}`,
                    },
                    stickiness: 1,
                    hoverMessage: { value: cursor.username },
                },
                color,
            };
        });

        decorationsRef.current = editorInstance.deltaDecorations(
            decorationsRef.current,
            decorations.map(({ color, ...decoration }) => decoration)
        );

        return () => {
            if (editorRef.current) {
                decorationsRef.current = editorRef.current.deltaDecorations(
                    decorationsRef.current,
                    []
                );
            }
        };
    }, [remoteCursors]);

    // =====================================================
    // READ ONLY CHANGE
    // =====================================================

    useEffect(
        () => {

            const editorInstance =
                editorRef.current;


            if (!editorInstance) {
                return;
            }


            editorInstance.updateOptions({
                readOnly,
            });

        },
        [
            readOnly,
        ]
    );


    // =====================================================
    // CLEANUP
    // =====================================================

    useEffect(
        () => {

            return () => {

                changeSubscriptionRef.current?.dispose();
                cursorSubscriptionsRef.current.forEach(subscription => subscription.dispose());
                cursorSubscriptionsRef.current = [];

                changeSubscriptionRef.current =
                    null;

                editorRef.current =
                    null;
            };

        },
        []
    );


    return (
        <div className="code-editor-shell">

            <style>
                {remoteCursors.map((cursor, index) => {
                    const color = /^#[0-9a-fA-F]{6}$/.test(cursor.color)
                        ? cursor.color
                        : "#60a5fa";
                    return `
                        .remote-cursor-caret-${index} {
                            border-left: 2px solid ${color};
                            margin-left: -1px;
                        }
                        .remote-cursor-label-${index} {
                            color: #fff;
                            background: ${color};
                            border-radius: 3px;
                            padding: 1px 4px;
                            font-size: 10px;
                            font-weight: 600;
                        }
                        .remote-cursor-selection-${index} {
                            background: ${color}33;
                            outline: 1px solid ${color}88;
                        }
                    `;
                }).join("\\n")}
            </style>

            <Editor
                height="100%"
                width="100%"
                language="java"
                theme="vs-dark"

                defaultValue={
                    safeValue
                }

                onMount={
                    handleEditorMount
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

                    fontSize:
                        14,

                    lineHeight:
                        22,

                    fontFamily:
                        "JetBrains Mono, Consolas, Monaco, monospace",

                    fontLigatures:
                        true,

                    fontWeight:
                        "400",

                    tabSize:
                        4,

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

                    cursorBlinking:
                        "smooth",

                    cursorSmoothCaretAnimation:
                        "on",

                    renderLineHighlight:
                        "all",

                    minimap: {
                        enabled:
                            true,

                        side:
                            "right",

                        showSlider:
                            "mouseover",

                        renderCharacters:
                            true,
                    },

                    folding:
                        true,

                    foldingHighlight:
                        true,

                    showFoldingControls:
                        "mouseover",

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

                    renderWhitespace:
                        "selection",

                    renderControlCharacters:
                        false,

                    padding: {
                        top:
                            14,

                        bottom:
                            20,
                    },

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

                    overviewRulerLanes:
                        3,

                    hideCursorInOverviewRuler:
                        false,

                    contextmenu:
                        true,

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

                    largeFileOptimizations:
                        true,

                    stopRenderingLineAfter:
                        10000,

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