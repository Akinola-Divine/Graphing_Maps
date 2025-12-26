package codes;

/**
 * A directed, weighted edge used by {@link WeightedDigraph}.
 *
 * <p>An {@code codes.Edge} represents a directed connection from vertex {@code v}
 * to vertex {@code w} with a non-negative weight. Edges are immutable
 * with respect to endpoints and weight, but the graph assigns each edge
 * a unique sequential ID when the edge is inserted.
 *
 * <h3>codes.Edge ID rules</h3>
 * <ul>
 *     <li>Newly constructed edges have ID {@code -1}.</li>
 *     <li>The ID is set exactly once by {@link WeightedDigraph#addEdge(Edge)}
 *         via {@link #setiD(int)}.</li>
 *     <li>User code should <strong>never</strong> call {@code setiD} directly.</li>
 * </ul>
 */

public class Edge {
    private final double weight;
    private int v;
    private int w;
    private int iD;

    /**
     * Creates a directed edge {@code v -> w} with the given weight.
     *
     * @param v source vertex index (must be ≥ 0)
     * @param w destination vertex index (must be ≥ 0)
     * @param weight non-negative edge weight (must not be NaN)
     * @throws IllegalArgumentException if {@code v < 0}, {@code w < 0},
     *                                  or weight is NaN
     */

    public Edge(int v, int w, double weight){
        if (v < 0) throw new IllegalArgumentException("vertex index must be a non-negative integer");
        if (w < 0) throw new IllegalArgumentException("vertex index must be a non-negative integer");
        if (Double.isNaN(weight)) throw new IllegalArgumentException("Weight is NaN");

        this.weight = weight;
        this.v = v;
        this.w = w;
        this.iD = -1;
    }

    /**
     * Assigns a unique ID to this edge. Used only by {@link WeightedDigraph}.
     *
     * <p>Preconditions:
     * <ul>
     *     <li>ID must be non-negative</li>
     *     <li>codes.Edge must not already have an ID</li>
     * </ul>
     *
     * @param iD unique edge ID
     * @throws IllegalArgumentException if {@code iD < 0}
     * @throws IllegalStateException if this edge already has an ID
     */

    public void setiD(int iD){
        if (iD < 0) throw new IllegalArgumentException("edge id must be non-negative");
        if (this.iD != -1) {
            throw new IllegalStateException("edge id already set to " + this.iD);
        }
        this.iD = iD;
    }

    /**
     * Returns the weight of this edge.
     *
     * @return edge weight
     */

    public double weight() {
        return weight;
    }

    /**
     * Returns the source (tail) vertex of this directed edge.
     *
     * @return vertex {@code v}
     */

    public int firstEnd(){
        return v;
    }

    /**
     * Returns the unique ID assigned by the owning graph.
     *
     * @return edge ID, or {@code -1} if not yet added to a graph
     */

    public int edgeID(){
        return iD;
    }

    /**
     * Returns the destination (head) vertex of this directed edge.
     *
     * @return vertex {@code w}
     */

    public int otherEnd(){
        return w;
    }
}
