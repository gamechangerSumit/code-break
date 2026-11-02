package com.codebreak.backend.websocket;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodeChangeApplierTests {

    @Test
    void appliesMultipleEditsFromOneOriginalDocumentVersion() {
        List<CollaborationMessage.CodeChange> changes = List.of(
                new CollaborationMessage.CodeChange(1, 2, "XY"),
                new CollaborationMessage.CodeChange(5, 1, "!")
        );

        assertEquals(
                "aXYde!g",
                CodeChangeApplier.apply("abcdefg", changes)
        );
    }

    @Test
    void treatsNullCurrentContentAsEmptyDocument() {
        assertEquals(
                "hello",
                CodeChangeApplier.apply(
                        null,
                        List.of(new CollaborationMessage.CodeChange(0, 0, "hello"))
                )
        );
    }

    @Test
    void rejectsNullOrIncompleteOperations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CodeChangeApplier.apply("abc", java.util.Arrays.asList((CollaborationMessage.CodeChange) null))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> CodeChangeApplier.apply(
                        "abc",
                        List.of(new CollaborationMessage.CodeChange(null, 0, "x"))
                )
        );
    }

    @Test
    void rejectsOutOfBoundsRanges() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CodeChangeApplier.apply(
                        "abc",
                        List.of(new CollaborationMessage.CodeChange(2, 2, "x"))
                )
        );
    }

    @Test
    void rejectsOverlappingAndAmbiguousSameOffsetEdits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CodeChangeApplier.apply(
                        "abcdef",
                        List.of(
                                new CollaborationMessage.CodeChange(1, 3, "x"),
                                new CollaborationMessage.CodeChange(2, 1, "y")
                        )
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> CodeChangeApplier.apply(
                        "abc",
                        List.of(
                                new CollaborationMessage.CodeChange(1, 0, "x"),
                                new CollaborationMessage.CodeChange(1, 1, "y")
                        )
                )
        );
    }

    @Test
    void rejectsOversizedBatches() {
        List<CollaborationMessage.CodeChange> changes =
                java.util.Collections.nCopies(
                        1001,
                        new CollaborationMessage.CodeChange(0, 0, "x")
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> CodeChangeApplier.apply("", changes)
        );
    }
}
