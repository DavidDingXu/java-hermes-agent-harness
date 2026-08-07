package com.ading.ai.hermes.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuntimeBoundaryMapTest {

    @Test
    void containsTheMinimalHermesRuntimeBoundariesInOrder() {
        RuntimeBoundaryMap map = RuntimeBoundaryMap.minimalHermesStyle();

        assertEquals(
                List.of(
                        BoundaryKind.ENTRY,
                        BoundaryKind.MODEL,
                        BoundaryKind.TOOL,
                        BoundaryKind.CONTEXT,
                        BoundaryKind.SESSION,
                        BoundaryKind.SAFETY,
                        BoundaryKind.OBSERVABILITY
                ),
                map.boundaries().stream().map(RuntimeBoundary::kind).toList()
        );
    }

    @Test
    void everyBoundaryNamesItsInputOutputAndResponsibility() {
        RuntimeBoundaryMap map = RuntimeBoundaryMap.minimalHermesStyle();

        assertEquals(7, map.boundaries().size());
        for (RuntimeBoundary boundary : map.boundaries()) {
            assertFalse(boundary.name().isBlank());
            assertFalse(boundary.input().isBlank());
            assertFalse(boundary.output().isBlank());
            assertFalse(boundary.responsibility().isBlank());
            assertFalse(boundary.hermesEvidence().isBlank());
        }
    }

    @Test
    void rejectsDuplicateBoundaryKinds() {
        RuntimeBoundary entry = new RuntimeBoundary(
                BoundaryKind.ENTRY,
                "Entry",
                "external event",
                "turn request",
                "normalizes input",
                "README gateway and CLI surfaces"
        );

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeBoundaryMap(List.of(entry, entry))
        );

        assertEquals("runtime boundary kind must be unique: ENTRY", error.getMessage());
    }
}
