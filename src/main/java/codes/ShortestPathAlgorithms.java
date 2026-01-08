package codes;

import edu.princeton.cs.algs4.IndexMinPQ;

import java.util.Collections;
import java.util.Stack;

/**
 * A collection of shortest path algorithms for weighted directed graphs.
 *
 * <p>Includes implementations of Dijkstra's algorithm and A* search algorithm
 * for finding shortest paths based on distance or time metrics.</p>
 *
 * <p>Design inspired by Robert Sedgewick and Kevin Wayne's shortest path implementations
 * from <i>Algorithms, 4th Edition</i> (Addison-Wesley, 2011).</p>
 *
 * @see <a href="https://algs4.cs.princeton.edu/44sp/">Shortest Paths - Algorithms, 4th Edition</a>
 */

public class ShortestPathAlgorithms {

    /**
     * Stores projected x/y coordinates for each vertex.
     *
     * <p>Used by the A* algorithm to compute straight-line distance heuristics.</p>
     */

    public static class VertexStore {
        private final double[] x;
        private final double[] y;

        /**
         * Initializes a vertex store with the given coordinate arrays.
         *
         * @param x the x-coordinates for each vertex
         * @param y the y-coordinates for each vertex
         * @throws IllegalArgumentException if either array is null or lengths differ
         */

        public VertexStore(double[] x, double[] y) {
            if (x == null || y == null) throw new IllegalArgumentException("x/y arrays are null");
            if (x.length != y.length) throw new IllegalArgumentException("x and y must have same length");
            this.x = x;
            this.y = y;
        }

        /**
         * Returns the number of vertices.
         *
         * @return the number of vertices
         */

        public int V() {
            return x.length;
        }

        /**
         * Returns the x-coordinate of vertex {@code v}.
         *
         * @param v the vertex
         * @return the x-coordinate
         */

        public double x(int v) {
            return x[v];
        }

        /**
         * Returns the y-coordinate of vertex {@code v}.
         *
         * @param v the vertex
         * @return the y-coordinate
         */

        public double y(int v) {
            return y[v];
        }
    }

    /**
     * Computes shortest paths from a single source vertex to all other vertices
     * using Dijkstra's algorithm.
     *
     * <p>Supports both distance-based and time-based routing metrics. Uses an
     * indexed priority queue for efficient relaxation operations.</p>
     *
     * <p>Time complexity: O(E log V) where E is the number of edges and V is
     * the number of vertices.</p>
     */

    public static class Dijkstra {
        private final double[] distTo;
        private final int[] parentEdgeId;
        private final IndexMinPQ<Double> pq;

        private final WeightedDigraph G;
        private final EdgeAttributes attrs;
        private final RoutingEngine.Metric metric;
        private final int s;

        /**
         * Computes shortest paths from source vertex {@code s} to all reachable vertices.
         *
         * @param G      the weighted directed graph
         * @param attrs  edge attributes containing distance/time information
         * @param metric the routing metric (DISTANCE or TIME)
         * @param s      the source vertex
         * @throws IllegalArgumentException if {@code s} is not a valid vertex
         */

        public Dijkstra(WeightedDigraph G, EdgeAttributes attrs, RoutingEngine.Metric metric, int s) {
            this.G = G;
            this.attrs = attrs;
            this.metric = metric;
            this.s = s;

            G.validateVertex(s);

            int V = G.V();
            this.distTo = new double[V];
            this.parentEdgeId = new int[V];
            this.pq = new IndexMinPQ<>(V);

            for (int v = 0; v < V; v++) {
                distTo[v] = Double.POSITIVE_INFINITY;
                parentEdgeId[v] = -1;
            }

            distTo[s] = 0.0;
            pq.insert(s, 0.0);

            while (!pq.isEmpty()) {
                int v = pq.delMin();
                relax(v);
            }
        }

        /**
         * Returns the edge cost based on the current routing metric.
         *
         * @param edgeId the edge ID
         * @return the cost (distance in meters or time in seconds)
         */

        private double edgeCost(int edgeId) {
            return (metric == RoutingEngine.Metric.DISTANCE)
                    ? attrs.distanceMeters(edgeId)
                    : attrs.timeSeconds(edgeId);
        }

        /**
         * Relaxes all outgoing edges from vertex {@code v}.
         *
         * @param v the vertex to relax from
         */

        private void relax(int v) {
            for (Edge e : G.outEdges(v)) {
                int w = e.otherEnd();
                int eid = e.edgeID();

                double candidate = distTo[v] + edgeCost(eid);
                if (candidate < distTo[w]) {
                    distTo[w] = candidate;
                    parentEdgeId[w] = eid;

                    if (pq.contains(w)) pq.decreaseKey(w, distTo[w]);
                    else pq.insert(w, distTo[w]);
                }
            }
        }

        /**
         * Returns the shortest path distance from source to vertex {@code v}.
         *
         * @param v the destination vertex
         * @return the distance (or {@code Double.POSITIVE_INFINITY} if unreachable)
         * @throws IllegalArgumentException if {@code v} is not a valid vertex
         */

        public double distTo(int v) {
            G.validateVertex(v);
            return distTo[v];
        }

        /**
         * Returns true if there is a path from the source to vertex {@code v}.
         *
         * @param v the destination vertex
         * @return {@code true} if a path exists; {@code false} otherwise
         * @throws IllegalArgumentException if {@code v} is not a valid vertex
         */

        public boolean hasPathTo(int v) {
            G.validateVertex(v);
            return distTo[v] < Double.POSITIVE_INFINITY;
        }

        /**
         * Returns the sequence of edge IDs on the shortest path from source to {@code t}.
         *
         * @param t the destination vertex
         * @return an iterable of edge IDs in order from source to destination;
         *         empty if no path exists or {@code t} equals source
         * @throws IllegalArgumentException if {@code t} is not a valid vertex
         * @throws IllegalStateException    if path reconstruction fails unexpectedly
         */

        public Iterable<Integer> pathEdgeIdsTo(int t) {
            G.validateVertex(t);
            if (!hasPathTo(t) || t == s) return new Stack<>();

            Stack<Integer> stack = new Stack<>();
            int cur = t;

            while (cur != s) {
                int eid = parentEdgeId[cur];
                if (eid == -1) {
                    throw new IllegalStateException("Vertex " + t + " has parent edge id -1 despite being reachable");
                }
                stack.push(eid);

                Edge e = G.edgeByID(eid);
                cur = e.firstEnd();
            }

            Collections.reverse(stack);
            return stack;
        }
    }

    /**
     * Computes the shortest path between two vertices using the A* search algorithm.
     *
     * <p>A* uses a heuristic function to guide the search toward the goal, making it
     * more efficient than Dijkstra's algorithm for single-pair shortest path queries.
     * The heuristic is based on Euclidean (straight-line) distance.</p>
     *
     * <p>For the TIME metric, the heuristic divides distance by the maximum speed
     * to ensure admissibility (never overestimates actual cost).</p>
     *
     * <p>Time complexity: O(E log V) worst case, but typically faster than Dijkstra
     * for point-to-point queries due to heuristic pruning.</p>
     */

    public static class Astar {
        private final double[] gScore;
        private final int[] parentEdgeId;
        private final IndexMinPQ<Double> open;

        private final WeightedDigraph G;
        private final EdgeAttributes attrs;
        private final VertexStore vs;
        private final RoutingEngine.Metric metric;
        private final int s;
        private final int goal;

        private final double vmaxMetersPerSec;

        /**
         * Computes the shortest path from source {@code s} to {@code goal}.
         *
         * @param G                 the weighted directed graph
         * @param attrs             edge attributes containing distance/time information
         * @param vs                vertex coordinate store for heuristic computation
         * @param metric            the routing metric (DISTANCE or TIME)
         * @param s                 the source vertex
         * @param goal              the destination vertex
         * @param vmaxMetersPerSec  maximum speed in meters/second (used for TIME metric)
         * @throws IllegalArgumentException if vertices are invalid, VertexStore size
         *                                  doesn't match graph, or vmaxMetersPerSec is
         *                                  non-positive when using TIME metric
         */

        public Astar(WeightedDigraph G,
                     EdgeAttributes attrs,
                     VertexStore vs,
                     RoutingEngine.Metric metric,
                     int s,
                     int goal,
                     double vmaxMetersPerSec) {

            this.G = G;
            this.attrs = attrs;
            this.vs = vs;
            this.metric = metric;
            this.s = s;
            this.goal = goal;
            this.vmaxMetersPerSec = vmaxMetersPerSec;

            G.validateVertex(s);
            G.validateVertex(goal);

            if (vs.V() != G.V()) {
                throw new IllegalArgumentException("VertexStore size (" + vs.V() + ") must equal graph.V() (" + G.V() + ")");
            }

            if (metric == RoutingEngine.Metric.TIME && !(vmaxMetersPerSec > 0.0)) {
                throw new IllegalArgumentException("vmaxMetersPerSec must be > 0 for TIME heuristic");
            }

            int V = G.V();
            this.gScore = new double[V];
            this.parentEdgeId = new int[V];
            this.open = new IndexMinPQ<>(V);

            for (int v = 0; v < V; v++) {
                gScore[v] = Double.POSITIVE_INFINITY;
                parentEdgeId[v] = -1;
            }

            gScore[s] = 0.0;
            open.insert(s, fScore(s));

            while (!open.isEmpty()) {
                int v = open.delMin();
                if (v == goal) break;
                relax(v);
            }
        }

        /**
         * Returns the edge cost based on the current routing metric.
         *
         * @param edgeId the edge ID
         * @return the cost (distance in meters or time in seconds)
         */

        private double edgeCost(int edgeId) {
            return (metric == RoutingEngine.Metric.DISTANCE)
                    ? attrs.distanceMeters(edgeId)
                    : attrs.timeSeconds(edgeId);
        }

        /**
         * Computes the admissible heuristic estimate from vertex {@code v} to goal.
         *
         * <p>Uses Euclidean distance for DISTANCE metric, or Euclidean distance
         * divided by maximum speed for TIME metric.</p>
         *
         * @param v the vertex
         * @return the heuristic estimate
         */

        private double heuristic(int v) {
            double dx = vs.x(v) - vs.x(goal);
            double dy = vs.y(v) - vs.y(goal);
            double straightLine = Math.hypot(dx, dy);

            if (metric == RoutingEngine.Metric.DISTANCE) return straightLine;
            return straightLine / vmaxMetersPerSec;
        }

        /**
         * Computes the f-score for vertex {@code v}.
         *
         * <p>f(v) = g(v) + h(v), where g(v) is the actual cost from source
         * and h(v) is the heuristic estimate to goal.</p>
         *
         * @param v the vertex
         * @return the f-score
         */

        private double fScore(int v) {
            return gScore[v] + heuristic(v);
        }

        /**
         * Relaxes all outgoing edges from vertex {@code v}.
         *
         * @param v the vertex to relax from
         */

        private void relax(int v) {
            for (Edge e : G.outEdges(v)) {
                int w = e.otherEnd();
                int eid = e.edgeID();

                double candidate = gScore[v] + edgeCost(eid);
                if (candidate < gScore[w]) {
                    gScore[w] = candidate;
                    parentEdgeId[w] = eid;

                    double f = fScore(w);
                    if (open.contains(w)) open.decreaseKey(w, f);
                    else open.insert(w, f);
                }
            }
        }

        /**
         * Returns true if a path exists from source to goal.
         *
         * @return {@code true} if a path exists; {@code false} otherwise
         */

        public boolean hasPathToGoal() {
            return gScore[goal] < Double.POSITIVE_INFINITY;
        }

        /**
         * Returns the total cost of the shortest path to the goal.
         *
         * @return the path cost (or {@code Double.POSITIVE_INFINITY} if unreachable)
         */

        public double costToGoal() {
            return gScore[goal];
        }

        /**
         * Returns the sequence of edge IDs on the shortest path from source to goal.
         *
         * @return an iterable of edge IDs in order from source to goal;
         *         empty if no path exists or goal equals source
         * @throws IllegalStateException if path reconstruction fails unexpectedly
         */

        public Iterable<Integer> pathEdgeIdsToGoal() {
            if (!hasPathToGoal() || goal == s) return new Stack<>();

            Stack<Integer> stack = new Stack<>();
            int cur = goal;

            while (cur != s) {
                int eid = parentEdgeId[cur];
                if (eid == -1) {
                    throw new IllegalStateException("Goal has parent edge id -1 despite being reachable");
                }
                stack.push(eid);

                Edge e = G.edgeByID(eid);
                cur = e.firstEnd();
            }

            Collections.reverse(stack);
            return stack;
        }
    }
}