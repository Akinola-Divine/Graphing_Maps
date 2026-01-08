package codes;

/**
 * Stores polyline geometry for all edges in a graph using Compressed Sparse Row (CSR) format.
 *
 * <p>Each edge's geometry is a sequence of (x, y) coordinate pairs representing
 * the road's shape. Points are stored in flat arrays, with an index array
 * indicating where each edge's points begin and end.</p>
 *
 * <p>Storage layout:
 * <pre>
 *     edgeStart: [0, 3, 7, 10, ...]
 *                 │  │  │
 *                 │  │  └─ edge 2 starts at index 10
 *                 │  └──── edge 1 starts at index 7 (edge 0 ends at 7)
 *                 └─────── edge 0 starts at index 0
 *
 *     x: [x0, x1, x2, x3, x4, x5, x6, x7, x8, x9, ...]
 *     y: [y0, y1, y2, y3, y4, y5, y6, y7, y8, y9, ...]
 *         └────────┘  └──────────────┘  └─────────...
 *          edge 0         edge 1          edge 2
 * </pre>
 * </p>
 *
 * <p>Example usage:
 * <pre>
 *     EdgeGeometry geom = new EdgeGeometry(edgeStart, x, y);
 *
 *     // Iterate over points in edge 5
 *     int start = geom.startIndex(5);
 *     int end = geom.endIndex(5);
 *     for (int i = start; i &lt; end; i++) {
 *         double px = geom.x(i);
 *         double py = geom.y(i);
 *     }
 * </pre>
 * </p>
 *
 * <p>Memory efficiency: O(totalPoints) for coordinates + O(edges) for index array,
 * compared to O(edges × avgPointsPerEdge) for array-of-arrays storage.</p>
 *
 * @see SegmentSnapper uses edge geometry for map matching
 * @see Reconstruction uses edge geometry for route visualization
 */
public class EdgeGeometry {

    /**
     * CSR row pointers: points for edge {@code e} are at indices
     * {@code edgeStart[e]} to {@code edgeStart[e+1] - 1}.
     * Length is {@code edgeCount + 1}.
     */
    private int[] edgeStart;

    /**
     * X-coordinates of all geometry points.
     * For geographic data, this typically stores longitude (degrees) or easting (meters).
     */
    protected double[] x;

    /**
     * Y-coordinates of all geometry points.
     * For geographic data, this typically stores latitude (degrees) or northing (meters).
     */
    protected double[] y;

    /**
     * Constructs an edge geometry store from CSR-format arrays.
     *
     * <p>The {@code edgeStart} array must have length {@code edgeCount + 1},
     * where {@code edgeStart[e]} is the starting index of edge {@code e}'s points
     * and {@code edgeStart[edgeCount]} equals {@code x.length}.</p>
     *
     * @param edgeStart CSR row pointers (length = edgeCount + 1)
     * @param x         x-coordinates of all points
     * @param y         y-coordinates of all points
     * @throws IllegalArgumentException if any array is null, x/y lengths differ,
     *                                  or edgeStart is empty
     */
    public EdgeGeometry(int[] edgeStart, double[] x, double[] y) {
        if (edgeStart == null || x == null || y == null)
            throw new IllegalArgumentException("null arrays");
        if (x.length != y.length)
            throw new IllegalArgumentException("x/y length mismatch");
        if (edgeStart.length == 0)
            throw new IllegalArgumentException("edgeStart empty");

        this.edgeStart = edgeStart;
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the CSR row pointer array.
     *
     * <p>Useful for creating a copy of this geometry or for bulk operations.</p>
     *
     * @return the edgeStart array (not a copy)
     */
    public int[] edgeStart() {
        return edgeStart;
    }

    /**
     * Returns the number of edges with geometry.
     *
     * @return the edge count
     */
    public int edgeCount() {
        return edgeStart.length - 1;
    }

    /**
     * Returns the starting index (inclusive) for an edge's points.
     *
     * @param edgeId the edge ID
     * @return the first index in x/y arrays for this edge
     * @throws ArrayIndexOutOfBoundsException if edgeId is invalid
     */
    public int startIndex(int edgeId) {
        return edgeStart[edgeId];
    }

    /**
     * Returns the ending index (exclusive) for an edge's points.
     *
     * <p>The number of points for edge {@code e} is
     * {@code endIndex(e) - startIndex(e)}.</p>
     *
     * @param edgeId the edge ID
     * @return the index after the last point for this edge
     * @throws ArrayIndexOutOfBoundsException if edgeId is invalid
     */
    public int endIndex(int edgeId) {
        return edgeStart[edgeId + 1];
    }

    /**
     * Returns the x-coordinate at the given index.
     *
     * @param idx the point index
     * @return the x-coordinate
     * @throws ArrayIndexOutOfBoundsException if idx is out of range
     */
    public double x(int idx) {
        return x[idx];
    }

    /**
     * Returns the y-coordinate at the given index.
     *
     * @param idx the point index
     * @return the y-coordinate
     * @throws ArrayIndexOutOfBoundsException if idx is out of range
     */
    public double y(int idx) {
        return y[idx];
    }

    /**
     * Returns the total number of geometry points across all edges.
     *
     * @return the total point count
     */
    public int size() {
        return x.length;
    }

    /**
     * Returns the number of points in the specified edge's geometry.
     *
     * @param edgeId the edge ID
     * @return the point count for this edge
     * @throws ArrayIndexOutOfBoundsException if edgeId is invalid
     */
    public int pointCount(int edgeId) {
        return endIndex(edgeId) - startIndex(edgeId);
    }

    /**
     * Returns a string summary for debugging.
     *
     * @return summary with edge count and total points
     */
    @Override
    public String toString() {
        return String.format("EdgeGeometry[%d edges, %d points]",
                edgeCount(), size());
    }
}