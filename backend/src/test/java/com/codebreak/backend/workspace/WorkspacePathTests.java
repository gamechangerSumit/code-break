package com.codebreak.backend.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspacePathTests {

    @Test
    void normalizesSeparatorsAndOuterSlashes() {
        assertEquals("src/main/App.java", WorkspacePath.normalize("/src\\\\main\\\\App.java/"));
    }

    @Test
    void acceptsNullAndBlankPathsForCallersToHandle() {
        assertEquals("", WorkspacePath.normalize(null));
        assertEquals("", WorkspacePath.normalize("  "));
    }

    @Test
    void rejectsTraversalAndDotSegments() {
        assertThrows(IllegalArgumentException.class, () -> WorkspacePath.normalize("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> WorkspacePath.normalize("src/../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> WorkspacePath.normalize("./secret.txt"));
    }

    @Test
    void rejectsEmptyInteriorSegmentsAndControlCharacters() {
        assertThrows(IllegalArgumentException.class, () -> WorkspacePath.normalize("src//Main.java"));
        assertThrows(IllegalArgumentException.class, () -> WorkspacePath.normalize("src/\u0000secret"));
    }

    @Test
    void rejectsOversizedPathsAndSegments() {
        assertThrows(IllegalArgumentException.class, () -> WorkspacePath.normalize("a".repeat(1025)));
        assertThrows(IllegalArgumentException.class, () -> WorkspacePath.normalize("a".repeat(256)));
    }

    @Test
    void doesNotRejectOrdinaryNamesContainingTwoDots() {
        assertEquals("release..notes.txt", WorkspacePath.normalize("release..notes.txt"));
    }
}
