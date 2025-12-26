package tests;

import codes.Digraph;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DigraphTest {

    @Test
    void constructor_negativeV_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Digraph(-1));
    }

    @Test
    void addEdge_invalidVertex_throws() {
        Digraph g = new Digraph(3);
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, -1));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(3, 0));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 3));
    }

    @Test
    void addEdge_updatesDegrees_andHasEdge() {
        Digraph g = new Digraph(4);
        g.addEdge(1, 2);

        assertEquals(1, g.E());
        assertEquals(1, g.outdegree(1));
        assertEquals(1, g.indegree(2));
        assertTrue(g.hasEdge(1, 2));
        assertFalse(g.hasEdge(2, 1));
    }

    @Test
    void parallelEdges_allowed() {
        Digraph g = new Digraph(2);
        g.addEdge(0, 1);
        g.addEdge(0, 1);

        assertEquals(2, g.E());
        assertEquals(2, g.outdegree(0));
        assertEquals(2, g.indegree(1));
        assertTrue(g.hasEdge(0, 1));
    }

    @Test
    void selfLoop_allowed() {
        Digraph g = new Digraph(2);
        g.addEdge(1, 1);

        assertEquals(1, g.E());
        assertEquals(1, g.outdegree(1));
        assertEquals(1, g.indegree(1));
        assertTrue(g.hasEdge(1, 1));
    }

    @Test
    void reverse_reversesEdgesAndPreservesCounts() {
        Digraph g = new Digraph(4);
        g.addEdge(0, 1);
        g.addEdge(0, 2);
        g.addEdge(2, 3);

        Digraph r = g.reverse();
        assertEquals(g.V(), r.V());
        assertEquals(g.E(), r.E());

        assertTrue(r.hasEdge(1, 0));
        assertTrue(r.hasEdge(2, 0));
        assertTrue(r.hasEdge(3, 2));

        // degree swap sanity
        assertEquals(g.outdegree(0), r.indegree(0));
        assertEquals(g.indegree(0), r.outdegree(0));
    }
}