package tests;

import codes.EdgeAttributes;
import codes.RoutingEngine;
import codes.ShortestPathAlgorithms;
import codes.WeightedDigraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class DijkstraTest {

    @Test
    void distanceVsTime_chooseDifferentPaths() {
        // Graph:
        // 0->1 (dist 5, time 5)
        // 1->2 (dist 5, time 5)
        // 0->2 (dist 9, time 20)
        // Distance best: direct (9)
        // Time best: via 1 (10)

        WeightedDigraph g = new WeightedDigraph(3);
        EdgeAttributes attrs = new EdgeAttributes();

        int e01 = g.addEdge(0, 1, 0.0);
        int e12 = g.addEdge(1, 2, 0.0);
        int e02 = g.addEdge(0, 2, 0.0);

        attrs.setEdgeCount(g.E());
        attrs.setDistanceMeters(e01, 5);
        attrs.setTimeSeconds(e01, 5);

        attrs.setDistanceMeters(e12, 5);
        attrs.setTimeSeconds(e12, 5);

        attrs.setDistanceMeters(e02, 9);
        attrs.setTimeSeconds(e02, 20);

        ShortestPathAlgorithms.Dijkstra distSP =
                new ShortestPathAlgorithms.Dijkstra(g, attrs, RoutingEngine.Metric.DISTANCE, 0);

        assertTrue(distSP.hasPathTo(2));
        assertEquals(9.0, distSP.distTo(2), 1e-9);

        int[] distPath = toIntArray(distSP.pathEdgeIdsTo(2));
        assertArrayEquals(new int[]{e02}, distPath);

        ShortestPathAlgorithms.Dijkstra timeSP =
                new ShortestPathAlgorithms.Dijkstra(g, attrs, RoutingEngine.Metric.TIME, 0);

        assertTrue(timeSP.hasPathTo(2));
        assertEquals(10.0, timeSP.distTo(2), 1e-9);

        int[] timePath = toIntArray(timeSP.pathEdgeIdsTo(2));
        assertArrayEquals(new int[]{e01, e12}, timePath);
    }

    private static int[] toIntArray(Iterable<Integer> ids) {
        ArrayList<Integer> list = new ArrayList<>();
        for (int x : ids) list.add(x);
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }
}


class AstarTest {

    @Test
    void astarDistance_matchesDijkstraCost() {
        WeightedDigraph g = new WeightedDigraph(3);
        EdgeAttributes attrs = new EdgeAttributes();

        int e01 = g.addEdge(0, 1, 0.0);
        int e12 = g.addEdge(1, 2, 0.0);
        int e02 = g.addEdge(0, 2, 0.0);

        attrs.setEdgeCount(g.E());
        attrs.setDistanceMeters(e01, 5);  attrs.setTimeSeconds(e01, 5);
        attrs.setDistanceMeters(e12, 5);  attrs.setTimeSeconds(e12, 5);
        attrs.setDistanceMeters(e02, 9);  attrs.setTimeSeconds(e02, 20);

        // Coordinates: straight line
        double[] x = {0, 5, 10};
        double[] y = {0, 0, 0};
        ShortestPathAlgorithms.VertexStore vs = new ShortestPathAlgorithms.VertexStore(x, y);

        ShortestPathAlgorithms.Dijkstra d =
                new ShortestPathAlgorithms.Dijkstra(g, attrs, RoutingEngine.Metric.DISTANCE, 0);

        ShortestPathAlgorithms.Astar a =
                new ShortestPathAlgorithms.Astar(g, attrs, vs, RoutingEngine.Metric.DISTANCE, 0, 2, 0.0);

        assertTrue(d.hasPathTo(2));
        assertTrue(a.hasPathToGoal());

        assertEquals(d.distTo(2), a.costToGoal(), 1e-9);

        // Path can differ on ties; here it should be direct.
        int[] path = toIntArray(a.pathEdgeIdsToGoal());
        assertArrayEquals(new int[]{e02}, path);
    }

    @Test
    void astarTime_matchesDijkstraCost_withAdmissibleHeuristic() {
        WeightedDigraph g = new WeightedDigraph(3);
        EdgeAttributes attrs = new EdgeAttributes();

        int e01 = g.addEdge(0, 1, 0.0);
        int e12 = g.addEdge(1, 2, 0.0);
        int e02 = g.addEdge(0, 2, 0.0);

        attrs.setEdgeCount(g.E());
        attrs.setDistanceMeters(e01, 5);  attrs.setTimeSeconds(e01, 5);
        attrs.setDistanceMeters(e12, 5);  attrs.setTimeSeconds(e12, 5);
        attrs.setDistanceMeters(e02, 9);  attrs.setTimeSeconds(e02, 20);

        double[] x = {0, 5, 10};
        double[] y = {0, 0, 0};
        ShortestPathAlgorithms.VertexStore vs = new ShortestPathAlgorithms.VertexStore(x, y);

        // vmax must be an upper bound; 10 m/s is fine here.
        double vmax = 10.0;

        ShortestPathAlgorithms.Dijkstra d =
                new ShortestPathAlgorithms.Dijkstra(g, attrs, RoutingEngine.Metric.TIME, 0);

        ShortestPathAlgorithms.Astar a =
                new ShortestPathAlgorithms.Astar(g, attrs, vs, RoutingEngine.Metric.TIME, 0, 2, vmax);

        assertEquals(d.distTo(2), a.costToGoal(), 1e-9);
        int[] path = toIntArray(a.pathEdgeIdsToGoal());
        assertArrayEquals(new int[]{e01, e12}, path);
    }

    private static int[] toIntArray(Iterable<Integer> ids) {
        ArrayList<Integer> list = new ArrayList<>();
        for (int x : ids) list.add(x);
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }
}