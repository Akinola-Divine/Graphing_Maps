package codes;

import java.util.ArrayList;
import java.util.List;

/**
 * Command-line interface for routing between geographic coordinates.
 *
 * <p>Provides a high-level API for computing routes given latitude/longitude
 * coordinates. Handles the full pipeline:
 * <ol>
 *   <li>Projects coordinates to a local meter-based coordinate system</li>
 *   <li>Snaps start/goal points to the nearest road segments</li>
 *   <li>Computes the shortest path using A* search</li>
 *   <li>Reconstructs the route geometry</li>
 *   <li>Converts results back to latitude/longitude</li>
 * </ol>
 * </p>
 *
 * <p>Example usage:
 * <pre>
 *     Main.OSMCompiler.BuildResult network = Main.OSMCompiler.compile("map.osm");
 *     List&lt;Point&gt; path = RouteCLI.routeLatLon(
 *         40.7128, -74.0060,  // New York
 *         40.7580, -73.9855,  // Times Square
 *         network
 *     );
 * </pre>
 * </p>
 */
public class RouteCLI {

    /**
     * Computes a route between two geographic points and returns the path geometry.
     *
     * <p>This is a convenience method that discards route metadata. Use
     * {@link #routeLatLonWithRoute} if you need cost or edge information.</p>
     *
     * @param lat1   start latitude (degrees)
     * @param lon1   start longitude (degrees)
     * @param lat2   goal latitude (degrees)
     * @param lon2   goal longitude (degrees)
     * @param result the compiled OSM network data
     * @return list of points (lon, lat) representing the route; empty if no route found
     */
    public static List<Point> routeLatLon(
            double lat1, double lon1,
            double lat2, double lon2,
            Main.OSMCompiler.BuildResult result
    ) {
        RoutingResult rr =
                routeLatLonWithRoute(lat1, lon1, lat2, lon2, result);
        return rr == null ? List.of() : rr.geometry();
    }

    /**
     * Computes a route between two geographic points, returning both geometry and metadata.
     *
     * @param lat1   start latitude (degrees)
     * @param lon1   start longitude (degrees)
     * @param lat2   goal latitude (degrees)
     * @param lon2   goal longitude (degrees)
     * @param result the compiled OSM network data
     * @return the routing result containing geometry and route info; {@code null} if no route found
     */
    public static RoutingResult routeLatLonWithRoute(
            double lat1, double lon1,
            double lat2, double lon2,
            Main.OSMCompiler.BuildResult result
    ) {
        return routeInternal(lat1, lon1, lat2, lon2, result);
    }

    /**
     * Internal routing implementation.
     *
     * <p>Pipeline:
     * <ol>
     *   <li>Create local projection centered on the network</li>
     *   <li>Project all vertices and edge geometry to meters</li>
     *   <li>Build spatial index for segment snapping</li>
     *   <li>Snap query points to nearest road segments</li>
     *   <li>Handle same-edge short-circuit (trivial case)</li>
     *   <li>Try all combinations of start/goal vertices (handles bidirectional snapping)</li>
     *   <li>Reconstruct geometry and convert back to lat/lon</li>
     * </ol>
     * </p>
     *
     * @param lat1   start latitude (degrees)
     * @param lon1   start longitude (degrees)
     * @param lat2   goal latitude (degrees)
     * @param lon2   goal longitude (degrees)
     * @param result the compiled OSM network data
     * @return the routing result; {@code null} if snapping fails or no route exists
     */
    private static RoutingResult routeInternal(
            double lat1, double lon1,
            double lat2, double lon2,
            Main.OSMCompiler.BuildResult result
    ) {
        // --- projection ---
        // Center the projection on the network's mean lat/lon for minimal distortion
        double lat0 = LocalProjection.meanLatitude(result.vertexStore.lat);
        double lon0 = LocalProjection.meanLongitude(result.vertexStore.lon);
        LocalProjection projection = new LocalProjection(lat0, lon0);

        // --- project vertices ---
        int V = result.vertexStore.V();
        double[] vx = new double[V];
        double[] vy = new double[V];
        projection.projectAll(
                result.vertexStore.lat,
                result.vertexStore.lon,
                vx, vy
        );

        // --- project edge geometry ---
        // Convert all edge polyline points from lat/lon to local meters
        EdgeGeometry geo = result.edgeGeometry;
        double[] gx = new double[geo.size()];
        double[] gy = new double[geo.size()];
        double[] tmp = new double[2];

        for (int i = 0; i < geo.size(); i++) {
            // Note: geo stores (lon, lat) as (x, y), so we pass (y, x) to project
            projection.project(geo.y(i), geo.x(i), tmp);
            gx[i] = tmp[0];
            gy[i] = tmp[1];
        }

        EdgeGeometry projectedGeom =
                new EdgeGeometry(geo.edgeStart(), gx, gy);

        // Build spatial index with 1km cell size
        SegmentSnapper snapper =
                new SegmentSnapper(result.graph, projectedGeom, 1000.0);

        // --- project query points ---
        double[] q0 = new double[2];
        double[] q1 = new double[2];
        projection.project(lat1, lon1, q0);
        projection.project(lat2, lon2, q1);

        // Snap to nearest road segments
        SegmentSnapper.SegmentSnapResult startSnap = snapper.snap(q0[0], q0[1]);
        SegmentSnapper.SegmentSnapResult goalSnap  = snapper.snap(q1[0], q1[1]);

        // --- validation ---
        if (startSnap == null || goalSnap == null) return null;
        if (startSnap.edgeId < 0 || goalSnap.edgeId < 0) return null;

        // --- same edge short-circuit ---
        // If both points snap to the same edge, return direct line (no routing needed)
        if (startSnap.edgeId == goalSnap.edgeId) {

            List<Point> xy = Reconstruction.subEdge(projectedGeom, startSnap.edgeId, startSnap.t, goalSnap.t);

            List<Point> out = new ArrayList<>(xy.size());
            double[] ll = new double[2];
            for (Point p : xy) {
                projection.inverse(p.x, p.y, ll);
                out.add(new Point(ll[1], ll[0])); // lon,lat
            }

            // Create a "fake" 1-edge route so instructions can be generated
            RoutingEngine.Route r = new RoutingEngine.Route(
                    true,
                    startSnap.fromVertex,
                    goalSnap.toVertex,
                    RoutingEngine.Metric.DISTANCE,
                    RoutingEngine.Algorithm.ASTAR,
                    result.attrs.distanceMeters(startSnap.edgeId),
                    new int[]{ startSnap.edgeId }
            );

            return new RoutingResult(out, r);
        }

        // --- routing ---
        RoutingEngine engine =
                new RoutingEngine(
                        result.graph,
                        result.attrs,
                        new ShortestPathAlgorithms.VertexStore(
                                result.vertexStore.lat,
                                result.vertexStore.lon),
                        1.0  // vmax = 1.0 m/s (placeholder for distance routing)
                );

        // Try all vertex combinations to find best route
        RoutingEngine.Route r = tryRoute(
                engine,
                startSnap,
                goalSnap,
                result.attrs
        );

        if (r == null || !r.found) return null;

        // --- reconstruct geometry ---
        List<Point> xyRoute =
                Reconstruction.reconstruct(
                        r, projectedGeom, startSnap, goalSnap
                );

        // --- inverse projection ---
        // Convert all route points back to lat/lon
        List<Point> latLonRoute = new ArrayList<>();
        double[] ll = new double[2];

        for (Point p : xyRoute) {
            projection.inverse(p.x, p.y, ll);
            // Store as (lon, lat) in Point
            latLonRoute.add(new Point(ll[1], ll[0]));
        }

        return new RoutingResult(latLonRoute, r);
    }

    /**
     * Tries all combinations of start/goal vertices to find the best route.
     *
     * <p>When snapping to a road segment, either endpoint of that segment could
     * be the optimal entry/exit point. This method tries all four combinations
     * (2 start vertices × 2 goal vertices) and returns the route with the
     * lowest total cost, including partial edge distances from the snap points.</p>
     *
     * <p>The total cost accounts for:
     * <ul>
     *   <li>Distance from the start snap point to the chosen start vertex</li>
     *   <li>Graph distance between start and goal vertices</li>
     *   <li>Distance from the chosen goal vertex to the goal snap point</li>
     * </ul>
     * </p>
     *
     * @param engine    the routing engine
     * @param startSnap the snap result for the start point
     * @param goalSnap  the snap result for the goal point
     * @param attrs     edge attributes for distance lookups
     * @return the best route found; {@code null} if no route exists
     */
    private static RoutingEngine.Route tryRoute(
            RoutingEngine engine,
            SegmentSnapper.SegmentSnapResult startSnap,
            SegmentSnapper.SegmentSnapResult goalSnap,
            EdgeAttributes attrs
    ) {
        RoutingEngine.Route best = null;
        double bestTotalCost = Double.POSITIVE_INFINITY;

        int s0 = startSnap.fromVertex;
        int s1 = startSnap.toVertex;
        int g0 = goalSnap.fromVertex;
        int g1 = goalSnap.toVertex;

        // Get edge lengths for partial distance calculation
        double startEdgeLen = attrs.distanceMeters(startSnap.edgeId);
        double goalEdgeLen = attrs.distanceMeters(goalSnap.edgeId);

        for (int sv : new int[]{s0, s1}) {
            for (int gv : new int[]{g0, g1}) {
                RoutingEngine.Route r = engine.routeDistanceAStar(sv, gv);

                if (!r.found) continue;

                // Calculate actual total distance including partial edges
                double partialStart = (sv == s0)
                        ? startSnap.t * startEdgeLen           // distance from snap to s0
                        : (1 - startSnap.t) * startEdgeLen;    // distance from snap to s1

                double partialGoal = (gv == g0)
                        ? goalSnap.t * goalEdgeLen             // distance from g0 to snap
                        : (1 - goalSnap.t) * goalEdgeLen;      // distance from g1 to snap

                double totalCost = partialStart + r.totalCost + partialGoal;

                if (totalCost < bestTotalCost) {
                    bestTotalCost = totalCost;
                    best = r;
                }
            }
        }
        return best;
    }
}