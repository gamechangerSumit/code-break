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


interface CodeEditorProps {

    value: string;

    onChange: (
        value: string,
        changes: CodeEditorChange[]
    ) => void;

    readOnly?: boolean;
}


export default function CodeEditor({
    value,
    onChange,
    readOnly = false,
}: CodeEditorProps) {

    const editorRef =
        useRef<editor.IStandaloneCodeEditor | null>(
            null
        );


    const changeSubscriptionRef =
        useRef<IDisposable | null>(
            null
        );


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


        editorInstance.focus();
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