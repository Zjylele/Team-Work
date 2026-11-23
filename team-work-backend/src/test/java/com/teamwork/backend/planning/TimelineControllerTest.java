package com.teamwork.backend.planning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineControllerTest {
    @Test
    void rejectsReverseEdgeClosingTransitiveCycle() {
        var edges = List.of(edge(1, 2), edge(2, 3), edge(2, 4), edge(4, 3));

        assertTrue(TimelineController.createsCycle(edge(3, 1), edges));
        assertTrue(TimelineController.createsCycle(edge(2, 1), edges));
        assertFalse(TimelineController.createsCycle(edge(1, 3), edges));
        assertFalse(TimelineController.createsCycle(edge(3, 5), edges));
    }

    private static TaskDependencyMapper.Dependency edge(long predecessor, long successor) {
        return new TaskDependencyMapper.Dependency(predecessor, successor);
    }
}
