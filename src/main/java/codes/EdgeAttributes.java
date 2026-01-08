package codes;

/**
 * Stores distance, time, and street name attributes for edges in a weighted graph.
 *
 * <p>This class maintains parallel arrays for edge attributes, indexed by edge ID.
 * It supports dynamic resizing to accommodate graphs that grow over time.</p>
 *
 * <p>Typical usage pattern:
 * <pre>
 *     EdgeAttributes attrs = new EdgeAttributes();
 *     // After adding an edge with ID = graph.E() - 1:
 *     attrs.setEdgeCount(graph.E());
 *     attrs.setDistanceMeters(edgeId, distance);
 *     attrs.setTimeSeconds(edgeId, time);
 *     attrs.setStreetName(edgeId, "Main Street");
 * </pre>
 * </p>
 *
 * @see RoutingEngine uses edge attributes for path cost computation
 * @see InstructionGenerator uses street names for turn-by-turn directions
 */
public class EdgeAttributes {

    /** Number of edges currently tracked (valid edgeIds are 0 to edgeCount-1). */
    private int edgeCount;

    /** Distance in meters for each edge. */
    private double[] distanceMeters;

    /** Travel time in seconds for each edge. */
    private double[] timeSeconds;

    /** Street name for each edge (may be null for unnamed roads). */
    private String[] streetName;

    /**
     * Initializes an empty EdgeAttributes with default capacity of 4.
     */
    public EdgeAttributes() {
        this.edgeCount = 0;
        this.distanceMeters = new double[4];
        this.timeSeconds = new double[4];
        this.streetName = new String[4];
    }

    /**
     * Initializes an empty EdgeAttributes with the specified initial capacity.
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
        this.streetName = new String[cap];
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
        String[] newStreet = new String[newCap];

        System.arraycopy(distanceMeters, 0, newDist, 0, distanceMeters.length);
        System.arraycopy(timeSeconds, 0, newTime, 0, timeSeconds.length);
        System.arraycopy(streetName, 0, newStreet, 0, streetName.length);

        distanceMeters = newDist;
        timeSeconds = newTime;
        streetName = newStreet;
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
     * Sets the street name for the specified edge.
     *
     * <p>Street names are used by {@link InstructionGenerator} to produce
     * human-readable turn-by-turn directions.</p>
     *
     * @param edgeId the edge ID
     * @param name   the street name (may be null for unnamed roads)
     * @throws IllegalArgumentException if {@code edgeId} is invalid
     */
    public void setStreetName(int edgeId, String name) {
        validateEdgeId(edgeId);
        streetName[edgeId] = name;
    }

    /**
     * Returns the street name for the specified edge.
     *
     * @param edgeId the edge ID
     * @return the street name, or {@code null} if unnamed
     * @throws IllegalArgumentException if {@code edgeId} is invalid
     */
    public String streetName(int edgeId) {
        validateEdgeId(edgeId);
        return streetName[edgeId];
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

    /**
     * Returns a string summary for debugging.
     *
     * @return summary with edge count
     */
    @Override
    public String toString() {
        return String.format("EdgeAttributes[%d edges]", edgeCount);
    }
}