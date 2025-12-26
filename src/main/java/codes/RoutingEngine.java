package codes;

import java.util.ArrayList;

/**
 * A routing engine that computes shortest paths on weighted directed graphs.
 *
 * <p>Provides a unified interface for computing routes using either Dijkstra's algorithm
 * or A* search, with support for both distance-based and time-based metrics.</p>
 *
 * <p>Example usage:
 * <pre>
 *     // Distance-based routing with Dijkstra (no VertexStore needed)
 *     codes.RoutingEngine engine = new codes.RoutingEngine(graph, attrs);
 *     Route route = engine.routeDistanceDijkstra(0, 42);
 *
 *     // Time-based routing with A* (requires VertexStore and max speed)
 *     codes.RoutingEngine engine = new codes.RoutingEngine(graph, attrs, vertexStore, 30.0);
 *     Route route = engine.routeTimeAStar(0, 42);
 * </pre>
 * </p>
 */
public class RoutingEngine {

    /** The underlying weighted directed graph. */
    private WeightedDigraph digraph;

    /** codes.Edge attributes containing distance and time information. */
    private EdgeAttributes attrs;

    /** Vertex coordinates for A* heuristic computation (may be null if A* not used). */
    private final ShortestPathAlgorithms.VertexStore vertexStore;

    /** Maximum speed in meters/second for time-based A* heuristic. */
    private final double vmaxMetersPerSec;

    /**
     * Routing metric options.
     */
    public enum Metric {
        /** Optimize for shortest distance (meters). */
        DISTANCE,
        /** Optimize for shortest travel time (seconds). */
        TIME
    }

    /**
     * Shortest path algorithm options.
     */
    public enum Algorithm {
        /** Dijkstra's algorithm - computes shortest paths from source to all vertices. */
        DIJKSTRA,
        /** A* search - uses heuristic to efficiently find path to a single goal. */
        ASTAR
    }

    /**
     * Constructs a routing engine for Dijkstra-only routing.
     *
     * <p>Use this constructor when A* is not needed. A* routing methods will
     * throw {@link IllegalStateException} if called.</p>
     *
     * @param G     the weighted directed graph
     * @param attrs edge attributes containing distance/time data
     * @throws IllegalArgumentException if {@code G} or {@code attrs} is null,
     *                                  or if {@code attrs.edgeCount() < G.E()}
     */
    public RoutingEngine(WeightedDigraph G, EdgeAttributes attrs) {
        this(G, attrs, null, 0.0);
    }

    /**
     * Constructs a routing engine with full A* support.
     *
     * @param G                 the weighted directed graph
     * @param attrs             edge attributes containing distance/time data
     * @param vertexStore       vertex coordinates for A* heuristic (may be null for Dijkstra-only)
     * @param vmaxMetersPerSec  maximum speed in m/s for time-based A* heuristic
     * @throws IllegalArgumentException if {@code G} or {@code attrs} is null,
     *                                  {@code attrs.edgeCount() < G.E()}, or
     *                                  {@code vertexStore.V() != G.V()}
     */
    public RoutingEngine(WeightedDigraph G, EdgeAttributes attrs,
                         ShortestPathAlgorithms.VertexStore vertexStore,
                         double vmaxMetersPerSec) {

        if (G == null || attrs == null) throw new IllegalArgumentException("Graph or codes.Edge Attributes cannot be null.");
        if (attrs.edgeCount() < G.E()) throw new IllegalArgumentException("codes.EdgeAttributes.edgeCount() < G.E().");
        if (vertexStore != null && vertexStore.V() != G.V()) {
            throw new IllegalArgumentException("VertexStore.V() must match G.V()");
        }

        this.digraph = G;
        this.attrs = attrs;
        this.vertexStore = vertexStore;
        this.vmaxMetersPerSec = vmaxMetersPerSec;
    }

    /**
     * Computes the shortest distance route using Dijkstra's algorithm.
     *
     * @param start the source vertex
     * @param goal  the destination vertex
     * @return the computed route
     * @throws IllegalArgumentException if either vertex is invalid
     */
    public Route routeDistanceDijkstra(int start, int goal) {
        return route(start, goal, Metric.DISTANCE, Algorithm.DIJKSTRA);
    }

    /**
     * Computes the shortest time route using Dijkstra's algorithm.
     *
     * @param start the source vertex
     * @param goal  the destination vertex
     * @return the computed route
     * @throws IllegalArgumentException if either vertex is invalid
     */
    public Route routeTimeDijkstra(int start, int goal) {
        return route(start, goal, Metric.TIME, Algorithm.DIJKSTRA);
    }

    /**
     * Computes the shortest distance route using A* search.
     *
     * @param start the source vertex
     * @param goal  the destination vertex
     * @return the computed route
     * @throws IllegalArgumentException if either vertex is invalid
     * @throws IllegalStateException    if VertexStore was not provided at construction
     */
    public Route routeDistanceAStar(int start, int goal) {
        return route(start, goal, Metric.DISTANCE, Algorithm.ASTAR);
    }

    /**
     * Computes the shortest time route using A* search.
     *
     * @param start the source vertex
     * @param goal  the destination vertex
     * @return the computed route
     * @throws IllegalArgumentException if either vertex is invalid
     * @throws IllegalStateException    if VertexStore was not provided or vmaxMetersPerSec <= 0
     */
    public Route routeTimeAStar(int start, int goal) {
        return route(start, goal, Metric.TIME, Algorithm.ASTAR);
    }

    /**
     * Core routing method that dispatches to the appropriate algorithm.
     *
     * @param start     the source vertex
     * @param goal      the destination vertex
     * @param metric    the optimization metric (DISTANCE or TIME)
     * @param algorithm the algorithm to use (DIJKSTRA or ASTAR)
     * @return the computed route
     * @throws IllegalArgumentException if either vertex is invalid
     * @throws IllegalStateException    if A* prerequisites are not met
     */
    private Route route(int start, int goal, Metric metric, Algorithm algorithm) {
        digraph.validateVertex(start);
        digraph.validateVertex(goal);

        if (start == goal) {
            return new Route(true, start, goal, metric, algorithm, 0.0, new int[0]);
        }

        boolean found;
        double totalCost;
        int[] edgeIds;

        if (algorithm == Algorithm.DIJKSTRA) {
            ShortestPathAlgorithms.Dijkstra sp =
                    new ShortestPathAlgorithms.Dijkstra(digraph, attrs, metric, start);

            found = sp.hasPathTo(goal);
            totalCost = found ? sp.distTo(goal) : Double.POSITIVE_INFINITY;
            edgeIds = found ? toIntArray(sp.pathEdgeIdsTo(goal)) : new int[0];

        } else { // ASTAR
            if (vertexStore == null) {
                throw new IllegalStateException("A* requires a VertexStore (construct codes.RoutingEngine with VertexStore).");
            }
            if (metric == Metric.TIME && !(vmaxMetersPerSec > 0.0)) {
                throw new IllegalStateException("TIME A* requires vmaxMetersPerSec > 0.");
            }

            double vmax = (metric == Metric.TIME) ? vmaxMetersPerSec : 1.0;

            ShortestPathAlgorithms.Astar sp =
                    new ShortestPathAlgorithms.Astar(digraph, attrs, vertexStore, metric, start, goal, vmax);

            found = sp.hasPathToGoal();
            totalCost = found ? sp.costToGoal() : Double.POSITIVE_INFINITY;
            edgeIds = found ? toIntArray(sp.pathEdgeIdsToGoal()) : new int[0];
        }

        return new Route(found, start, goal, metric, algorithm, totalCost, edgeIds);
    }

    /**
     * Converts an iterable of integers to a primitive int array.
     *
     * @param edgeIds the edge IDs as an iterable
     * @return the edge IDs as a primitive array
     */
    private static int[] toIntArray(Iterable<Integer> edgeIds) {
        ArrayList<Integer> list = new ArrayList<>();
        for (int id : edgeIds) list.add(id);

        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }

    /**
     * Represents the result of a routing computation.
     *
     * <p>Contains all information about the computed route including whether
     * a path was found, the total cost, and the sequence of edges to traverse.</p>
     */
    public static class Route {

        /** {@code true} if a path from start to goal exists. */
        public final boolean found;

        /** The source vertex. */
        public final int startVertex;

        /** The destination vertex. */
        public final int goalVertex;

        /** The metric used for optimization (DISTANCE or TIME). */
        public final Metric metric;

        /** The algorithm used to compute the route. */
        public final Algorithm algorithm;

        /** Total cost of the route (meters or seconds, depending on metric). */
        public final double totalCost;

        /** codes.Edge IDs in traversal order from start to goal (empty if no path). */
        public final int[] edgeIds;

        /**
         * Constructs a route result.
         *
         * @param found       whether a path was found
         * @param startVertex the source vertex
         * @param goalVertex  the destination vertex
         * @param metric      the optimization metric
         * @param algorithm   the algorithm used
         * @param totalCost   the total path cost
         * @param edgeIds     the edge IDs in traversal order
         */
        public Route(boolean found, int startVertex, int goalVertex,
                     Metric metric, Algorithm algorithm,
                     double totalCost, int[] edgeIds) {

            this.found = found;
            this.startVertex = startVertex;
            this.goalVertex = goalVertex;
            this.metric = metric;
            this.algorithm = algorithm;
            this.totalCost = totalCost;
            this.edgeIds = edgeIds;
        }


        /**
         * Returns a string representation of this route.
         *
         * @return a summary of the route
         */
        @Override
        public String toString() {
            if (!found) {
                return String.format("Route[%d → %d]: NO PATH (%s, %s)",
                        startVertex, goalVertex, algorithm, metric);
            }
            String unit = (metric == Metric.DISTANCE) ? "m" : "s";
            return String.format("Route[%d → %d]: %.2f%s, %d edges (%s, %s)",
                    startVertex, goalVertex, totalCost, unit, edgeIds.length, algorithm, metric);
        }
    }
}