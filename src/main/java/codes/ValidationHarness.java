package codes;

import java.nio.file.Path;

import static java.lang.Math.max;

/**
 * A validation harness for testing routing algorithm correctness.
 *
 * <p>Runs randomized tests comparing Dijkstra's algorithm against A* search
 * to verify they produce equivalent results. Also validates path integrity
 * by checking edge connectivity and cost consistency.</p>
 *
 * <p>Validation checks performed:
 * <ul>
 *   <li><b>Reachability:</b> Both algorithms agree on whether a path exists</li>
 *   <li><b>Cost equality:</b> Both algorithms produce the same optimal cost</li>
 *   <li><b>Path integrity:</b> Edges form a valid connected path from start to goal</li>
 *   <li><b>Cost consistency:</b> Sum of edge distances equals reported total cost</li>
 * </ul>
 * </p>
 *
 * <p>Example usage:
 * <pre>
 *     ValidationHarness harness = new ValidationHarness(graph, attrs, vertexStore);
 *     harness.run();  // Runs 100 random tests and prints summary
 * </pre>
 * </p>
 */
public class ValidationHarness {

    /** Number of random test pairs to generate. */
    private final int NUM_TESTS = 100;

    /** Tolerance for floating-point cost comparisons. */
    private final double COST_EPS = 1e-6;

    /** The graph to test routing on. */
    private WeightedDigraph graph;

    /** The routing engine under test. */
    private RoutingEngine rEngine;

    /** Edge attributes for distance lookups. */
    private EdgeAttributes eAttrs;

    /** Vertex coordinates for A* heuristic. */
    private ShortestPathAlgorithms.VertexStore vStore;

    /** Random start vertices for each test. */
    private int[] starts;

    /** Random goal vertices for each test. */
    private int[] goals;

    // -------- Statistics --------

    /** Total number of queries executed. */
    private int totalQueries;

    /** Number of queries where a path was found. */
    private int reachableCount;

    /** Sum of route distances for reachable pairs (meters). */
    private double sumRouteMeters;

    /** Maximum route distance seen (meters). */
    private double maxRouteMeters;

    /** Sum of edge counts for reachable routes. */
    private int sumEdgeCount;

    /** Maximum edge count seen in a single route. */
    private int maxEdgeCount;

    /**
     * Constructs a validation harness for the given graph.
     *
     * @param g      the weighted directed graph to test
     * @param attrs  edge attributes containing distances
     * @param vstore vertex coordinates for A* heuristic
     */
    public ValidationHarness(WeightedDigraph g, EdgeAttributes attrs, ShortestPathAlgorithms.VertexStore vstore) {
        graph = g;
        eAttrs = attrs;
        vStore = vstore;
        starts = new int[NUM_TESTS];
        goals = new int[NUM_TESTS];
        rEngine = new RoutingEngine(graph, eAttrs, vStore, 100);
    }

    /**
     * Runs all validation tests and prints a summary.
     *
     * <p>For each random start/goal pair:
     * <ol>
     *   <li>Computes route using Dijkstra's algorithm</li>
     *   <li>Computes route using A* search</li>
     *   <li>Validates reachability agreement</li>
     *   <li>Validates cost equality (within epsilon)</li>
     *   <li>Validates path integrity for both routes</li>
     *   <li>Accumulates statistics</li>
     * </ol>
     * </p>
     *
     * @throws IllegalStateException if any validation check fails
     */
    public void run() {
        generateRandomPairs();

        // Reset statistics
        totalQueries = 0;
        reachableCount = 0;
        sumRouteMeters = 0.0;
        maxRouteMeters = 0.0;
        sumEdgeCount = 0;
        maxEdgeCount = 0;

        for (int i = 0; i < NUM_TESTS; i++) {
            int s = starts[i];
            int t = goals[i];

            // Run both algorithms
            RoutingEngine.Route dijkstraRoute = rEngine.routeDistanceDijkstra(s, t);
            RoutingEngine.Route astarRoute = rEngine.routeDistanceAStar(s, t);

            // Validate correctness
            validateReachability(dijkstraRoute, astarRoute);
            validateCostEquality(dijkstraRoute, astarRoute);
            validatePathIntegrity(dijkstraRoute);
            validatePathIntegrity(astarRoute);

            totalQueries++;

            // Collect statistics for reachable pairs
            if (dijkstraRoute.found) {
                reachableCount++;
                sumRouteMeters += dijkstraRoute.totalCost;
                maxRouteMeters = max(maxRouteMeters, dijkstraRoute.totalCost);
                sumEdgeCount += dijkstraRoute.edgeIds.length;
                maxEdgeCount = max(maxEdgeCount, dijkstraRoute.edgeIds.length);
            }
        }

        printSummary();
    }

    /**
     * Generates random start/goal vertex pairs for testing.
     *
     * <p>Ensures that start and goal are different vertices for each test.</p>
     */
    public void generateRandomPairs() {
        int v = graph.V();

        for (int i = 0; i < NUM_TESTS; i++) {
            int s = (int) (Math.random() * v);
            int t = (int) (Math.random() * v);

            // Ensure s != t
            while (t == s) {
                t = (int) (Math.random() * v);
            }

            starts[i] = s;
            goals[i] = t;
        }
    }

    /**
     * Validates that both algorithms agree on path existence.
     *
     * <p>If Dijkstra finds a path, A* must also find a path (since A* with
     * an admissible heuristic is complete).</p>
     *
     * @param d the Dijkstra route result
     * @param a the A* route result
     * @throws IllegalStateException if Dijkstra found a path but A* did not
     */
    public void validateReachability(RoutingEngine.Route d, RoutingEngine.Route a) {
        if (d.found && !a.found) {
            throw new IllegalStateException(
                    "Reachability mismatch: Dijkstra found path but A* did not");
        }
    }

    /**
     * Validates that both algorithms produce the same optimal cost.
     *
     * <p>Since both algorithms are optimal (for admissible heuristics),
     * their costs must be equal within floating-point tolerance.</p>
     *
     * @param d the Dijkstra route result
     * @param a the A* route result
     * @throws IllegalStateException if costs differ by more than epsilon
     */
    public void validateCostEquality(RoutingEngine.Route d, RoutingEngine.Route a) {
        validateReachability(d, a);

        if (Math.abs(d.totalCost - a.totalCost) > COST_EPS) {
            throw new IllegalStateException(String.format(
                    "Cost mismatch: Dijkstra=%.6f, A*=%.6f (diff=%.9f)",
                    d.totalCost, a.totalCost, Math.abs(d.totalCost - a.totalCost)));
        }
    }

    /**
     * Validates the structural integrity of a route.
     *
     * <p>Checks performed:
     * <ul>
     *   <li>First edge starts at the route's start vertex</li>
     *   <li>Consecutive edges are connected (end of edge i = start of edge i+1)</li>
     *   <li>Last edge ends at the route's goal vertex</li>
     *   <li>Sum of edge distances equals the reported total cost</li>
     * </ul>
     * </p>
     *
     * @param route the route to validate
     * @throws IllegalStateException if any integrity check fails
     */
    public void validatePathIntegrity(RoutingEngine.Route route) {
        // Skip unreachable routes
        if (!route.found) return;

        // Handle trivial same-vertex case
        if (route.edgeIds.length == 0) {
            if (route.startVertex == route.goalVertex) return;
            throw new IllegalStateException(
                    "Empty edge list but start != goal");
        }

        // Check first edge starts at start vertex
        Edge firstEdge = graph.edgeByID(route.edgeIds[0]);
        if (route.startVertex != firstEdge.firstEnd()) {
            throw new IllegalStateException(String.format(
                    "First edge doesn't start at start vertex: expected %d, got %d",
                    route.startVertex, firstEdge.firstEnd()));
        }

        // Check edge connectivity
        for (int i = 0; i < route.edgeIds.length - 1; i++) {
            Edge current = graph.edgeByID(route.edgeIds[i]);
            Edge next = graph.edgeByID(route.edgeIds[i + 1]);

            if (current.otherEnd() != next.firstEnd()) {
                throw new IllegalStateException(String.format(
                        "Edges not connected at index %d: edge %d ends at %d, edge %d starts at %d",
                        i, route.edgeIds[i], current.otherEnd(),
                        route.edgeIds[i + 1], next.firstEnd()));
            }
        }

        // Check last edge ends at goal vertex
        Edge lastEdge = graph.edgeByID(route.edgeIds[route.edgeIds.length - 1]);
        if (route.goalVertex != lastEdge.otherEnd()) {
            throw new IllegalStateException(String.format(
                    "Last edge doesn't end at goal vertex: expected %d, got %d",
                    route.goalVertex, lastEdge.otherEnd()));
        }

        // Check cost consistency
        double sum = 0;
        for (int edgeId : route.edgeIds) {
            sum += eAttrs.distanceMeters(edgeId);
        }

        if (Math.abs(route.totalCost - sum) > COST_EPS) {
            throw new IllegalStateException(String.format(
                    "Cost inconsistency: reported=%.6f, computed=%.6f",
                    route.totalCost, sum));
        }
    }

    /**
     * Prints a summary of all test results.
     *
     * <p>Includes:
     * <ul>
     *   <li>Reachability percentage</li>
     *   <li>Average and maximum route distances</li>
     *   <li>Average and maximum edge counts</li>
     * </ul>
     * </p>
     */
    public void printSummary() {
        System.out.println("---- Routing Validation Summary ----");

        if (reachableCount == 0) {
            System.out.println("No reachable pairs found in " + totalQueries + " tests");
            return;
        }

        double reachablePct = 100.0 * reachableCount / totalQueries;
        double avgDist = sumRouteMeters / reachableCount;
        double avgEdges = (double) sumEdgeCount / reachableCount;

        System.out.printf("Reachable pairs: %d / %d (%.1f%%)%n",
                reachableCount, totalQueries, reachablePct);
        System.out.printf("Average distance: %.1f m%n", avgDist);
        System.out.printf("Max distance: %.1f m%n", maxRouteMeters);
        System.out.printf("Average edges: %.1f%n", avgEdges);
        System.out.printf("Max edges: %d%n", maxEdgeCount);
        System.out.println("All validations passed ✓");
    }

    /**
     * Runs the validation harness on a compiled OSM file.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(String[] args) {
        System.out.println("Compiling OSM...");
        Main.OSMCompiler compiler = new Main.OSMCompiler();
        Main.OSMCompiler.BuildResult result = compiler.compile(Path.of("pei.osm"));

        System.out.printf("Graph: V=%d, E=%d%n", result.graph.V(), result.graph.E());
        System.out.println("Running validation...\n");

        ValidationHarness harness = new ValidationHarness(
                result.graph,
                result.attrs,
                new ShortestPathAlgorithms.VertexStore(
                        result.vertexStore.lat,
                        result.vertexStore.lon)
        );

        harness.run();
    }
}