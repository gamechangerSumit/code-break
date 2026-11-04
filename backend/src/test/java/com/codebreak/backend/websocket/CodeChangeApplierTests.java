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
    void appliesAdjacentEditsAndInsertionAtEndOfDocument() {
        List<CollaborationMessage.CodeChange> changes = List.of(
                new CollaborationMessage.CodeChange(0, 1, "A"),
                new CollaborationMessage.CodeChange(1, 1, "B"),
                new CollaborationMessage.CodeChange(2, 0, "!")
        );

        assertEquals("AB!", CodeChangeApplier.apply("ab", changes));
    }

    @Test
    void usesUtf16OffsetsForSupplementaryUnicodeCharacters() {
        assertEquals(
                "aXb",
                CodeChangeApplier.apply(
                        "a😀b",
                        List.of(new CollaborationMessage.CodeChange(1, 2, "X"))
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
    void rejectsDocumentsAndInsertedTextOverTwoMiB() {
        String oversized = "x".repeat(CodeChangeApplier.MAX_DOCUMENT_BYTES + 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> CodeChangeApplier.apply(oversized, List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> CodeChangeApplier.apply(
                        "",
                        List.of(new CollaborationMessage.CodeChange(0, 0, oversized))
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> CodeChangeApplier.apply(
                        "x",
                        List.of(new CollaborationMessage.CodeChange(0, 1, oversized))
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
