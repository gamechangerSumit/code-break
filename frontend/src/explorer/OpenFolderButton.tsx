import {
    useState,
} from "react";

import {
    openLocalFolder,
} from "../workspace/localWorkspace";

interface OpenFolderButtonProps {
    mode?:
        | "OWNER"
        | "COLLABORATOR";

    disabled?: boolean;

    onOpened?: (
        name: string
    ) => void;
}

export default function OpenFolderButton({
    mode = "OWNER",
    disabled = false,
    onOpened,
}: OpenFolderButtonProps) {

    const [
        opening,
        setOpening,
    ] = useState(false);

    const handleOpenFolder =
        async () => {

            if (
                disabled ||
                opening
            ) {
                return;
            }

            try {

                setOpening(true);

                const name =
                    await openLocalFolder(
                        mode
                    );

                onOpened?.(
                    name
                );

            } catch (error) {

                if (
                    error instanceof DOMException &&
                    error.name ===
                    "AbortError"
                ) {
                    return;
                }

                console.error(
                    "[OPEN FOLDER]",
                    error
                );

            } finally {

                setOpening(false);

            }
        };

    return (
        <button
            type="button"
            className="explorer-action-button open-folder-button"
            onClick={
                handleOpenFolder
            }
            disabled={
                disabled ||
                opening
            }
            title="Open local folder"
        >
            {
                opening
                    ? "⏳"
                    : "📂"
            }
        </button>
    );
}