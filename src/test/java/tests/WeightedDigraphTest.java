package tests;

import codes.Edge;
import codes.WeightedDigraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class WeightedDigraphTest {

    @Test
    void constructor_negativeV_throws() {
        assertThrows(IllegalArgumentException.class, () -> new WeightedDigraph(-1));
    }

    @Test
    void addEdge_assignsSequentialIds_andEdgeByIdWorks() {
        WeightedDigraph g = new WeightedDigraph(3);

        int e0 = g.addEdge(0, 1, 0.0);
        int e1 = g.addEdge(1, 2, 0.0);
        int e2 = g.addEdge(0, 2, 0.0);

        assertEquals(0, e0);
        assertEquals(1, e1);
        assertEquals(2, e2);
        assertEquals(3, g.E());

        assertEquals(0, g.edgeByID(0).edgeID());
        assertEquals(1, g.edgeByID(1).edgeID());
        assertEquals(2, g.edgeByID(2).edgeID());
    }

    @Test
    void degrees_areTrackedCorrectly() {
        WeightedDigraph g = new WeightedDigraph(3);
        g.addEdge(0, 1, 0.0);
        g.addEdge(0, 2, 0.0);
        g.addEdge(2, 1, 0.0);

        assertEquals(2, g.outdegree(0));
        assertEquals(1, g.outdegree(2));
        assertEquals(0, g.outdegree(1));

        assertEquals(2, g.indegree(1)); // edges 0->1 and 2->1
        assertEquals(1, g.indegree(2)); // edge 0->2
        assertEquals(0, g.indegree(0));
    }

    @Test
    void addEdge_edgeObjectAssignsIdAndRegisters() {
        WeightedDigraph g = new WeightedDigraph(2);

        Edge e = new Edge(0, 1, 0.0);
        assertEquals(-1, e.edgeID());

        g.addEdge(e);

        assertEquals(1, g.E());
        assertEquals(0, e.edgeID());
        assertSame(e, g.edgeByID(0));
        assertEquals(1, g.outdegree(0));
        assertEquals(1, g.indegree(1));
    }

    @Test
    void addEdge_edgeObjectWithIdRejected() {
        WeightedDigraph g = new WeightedDigraph(2);
        Edge e = new Edge(0, 1, 0.0);
        e.setiD(5);
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(e));
    }

    @Test
    void edges_iteratesInIdOrder_andNoNulls() {
        WeightedDigraph g = new WeightedDigraph(3);
        g.addEdge(0, 1, 0.0);
        g.addEdge(1, 2, 0.0);
        g.addEdge(0, 2, 0.0);

        ArrayList<Integer> ids = new ArrayList<>();
        for (Edge e : g.edges()) {
            assertNotNull(e);
            ids.add(e.edgeID());
        }

        assertEquals(3, ids.size());
        assertEquals(0, ids.get(0));
        assertEquals(1, ids.get(1));
        assertEquals(2, ids.get(2));
    }

    @Test
    void reverse_reversesDirections() {
        WeightedDigraph g = new WeightedDigraph(4);
        g.addEdge(0, 1, 0.0);
        g.addEdge(0, 2, 0.0);
        g.addEdge(2, 3, 0.0);

        WeightedDigraph r = g.reverse();
        assertEquals(g.V(), r.V());
        assertEquals(g.E(), r.E());

        // Check existence by scanning adjacency of reversed graph
        assertTrue(hasDirectedEdge(r, 1, 0));
        assertTrue(hasDirectedEdge(r, 2, 0));
        assertTrue(hasDirectedEdge(r, 3, 2));
    }

    private static boolean hasDirectedEdge(WeightedDigraph g, int from, int to) {
        for (Edge e : g.outEdges(from)) {
            if (e.otherEnd() == to) return true;
        }
        return false;
    }
}