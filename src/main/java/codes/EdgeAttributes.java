package codes;

/**
 * Stores distance and time attributes for edges in a weighted graph.
 *
 * <p>This class maintains parallel arrays for edge attributes, indexed by edge ID.
 * It supports dynamic resizing to accommodate graphs that grow over time.</p>
 *
 * <p>Typical usage pattern:
 * <pre>
 *     codes.EdgeAttributes attrs = new codes.EdgeAttributes();
 *     // After adding an edge with ID = graph.E() - 1:
 *     attrs.setEdgeCount(graph.E());
 *     attrs.setDistanceMeters(edgeId, distance);
 *     attrs.setTimeSeconds(edgeId, time);
 * </pre>
 * </p>
 */
public class EdgeAttributes {

    /** Number of edges currently tracked (valid edgeIds are 0 to edgeCount-1). */
    private int edgeCount;

    /** Distance in meters for each edge. */
    private double[] distanceMeters;

    /** Travel time in seconds for each edge. */
    private double[] timeSeconds;

    /**
     * Initializes an empty codes.EdgeAttributes with default capacity of 4.
     */
    public EdgeAttributes() {
        this.edgeCount = 0;
        this.distanceMeters = new double[4];
        this.timeSeconds = new double[4];
    }

    /**
     * Initializes an empty codes.EdgeAttributes with the specified initial capacity.
     *
     * @param initialCapacity the initial capacity for edge storage (minimum 4)
     * @throws IllegalArgumentException if {@code initialCapacity < 0}
     */
    public EdgeAttributes(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException("initialCapacity < 0");
        int cap = Math.max(4, initialCapacity);
        this.edgeCount = 0;
        this.distanceMeters = new double[cap];
        this.timeSeconds = new double[cap];
    }

    /**
     * Returns the number of edges currently tracked.
     *
     * <p>Valid edge IDs are in the range [0, edgeCount-1].</p>
     *
     * @return the number of edges
     */
    public int edgeCount() {
        return edgeCount;
    }

    /**
     * Ensures internal arrays can accommodate at least {@code minEdgeCount} edges.
     *
     * <p>If the current capacity is insufficient, arrays are resized by doubling
     * until the required capacity is met.</p>
     *
     * @param minEdgeCount the minimum number of edges to support
     */
    public void ensureCapacity(int minEdgeCount) {
        if (minEdgeCount <= distanceMeters.length) return;

        int newCap = distanceMeters.length;
        while (newCap < minEdgeCount) newCap *= 2;

        double[] newDist = new double[newCap];
        double[] newTime = new double[newCap];

        System.arraycopy(distanceMeters, 0, newDist, 0, distanceMeters.length);
        System.arraycopy(timeSeconds, 0, newTime, 0, timeSeconds.length);

        distanceMeters = newDist;
        timeSeconds = newTime;
    }

    /**
     * Updates the valid edge count to {@code newEdgeCount}.
     *
     * <p>This expands the valid edge ID range to [0, newEdgeCount-1].
     * Typically called after adding edges to the graph to synchronize
     * the attribute storage with the graph's edge count.</p>
     *
     * @param newEdgeCount the new edge count
     * @throws IllegalArgumentException if {@code newEdgeCount < 0}
     */
    public void setEdgeCount(int newEdgeCount) {
        if (newEdgeCount < 0) throw new IllegalArgumentException("newEdgeCount < 0");
        ensureCapacity(newEdgeCount);
        this.edgeCount = newEdgeCount;
    }

    /**
     * Validates that {@code edgeId} is within the valid range.
     *
     * @param edgeId the edge ID to validate
     * @throws IllegalArgumentException if {@code edgeId < 0} or {@code edgeId >= edgeCount}
     */
    private void validateEdgeId(int edgeId) {
        if (edgeId < 0 || edgeId >= edgeCount) {
            throw new IllegalArgumentException("edgeId must be between 0 and " + (edgeCount - 1));
        }
    }

    /**
     * Sets the distance in meters for the specified edge.
     *
     * @param edgeId the edge ID
     * @param meters the distance in meters
     * @throws IllegalArgumentException if {@code edgeId} is invalid,
     *                                  {@code meters} is NaN, or {@code meters < 0}
     */
    public void setDistanceMeters(int edgeId, double meters) {
        validateEdgeId(edgeId);
        if (Double.isNaN(meters)) throw new IllegalArgumentException("distance is NaN");
        if (meters < 0.0) throw new IllegalArgumentException("distance must be non-negative");
        distanceMeters[edgeId] = meters;
    }

    /**
     * Returns the distance in meters for the specified edge.
     *
     * @param edgeId the edge ID
     * @return the distance in meters
     * @throws IllegalArgumentException if {@code edgeId} is invalid
     */
    public double distanceMeters(int edgeId) {
        validateEdgeId(edgeId);
        return distanceMeters[edgeId];
    }

    /**
     * Sets the travel time in seconds for the specified edge.
     *
     * @param edgeId  the edge ID
     * @param seconds the travel time in seconds
     * @throws IllegalArgumentException if {@code edgeId} is invalid,
     *                                  {@code seconds} is NaN, or {@code seconds < 0}
     */
    public void setTimeSeconds(int edgeId, double seconds) {
        validateEdgeId(edgeId);
        if (Double.isNaN(seconds)) throw new IllegalArgumentException("time is NaN");
        if (seconds < 0.0) throw new IllegalArgumentException("time must be non-negative");
        timeSeconds[edgeId] = seconds;
    }

    /**
     * Returns the travel time in seconds for the specified edge.
     *
     * @param edgeId the edge ID
     * @return the travel time in seconds
     * @throws IllegalArgumentException if {@code edgeId} is invalid
     */
    public double timeSeconds(int edgeId) {
        validateEdgeId(edgeId);
        return timeSeconds[edgeId];
    }
}