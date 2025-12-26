package tests;

import codes.EdgeAttributes;
import codes.RoutingEngine;
import codes.WeightedDigraph;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class RoutingEngineTest {

    @Test
    void constructor_nullsThrow() {
        EdgeAttributes attrs = new EdgeAttributes();
        WeightedDigraph g = new WeightedDigraph(1);

        assertThrows(IllegalArgumentException.class, () -> new RoutingEngine(null, attrs));
        assertThrows(IllegalArgumentException.class, () -> new RoutingEngine(g, null));
    }

    @Test
    void routeDistanceDijkstra_returnsValidRoute() throws Exception {
        WeightedDigraph g = new WeightedDigraph(3);
        EdgeAttributes attrs = new EdgeAttributes();

        int e01 = g.addEdge(0, 1, 0.0);
        int e12 = g.addEdge(1, 2, 0.0);

        attrs.setEdgeCount(g.E());
        attrs.setDistanceMeters(e01, 5); attrs.setTimeSeconds(e01, 5);
        attrs.setDistanceMeters(e12, 6); attrs.setTimeSeconds(e12, 6);

        RoutingEngine engine = new RoutingEngine(g, attrs /* possibly also vertexStore depending on your constructor */);

        Object route = engine.routeDistanceDijkstra(0, 2);

        boolean found = (boolean) getField(route, "found");
        double totalCost = (double) getField(route, "totalCost");
        int[] edgeIds = (int[]) getField(route, "edgeIds");

        assertTrue(found);
        assertEquals(11.0, totalCost, 1e-9);
        assertArrayEquals(new int[]{e01, e12}, edgeIds);
    }

    private static Object getField(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(obj);
    }
}