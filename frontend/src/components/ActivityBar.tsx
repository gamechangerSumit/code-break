import {
    Code2,
    FolderTree,
    Settings2,
    Users,
} from "lucide-react";

type ActivityPanel =
    | "explorer"
    | "collaboration";

interface ActivityBarProps {
    activePanel: ActivityPanel;

    onPanelChange: (
        panel: ActivityPanel
    ) => void;
}

function ActivityBar({
    activePanel,
    onPanelChange,
}: ActivityBarProps) {

    return (
        <aside className="activity-bar">

            <div className="activity-bar-top">

                <button
                    type="button"
                    className={
                        activePanel === "explorer"
                            ? "activity-button activity-button-active"
                            : "activity-button"
                    }
                    onClick={() =>
                        onPanelChange(
                            "explorer"
                        )
                    }
                    title="Explorer"
                    aria-label="Explorer"
                    aria-pressed={
                        activePanel === "explorer"
                    }
                >

                    <FolderTree
                        size={19}
                        strokeWidth={1.8}
                    />

                    <span className="activity-tooltip">
                        Explorer
                    </span>

                </button>


                <button
                    type="button"
                    className={
                        activePanel === "collaboration"
                            ? "activity-button activity-button-active"
                            : "activity-button"
                    }
                    onClick={() =>
                        onPanelChange(
                            "collaboration"
                        )
                    }
                    title="Collaboration"
                    aria-label="Collaboration"
                    aria-pressed={
                        activePanel === "collaboration"
                    }
                >

                    <Users
                        size={19}
                        strokeWidth={1.8}
                    />

                    <span className="activity-tooltip">
                        Collaboration
                    </span>

                    <span className="activity-badge">
                        •
                    </span>

                </button>

            </div>


            <div className="activity-bar-bottom">

                <button
                    type="button"
                    className="activity-button"
                    title="Code Break"
                    aria-label="Code Break"
                >

                    <Code2
                        size={19}
                        strokeWidth={1.8}
                    />

                    <span className="activity-tooltip">
                        Code Break
                    </span>

                </button>


                <button
                    type="button"
                    className="activity-button"
                    title="Settings"
                    aria-label="Settings"
                >

                    <Settings2
                        size={19}
                        strokeWidth={1.8}
                    />

                    <span className="activity-tooltip">
                        Settings
                    </span>

                </button>

            </div>

        </aside>
    );
}

export default ActivityBar;