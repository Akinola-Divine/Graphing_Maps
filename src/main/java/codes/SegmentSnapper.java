package codes;

import static java.lang.Math.*;

/**
 * A spatial index for snapping query points to the nearest road segment.
 *
 * <p>Uses a uniform grid to accelerate nearest-segment queries. Each grid cell
 * stores references to segments whose midpoints fall within that cell. Queries
 * search outward from the query point's cell until a sufficiently close match
 * is found.</p>
 *
 * <p>This is useful for map matching, where GPS coordinates need to be snapped
 * to the nearest road in a network.</p>
 *
 * <p>Example usage:
 * <pre>
 *     SegmentSnapper snapper = new SegmentSnapper(graph, geometry, 100.0);
 *     SegmentSnapResult result = snapper.snap(queryX, queryY);
 *     if (result != null) {
 *         int nearestEdge = result.edgeId;
 *         double distanceToRoad = result.distanceMeters;
 *     }
 * </pre>
 * </p>
 */
public final class SegmentSnapper {

    /** The underlying road network graph. */
    private final WeightedDigraph graph;

    /** Geometry (polylines) for each edge. */
    private final EdgeGeometry geom;

    /** Minimum x-coordinate of the bounding box (grid origin). */
    private final double minX;

    /** Minimum y-coordinate of the bounding box (grid origin). */
    private final double minY;

    /** Size of each grid cell in meters. */
    private final double cellSize;

    /** Number of grid cells in the x-direction. */
    private final int gridW;

    /** Number of grid cells in the y-direction. */
    private final int gridH;

    /**
     * Prefix sum array for segment storage.
     * Segments in cell {@code cid} are stored at indices
     * {@code cellStart[cid]} to {@code cellStart[cid+1] - 1}.
     */
    private final int[] cellStart;

    /**
     * Packed segment references: upper 32 bits = edgeId, lower 32 bits = point index.
     */
    private final long[] cellSegments;

    /**
     * The result of snapping a query point to the nearest road segment.
     *
     * <p>Contains the edge and vertex information, the parametric position
     * along the segment, and the perpendicular distance to the road.</p>
     */
    public static class SegmentSnapResult {

        /** The edge ID of the nearest segment. */
        public final int edgeId;

        /** The source vertex of the edge. */
        public final int fromVertex;

        /** The destination vertex of the edge. */
        public final int toVertex;

        /**
         * Parametric position along the matched segment, in range [0, 1].
         * <ul>
         *   <li>t = 0: closest point is at segment start</li>
         *   <li>t = 1: closest point is at segment end</li>
         *   <li>0 < t < 1: closest point is between endpoints</li>
         * </ul>
         */
        public final double t;

        /** Perpendicular distance from query point to the segment (in meters). */
        public final double distanceMeters;

        /**
         * Constructs a snap result.
         *
         * @param edgeId         the matched edge ID
         * @param fromVertex     the source vertex
         * @param toVertex       the destination vertex
         * @param t              parametric position along segment [0, 1]
         * @param distanceMeters distance to segment in meters
         */
        public SegmentSnapResult(int edgeId,
                                 int fromVertex,
                                 int toVertex,
                                 double t,
                                 double distanceMeters) {
            this.edgeId = edgeId;
            this.fromVertex = fromVertex;
            this.toVertex = toVertex;
            this.t = t;
            this.distanceMeters = distanceMeters;
        }

        @Override
        public String toString() {
            return String.format("SnapResult[edge=%d, %d→%d, t=%.3f, dist=%.2fm]",
                    edgeId, fromVertex, toVertex, t, distanceMeters);
        }
    }

    /**
     * Constructs a segment snapper with a uniform grid spatial index.
     *
     * <p>Indexes all segments from the edge geometry into grid cells based on
     * segment midpoints. Smaller cell sizes give faster queries but use more memory.</p>
     *
     * @param graph          the road network graph
     * @param geom           edge geometry containing polyline coordinates
     * @param cellSizeMeters the grid cell size in meters (e.g., 100.0)
     */
    public SegmentSnapper(WeightedDigraph graph,
                          EdgeGeometry geom,
                          double cellSizeMeters) {

        this.graph = graph;
        this.geom = geom;
        this.cellSize = cellSizeMeters;

        // Compute bounding box of all geometry points
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < geom.x.length; i++) {
            minX = min(minX, geom.x[i]);
            minY = min(minY, geom.y[i]);
            maxX = max(maxX, geom.x[i]);
            maxY = max(maxY, geom.y[i]);
        }

        this.minX = minX;
        this.minY = minY;

        // Compute grid dimensions
        gridW = max(1, (int) ceil((maxX - minX) / cellSize));
        gridH = max(1, (int) ceil((maxY - minY) / cellSize));

        int cellCount = gridW * gridH;

        // First pass: count segments per cell
        int[] counts = new int[cellCount];

        for (int e = 0; e < geom.edgeCount(); e++) {
            int s = geom.startIndex(e);
            int end = geom.endIndex(e);
            for (int i = s; i < end - 1; i++) {
                int cx = cellX((geom.x[i] + geom.x[i + 1]) * 0.5);
                int cy = cellY((geom.y[i] + geom.y[i + 1]) * 0.5);
                counts[cy * gridW + cx]++;
            }
        }

        // Build prefix sum for compact storage
        cellStart = new int[cellCount + 1];
        for (int i = 0; i < cellCount; i++)
            cellStart[i + 1] = cellStart[i] + counts[i];

        // Second pass: pack segment references into flat array
        cellSegments = new long[cellStart[cellCount]];
        int[] write = cellStart.clone();

        for (int e = 0; e < geom.edgeCount(); e++) {
            int s = geom.startIndex(e);
            int end = geom.endIndex(e);
            for (int i = s; i < end - 1; i++) {
                int cx = cellX((geom.x[i] + geom.x[i + 1]) * 0.5);
                int cy = cellY((geom.y[i] + geom.y[i + 1]) * 0.5);
                int cid = cy * gridW + cx;
                // Pack edgeId (high 32 bits) and point index (low 32 bits)
                cellSegments[write[cid]++] =
                        (((long) e) << 32) | (i & 0xffffffffL);
            }
        }
    }

    /**
     * Snaps a query point to the nearest road segment.
     *
     * <p>Searches outward from the query point's grid cell, examining nearby
     * cells in expanding rings. For each segment, computes the closest point
     * on the segment using orthogonal projection.</p>
     *
     * <p>Search terminates early when the best match is closer than the
     * current search radius, guaranteeing the optimal result.</p>
     *
     * @param qx the query point x-coordinate (projected meters)
     * @param qy the query point y-coordinate (projected meters)
     * @return the nearest segment result, or {@code null} if no segments exist
     */
    public SegmentSnapResult snap(double qx, double qy) {

        int cx = cellX(qx);
        int cy = cellY(qy);

        double bestDist = Double.POSITIVE_INFINITY;
        SegmentSnapResult best = null;

        // Search outward in expanding rings (Manhattan distance)
        for (int r = 0; r <= 32; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {

                    int x = cx + dx;
                    int y = cy + dy;
                    if (x < 0 || y < 0 || x >= gridW || y >= gridH) continue;

                    int cid = y * gridW + x;

                    // Check all segments in this cell
                    for (int k = cellStart[cid]; k < cellStart[cid + 1]; k++) {

                        long packed = cellSegments[k];
                        int edgeId = (int) (packed >>> 32);
                        int idx    = (int) packed; // segment start point index (i), segment is i -> i+1

                        // Segment endpoints
                        double x0 = geom.x(idx);
                        double y0 = geom.y(idx);
                        double x1 = geom.x(idx + 1);
                        double y1 = geom.y(idx + 1);

                        // Segment direction vector
                        double sx = x1 - x0;
                        double sy = y1 - y0;

                        double denom = sx * sx + sy * sy;
                        if (denom == 0.0) continue; // degenerate segment

                        // Project query point onto segment (parametric segT on this segment)
                        double segT = ((qx - x0) * sx + (qy - y0) * sy) / denom;
                        segT = max(0.0, min(1.0, segT));

                        // Closest point on segment
                        double px = x0 + segT * sx;
                        double py = y0 + segT * sy;

                        double dist = hypot(qx - px, qy - py);

                        if (dist < bestDist) {
                            bestDist = dist;

                            // Convert segment-local segT to edge-normalized t in [0,1]
                            double tEdge = edgeNormalizedT(edgeId, idx, segT);

                            var e = graph.edgeByID(edgeId);
                            best = new SegmentSnapResult(
                                    edgeId, e.firstEnd(), e.otherEnd(), tEdge, dist
                            );
                        }
                    }
                }
            }

            // Early termination: if best match is within current ring, we're done
            if (best != null && bestDist <= r * cellSize) break;
        }

        return best;
    }

    /**
     * Convert a projection that lies on segment (idx -> idx+1) with local segT
     * into a normalized arc-length parameter tEdge along the entire edge polyline.
     */
    private double edgeNormalizedT(int edgeId, int segStartIdx, double segT) {
        int s = geom.startIndex(edgeId);
        int e = geom.endIndex(edgeId);

        // Degenerate: edge has < 2 points
        if (e - s < 2) return 0.0;

        double total = 0.0;   // total edge polyline length
        double before = 0.0;  // length from edge start up to segStartIdx

        for (int i = s; i < e - 1; i++) {
            double len = hypot(geom.x(i + 1) - geom.x(i),
                    geom.y(i + 1) - geom.y(i));
            total += len;
            if (i < segStartIdx) before += len;
        }

        double segLen = hypot(geom.x(segStartIdx + 1) - geom.x(segStartIdx),
                geom.y(segStartIdx + 1) - geom.y(segStartIdx));

        double along = before + segT * segLen;
        return total > 0.0 ? (along / total) : 0.0;
    }

    /**
     * Converts an x-coordinate to a grid cell column index.
     *
     * @param x the x-coordinate
     * @return the cell column index, clamped to [0, gridW-1]
     */
    private int cellX(double x) {
        return clamp((int) ((x - minX) / cellSize), 0, gridW - 1);
    }

    /**
     * Converts a y-coordinate to a grid cell row index.
     *
     * @param y the y-coordinate
     * @return the cell row index, clamped to [0, gridH-1]
     */
    private int cellY(double y) {
        return clamp((int) ((y - minY) / cellSize), 0, gridH - 1);
    }

    /**
     * Clamps an integer value to the given range.
     *
     * @param v  the value to clamp
     * @param lo the lower bound (inclusive)
     * @param hi the upper bound (inclusive)
     * @return the clamped value
     */
    private static int clamp(int v, int lo, int hi) {
        return max(lo, min(hi, v));
    }
}