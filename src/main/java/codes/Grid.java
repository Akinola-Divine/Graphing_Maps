package codes;

import static java.lang.Math.*;

/**
 * A uniform grid spatial index for fast nearest-vertex queries.
 *
 * <p>Divides the coordinate space into fixed-size cells and indexes vertices
 * by their cell location. Queries search outward in expanding rings until
 * a sufficiently close vertex is found.</p>
 *
 * <p>Uses Compressed Sparse Row (CSR) storage for memory efficiency:
 * vertices in cell {@code c} are stored at indices
 * {@code cellStart[c]} to {@code cellStart[c+1] - 1} in {@code cellVerts}.</p>
 *
 * <p>Example usage:
 * <pre>
 *     Grid grid = new Grid();
 *     grid.buildFromVertices(xCoords, yCoords, 100.0);  // 100m cells
 *     int nearest = grid.snapNearestVertex(queryX, queryY);
 * </pre>
 * </p>
 *
 * @see SegmentSnapper for snapping to road segments instead of vertices
 */
public class Grid {

    /* ---------------- GEOMETRY ---------------- */

    /** Minimum x-coordinate of the bounding box (meters). */
    private double minX;

    /** Minimum y-coordinate of the bounding box (meters). */
    private double minY;

    /** Maximum x-coordinate of the bounding box (meters). */
    private double maxX;

    /** Maximum y-coordinate of the bounding box (meters). */
    private double maxY;

    /** Size of each grid cell (meters). */
    private double cellSize;

    /** Number of cells in the x-direction. */
    private int gridW;

    /** Number of cells in the y-direction. */
    private int gridH;

    /** Total number of grid cells. */
    private int cellCount;

    /** X-coordinates of all vertices (meters). */
    private double[] vx;

    /** Y-coordinates of all vertices (meters). */
    private double[] vy;

    /**
     * CSR row pointers: vertices in cell {@code c} are at indices
     * {@code cellStart[c]} to {@code cellStart[c+1] - 1}.
     */
    private int[] cellStart;

    /** CSR column data: vertex IDs sorted by cell. */
    private int[] cellVerts;

    /* ---------------- BUILD ---------------- */

    /**
     * Builds the spatial index from vertex coordinates.
     *
     * <p>Construction algorithm:
     * <ol>
     *   <li>Compute bounding box of all vertices</li>
     *   <li>Determine grid dimensions based on cell size</li>
     *   <li>Count vertices per cell</li>
     *   <li>Build prefix sum array (CSR row pointers)</li>
     *   <li>Assign vertices to cells (CSR column data)</li>
     * </ol>
     * </p>
     *
     * <p>Time complexity: O(V) where V is the number of vertices.</p>
     * <p>Space complexity: O(V + cells) for the index structure.</p>
     *
     * @param vx             x-coordinates of vertices (meters)
     * @param vy             y-coordinates of vertices (meters)
     * @param cellSizeMeters the grid cell size in meters
     * @throws IllegalArgumentException if arrays are null, empty, mismatched,
     *                                  or cellSize is not positive
     */
    public void buildFromVertices(double[] vx, double[] vy, double cellSizeMeters) {

        if (vx == null || vy == null)
            throw new IllegalArgumentException("Null coordinate arrays");
        if (vx.length != vy.length)
            throw new IllegalArgumentException("vx/vy length mismatch");
        if (vx.length == 0)
            throw new IllegalArgumentException("No vertices");
        if (!(cellSizeMeters > 0.0))
            throw new IllegalArgumentException("cellSize must be > 0");

        this.vx = vx;
        this.vy = vy;
        this.cellSize = cellSizeMeters;

        int V = vx.length;

        // 1) Compute bounding box
        minX = maxX = vx[0];
        minY = maxY = vy[0];

        for (int i = 1; i < V; i++) {
            minX = min(minX, vx[i]);
            maxX = max(maxX, vx[i]);
            minY = min(minY, vy[i]);
            maxY = max(maxY, vy[i]);
        }

        // 2) Compute grid dimensions (ensure at least 1×1)
        gridW = max(1, (int) ceil((maxX - minX) / cellSize));
        gridH = max(1, (int) ceil((maxY - minY) / cellSize));
        cellCount = gridW * gridH;

        // 3) Count vertices per cell
        int[] counts = new int[cellCount];
        for (int v = 0; v < V; v++) {
            int cid = cellIdOf(vx[v], vy[v]);
            counts[cid]++;
        }

        // 4) Build prefix sum → CSR row pointers
        cellStart = new int[cellCount + 1];
        for (int c = 0; c < cellCount; c++) {
            cellStart[c + 1] = cellStart[c] + counts[c];
        }

        // 5) Fill CSR column data (vertex IDs)
        cellVerts = new int[V];
        int[] writePos = cellStart.clone();

        for (int v = 0; v < V; v++) {
            int cid = cellIdOf(vx[v], vy[v]);
            cellVerts[writePos[cid]++] = v;
        }
    }

    /* ---------------- QUERY ---------------- */

    /**
     * Finds the nearest vertex to the query point.
     *
     * <p>Convenience method that uses default max ring of 32 cells
     * and returns only the vertex ID.</p>
     *
     * @param qx query x-coordinate (meters)
     * @param qy query y-coordinate (meters)
     * @return the nearest vertex ID, or -1 if none found
     */
    public int snapNearestVertex(double qx, double qy) {
        return snapNearestVertex(qx, qy, 32).vertexId;
    }

    /**
     * Finds the nearest vertex to the query point with detailed result.
     *
     * <p>Search algorithm:
     * <ol>
     *   <li>Determine the cell containing the query point</li>
     *   <li>Search in expanding rings (Manhattan distance)</li>
     *   <li>For each cell in the ring, check all vertices</li>
     *   <li>Stop when best distance ≤ ring radius × cellSize</li>
     * </ol>
     * </p>
     *
     * <p>The early termination condition guarantees optimality: if the best
     * vertex found is within the current ring's radius, no vertex in outer
     * rings can be closer.</p>
     *
     * @param qx      query x-coordinate (meters)
     * @param qy      query y-coordinate (meters)
     * @param maxRing maximum number of rings to search (limits search area)
     * @return snap result with vertex ID and distance; vertex ID is -1 if none found
     */
    public SnapResult snapNearestVertex(double qx, double qy, int maxRing) {

        int cx = cellX(qx);
        int cy = cellY(qy);

        int bestV = -1;
        double bestDist = Double.POSITIVE_INFINITY;

        // Search outward in expanding rings
        for (int r = 0; r <= maxRing; r++) {

            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {

                    int x = cx + dx;
                    int y = cy + dy;

                    // Skip cells outside grid bounds
                    if (x < 0 || y < 0 || x >= gridW || y >= gridH) continue;

                    int cid = y * gridW + x;
                    int start = cellStart[cid];
                    int end   = cellStart[cid + 1];

                    // Check all vertices in this cell
                    for (int i = start; i < end; i++) {
                        int v = cellVerts[i];
                        double dxm = qx - vx[v];
                        double dym = qy - vy[v];
                        double d = sqrt(dxm * dxm + dym * dym);

                        if (d < bestDist) {
                            bestDist = d;
                            bestV = v;
                        }
                    }
                }
            }

            // Early termination: best vertex is within ring radius
            if (bestV != -1 && bestDist <= r * cellSize) {
                break;
            }
        }

        return new SnapResult(bestV, bestDist);
    }

    /* ---------------- CELL HELPERS ---------------- */

    /**
     * Computes the cell ID for a coordinate.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @return the cell ID (row-major order)
     */
    private int cellIdOf(double x, double y) {
        return cellY(y) * gridW + cellX(x);
    }

    /**
     * Converts an x-coordinate to a cell column index.
     *
     * @param x the x-coordinate
     * @return the cell column, clamped to [0, gridW-1]
     */
    private int cellX(double x) {
        int cx = (int) ((x - minX) / cellSize);
        return clamp(cx, 0, gridW - 1);
    }

    /**
     * Converts a y-coordinate to a cell row index.
     *
     * @param y the y-coordinate
     * @return the cell row, clamped to [0, gridH-1]
     */
    private int cellY(double y) {
        int cy = (int) ((y - minY) / cellSize);
        return clamp(cy, 0, gridH - 1);
    }

    /**
     * Clamps an integer to a range.
     *
     * @param v  the value
     * @param lo the lower bound (inclusive)
     * @param hi the upper bound (inclusive)
     * @return the clamped value
     */
    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /* ---------------- RESULT ---------------- */

    /**
     * Result of a nearest-vertex query.
     */
    public static final class SnapResult {

        /** The ID of the nearest vertex, or -1 if none found. */
        public final int vertexId;

        /** Distance from query point to the nearest vertex (meters). */
        public final double distanceMeters;

        /**
         * Constructs a snap result.
         *
         * @param vertexId       the nearest vertex ID (-1 if none)
         * @param distanceMeters the distance in meters
         */
        public SnapResult(int vertexId, double distanceMeters) {
            this.vertexId = vertexId;
            this.distanceMeters = distanceMeters;
        }

        /**
         * Returns true if a vertex was found.
         *
         * @return {@code true} if vertexId ≥ 0
         */
        public boolean found() {
            return vertexId >= 0;
        }

        @Override
        public String toString() {
            return found()
                    ? String.format("SnapResult[v=%d, dist=%.2fm]", vertexId, distanceMeters)
                    : "SnapResult[not found]";
        }
    }
}