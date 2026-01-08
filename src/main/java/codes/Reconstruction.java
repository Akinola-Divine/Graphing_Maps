package codes;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconstructs the visual polyline geometry for a computed route.
 *
 * <p>Given a route (sequence of edge IDs) and snap results for the start and goal
 * points, this class produces a continuous list of points representing the actual
 * path to render or export.</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Trims the first edge from the snapped start point forward</li>
 *   <li>Includes full geometry for all intermediate edges</li>
 *   <li>Trims the last edge up to the snapped goal point</li>
 *   <li>Handles the special case where start and goal are on the same edge</li>
 *   <li>Removes duplicate consecutive points</li>
 * </ul>
 * </p>
 *
 * <p>Example usage:
 * <pre>
 *     SegmentSnapResult startSnap = snapper.snap(startX, startY);
 *     SegmentSnapResult goalSnap = snapper.snap(goalX, goalY);
 *     Route route = engine.routeDistanceDijkstra(startSnap.fromVertex, goalSnap.fromVertex);
 *     List&lt;Point&gt; polyline = Reconstruction.reconstruct(route, geometry, startSnap, goalSnap);
 * </pre>
 * </p>
 */
public class Reconstruction {

    /**
     * Reconstructs the route geometry as a list of points.
     *
     * <p>The resulting polyline starts at the snapped start position, follows
     * the edge geometries through the route, and ends at the snapped goal position.</p>
     *
     * @param r     the computed route containing edge IDs
     * @param g     the edge geometry provider
     * @param start the snap result for the start point (contains edge and parametric t)
     * @param goal  the snap result for the goal point (contains edge and parametric t)
     * @return a list of points forming the route polyline; empty list if route is null/empty
     * @throws IllegalArgumentException if {@code r.found} is false
     */
    public static List<Point> reconstruct(
            RoutingEngine.Route r,
            EdgeGeometry g,
            SegmentSnapper.SegmentSnapResult start,
            SegmentSnapper.SegmentSnapResult goal
    ) {
        if (r == null || r.edgeIds == null || r.edgeIds.length == 0) {
            return List.of();
        }

        if (!r.found) {
            throw new IllegalArgumentException("Route not found, cannot reconstruct");
        }

        List<Point> polyline = new ArrayList<>();

        int first = r.edgeIds[0];

        /* ============================================================
         * SAME EDGE SHORT-CIRCUIT
         * If start and goal are on the same edge, extract only the
         * portion between the two t values.
         * ============================================================ */
        if (start != null && goal != null && start.edgeId == goal.edgeId) {
            Point pStart = interpolateOnEdge(g, start.edgeId, start.t);
            Point pGoal  = interpolateOnEdge(g, goal.edgeId, goal.t);

            polyline.add(pStart);

            int s = g.startIndex(start.edgeId);
            int e = g.endIndex(start.edgeId);

            // Add intermediate geometry points (excluding start/goal duplicates)
            for (int i = s; i < e; i++) {
                Point p = new Point(g.x(i), g.y(i));
                if (!p.equals(pStart) && !p.equals(pGoal)) {
                    polyline.add(p);
                }
            }

            polyline.add(pGoal);
            return polyline;
        }

        /* ============================================================
         * FIRST EDGE (TRIM FROM start.t FORWARD)
         * Only include geometry from the snap point to the edge end.
         * ============================================================ */
        int s = g.startIndex(first);
        int e = g.endIndex(first);

        // Compute total edge length for t → distance conversion
        double total = 0.0;
        for (int i = s; i < e - 1; i++) {
            total += Math.hypot(
                    g.x(i + 1) - g.x(i),
                    g.y(i + 1) - g.y(i)
            );
        }

        double target = start.t * total;
        double acc = 0.0;
        int seg = s;

        // Find the segment containing the start point
        for (int i = s; i < e - 1; i++) {
            double len = Math.hypot(
                    g.x(i + 1) - g.x(i),
                    g.y(i + 1) - g.y(i)
            );
            if (acc + len >= target) {
                seg = i;
                break;
            }
            acc += len;
        }

        // Add the interpolated start point
        Point pStart = interpolateOnEdge(g, first, start.t);
        polyline.add(pStart);

        // Add remaining geometry points after the snap point
        for (int i = seg + 1; i < e; i++) {
            polyline.add(new Point(g.x(i), g.y(i)));
        }

        /* ============================================================
         * MIDDLE EDGES
         * Add full geometry for all edges between first and last.
         * ============================================================ */
        for (int i = 1; i < r.edgeIds.length - 1; i++) {
            appendFullEdgeGeometry(polyline, g, r.edgeIds[i], true);
        }

        /* ============================================================
         * LAST EDGE (TRIM TO goal.t)
         * Only include geometry from edge start up to the snap point.
         * ============================================================ */
        int last = r.edgeIds[r.edgeIds.length - 1];

        if (goal != null && goal.edgeId == last) {
            int ls = g.startIndex(last);
            int le = g.endIndex(last);

            Point pGoal = interpolateOnEdge(g, last, goal.t);

            // Add geometry points before the goal
            for (int i = ls; i < le; i++) {
                Point p = new Point(g.x(i), g.y(i));
                if (!p.equals(pGoal)) {
                    polyline.add(p);
                }
            }

            polyline.add(pGoal);
        } else {
            appendFullEdgeGeometry(polyline, g, last, true);
        }

        return polyline;
    }

    /**
     * Computes the interpolated point at parametric position {@code t} along an edge.
     *
     * <p>The edge geometry is treated as a polyline with multiple segments. The
     * parameter {@code t} represents the normalized arc-length position:
     * <ul>
     *   <li>{@code t = 0.0}: returns the first point of the edge</li>
     *   <li>{@code t = 1.0}: returns the last point of the edge</li>
     *   <li>{@code 0 < t < 1}: returns an interpolated point along the polyline</li>
     * </ul>
     * </p>
     *
     * @param g      the edge geometry provider
     * @param edgeId the edge ID
     * @param t      the parametric position along the edge [0, 1]
     * @return the interpolated point
     * @throws IllegalArgumentException if {@code edgeId < 0}
     */
    public static Point interpolateOnEdge(
            EdgeGeometry g, int edgeId, double t
    ) {
        if (edgeId < 0) {
            throw new IllegalArgumentException("edgeId < 0");
        }

        int start = g.startIndex(edgeId);
        int end   = g.endIndex(edgeId);

        // Degenerate case: single point or empty edge
        if (end - start < 2) {
            return new Point(g.x(start), g.y(start));
        }

        // Compute total polyline length
        double total = 0.0;
        for (int i = start; i < end - 1; i++) {
            total += Math.hypot(
                    g.x(i + 1) - g.x(i),
                    g.y(i + 1) - g.y(i)
            );
        }

        double target = t * total;
        double acc = 0.0;

        // Walk along segments until we reach the target distance
        for (int i = start; i < end - 1; i++) {
            double x0 = g.x(i);
            double y0 = g.y(i);
            double x1 = g.x(i + 1);
            double y1 = g.y(i + 1);

            double len = Math.hypot(x1 - x0, y1 - y0);
            if (acc + len >= target) {
                // Interpolate within this segment
                double lt = (target - acc) / len;
                return new Point(
                        x0 + lt * (x1 - x0),
                        y0 + lt * (y1 - y0)
                );
            }
            acc += len;
        }

        // Fallback: return last point (handles t ≈ 1.0 and rounding)
        return new Point(g.x(end - 1), g.y(end - 1));
    }

    /**
     * Appends all geometry points from an edge to the polyline.
     *
     * <p>Optionally skips the first point to avoid duplicates when edges
     * are connected end-to-end. Also skips consecutive duplicate points.</p>
     *
     * @param polyline  the list to append points to
     * @param g         the edge geometry provider
     * @param edgeId    the edge ID
     * @param skipFirst if {@code true}, skips the first point of the edge
     */
    private static void appendFullEdgeGeometry(
            List<Point> polyline,
            EdgeGeometry g,
            int edgeId,
            boolean skipFirst
    ) {
        int s = g.startIndex(edgeId);
        int e = g.endIndex(edgeId);

        for (int i = s; i < e; i++) {
            // Skip first point if requested (avoids duplicate at edge junction)
            if (skipFirst && i == s) continue;

            Point p = new Point(g.x(i), g.y(i));

            // Skip consecutive duplicates
            if (!polyline.isEmpty()) {
                Point last = polyline.get(polyline.size() - 1);
                if (last.x == p.x && last.y == p.y) continue;
            }

            polyline.add(p);
        }
    }

    public static List<Point> subEdge(EdgeGeometry g, int edgeId, double t0, double t1) {
        int s = g.startIndex(edgeId);
        int e = g.endIndex(edgeId);
        if (e - s < 2) return java.util.List.of(new Point(g.x(s), g.y(s)));

        boolean reverse = false;
        if (t0 > t1) { double tmp = t0; t0 = t1; t1 = tmp; reverse = true; }

        // segment lengths + total
        int nSeg = (e - s - 1);
        double[] segLen = new double[nSeg];
        double total = 0.0;
        for (int i = 0; i < nSeg; i++) {
            double x0 = g.x(s + i),     y0 = g.y(s + i);
            double x1 = g.x(s + i + 1), y1 = g.y(s + i + 1);
            double len = Math.hypot(x1 - x0, y1 - y0);
            segLen[i] = len;
            total += len;
        }
        if (total == 0.0) return java.util.List.of(new Point(g.x(s), g.y(s)));

        double d0 = t0 * total;
        double d1 = t1 * total;

        java.util.ArrayList<Point> out = new java.util.ArrayList<>();
        double acc = 0.0;

        for (int i = 0; i < nSeg; i++) {
            double len = segLen[i];
            if (len == 0.0) continue;

            double next = acc + len;

            // segment is completely before interval
            if (next <= d0) { acc = next; continue; }
            // segment is completely after interval
            if (acc >= d1) break;

            double x0 = g.x(s + i),     y0 = g.y(s + i);
            double x1 = g.x(s + i + 1), y1 = g.y(s + i + 1);

            double a = Math.max(0.0, (d0 - acc) / len);
            double b = Math.min(1.0, (d1 - acc) / len);

            if (out.isEmpty()) {
                out.add(new Point(x0 + a * (x1 - x0), y0 + a * (y1 - y0)));
            }

            if (b < 1.0) {
                out.add(new Point(x0 + b * (x1 - x0), y0 + b * (y1 - y0)));
                break;
            } else {
                out.add(new Point(x1, y1));
            }

            acc = next;
        }

        if (reverse) java.util.Collections.reverse(out);
        return out;
    }
}