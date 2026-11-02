package com.codebreak.backend.websocket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Validates and applies one Monaco edit batch against a single document version.
 * Offsets are UTF-16 character offsets, matching Java String indexing and Monaco.
 */
public final class CodeChangeApplier {

    private static final int MAX_CHANGES_PER_MESSAGE = 1000;

    private CodeChangeApplier() {
    }

    public static String apply(
            String currentContent,
            List<CollaborationMessage.CodeChange> incomingChanges
    ) {
        String source = currentContent == null ? "" : currentContent;

        if (incomingChanges == null || incomingChanges.isEmpty()) {
            return source;
        }

        if (incomingChanges.size() > MAX_CHANGES_PER_MESSAGE) {
            throw new IllegalArgumentException("Too many edits in one message");
        }

        // Validate null entries before sorting (the comparator dereferences changes).
        for (CollaborationMessage.CodeChange change : incomingChanges) {
            if (change == null
                    || change.getRangeOffset() == null
                    || change.getRangeLength() == null
                    || change.getText() == null) {
                throw new IllegalArgumentException("Incomplete edit operation");
            }
        }

        List<CollaborationMessage.CodeChange> ascending =
                new ArrayList<>(incomingChanges);
        ascending.sort(Comparator.comparing(
                CollaborationMessage.CodeChange::getRangeOffset
        ));

        int previousEnd = -1;
        int previousOffset = -1;
        for (CollaborationMessage.CodeChange change : ascending) {
            int offset = change.getRangeOffset();
            int length = change.getRangeLength();
            long end = (long) offset + length;

            if (offset < 0 || length < 0 || end > source.length()) {
                throw new IllegalArgumentException("Edit range is outside document");
            }

            if (offset < previousEnd || offset == previousOffset) {
                throw new IllegalArgumentException("Overlapping edit operations");
            }

            previousOffset = offset;
            previousEnd = (int) end;
        }

        // Apply from right to left so original offsets remain valid.
        List<CollaborationMessage.CodeChange> descending =
                new ArrayList<>(incomingChanges);
        descending.sort(Comparator.comparing(
                CollaborationMessage.CodeChange::getRangeOffset,
                Comparator.reverseOrder()
        ));

        String result = source;
        for (CollaborationMessage.CodeChange change : descending) {
            int offset = change.getRangeOffset();
            int end = offset + change.getRangeLength();
            result = result.substring(0, offset)
                    + change.getText()
                    + result.substring(end);
        }

        return result;
    }
}
