package codes;

import java.util.NoSuchElementException;

/**
 * A directed weighted graph implementation in which:
 * <ul>
 *     <li>Vertices are labeled {@code 0..V-1}.</li>
 *     <li>Edges have non-negative weights.</li>
 *     <li>Each edge receives a unique sequential ID assigned by the graph
 *         when added (0..E-1).</li>
 *     <li>Adjacency lists store outgoing edges.</li>
 *     <li>Indegrees are tracked explicitly.</li>
 * </ul>
 *
 * <p>
 * The graph supports efficient iteration over:
 * <ul>
 *     <li>Outgoing edges of a vertex</li>
 *     <li>All edges by sequential edge ID</li>
 * </ul>
 *
 * <p>
 * The graph also supports dynamic edge-array growth; the edge-ID mapping
 * is always valid for {@code 0 <= id < E}.
 */

public class WeightedDigraph {
    private int V;
    private int E; // number of edges
    private Bag<Edge>[] edgeAdj; // adjacency list representation
    private int[] indegree; //  indegree[v] = number of edges pointing to v
    private Edge[] edgesById; // edges mapped by ID

    /**
     * Creates an empty weighted digraph with {@code V} vertices and no edges.
     *
     * @param V number of vertices (must be ≥ 0)
     * @throws IllegalArgumentException if {@code V < 0}
     */

    @SuppressWarnings("unchecked")
    public WeightedDigraph(int V){
        if (V < 0) throw new IllegalArgumentException("Number of vertices in a codes.WeightedDigraph cannot be less than zero");
        this.V = V;
        this.E = 0;
        indegree = new int[V];
        edgeAdj = (Bag<Edge>[]) new Bag[V];
        for (int i=0; i<V; i++)
            edgeAdj[i] = new Bag<Edge>();

        this.edgesById = new Edge[Math.max(4, V)];
    }


    /**
     * Returns the number of vertices in the graph.
     */

    public int V(){
        return this.V;
    }

    /**
     * Returns the number of edges in the graph.
     */

    public int E(){
        return this.E;
    }

    /**
     * Validates that a vertex index is within the legal range {@code 0..V-1}.
     *
     * @param v vertex index
     * @throws IllegalArgumentException if {@code v} is outside the valid range
     */

    protected void validateVertex(int v){
        if (v < 0 || v >= V)   throw new IllegalArgumentException("Vertex must be between 0 and " + (V-1));
    }

    /**
     * Validates that an edge ID is within {@code 0..E-1}.
     *
     * @param e edge ID
     * @throws IllegalArgumentException if {@code e} is outside the valid range
     */

    private void validateEdge(int e) {
        if (e < 0 || e >= E) {
            throw new IllegalArgumentException("codes.Edge id must be between 0 and " + (E - 1));
        }
    }


    /**
     * Adds an edge to the graph and assigns it a unique sequential ID.
     *
     * <p>Preconditions enforced:
     * <ul>
     *     <li>codes.Edge must not be {@code null}.</li>
     *     <li>Weight must be non-negative and not NaN.</li>
     *     <li>codes.Edge must not already belong to any graph (ID must be -1).</li>
     * </ul>
     *
     * @param edge an {@link Edge} whose endpoints and weight define a directed edge
     * @throws IllegalArgumentException if edge is invalid per above conditions
     */

    public void addEdge(Edge edge) {
        if (edge == null) throw new IllegalArgumentException("edge is null");
        if (Double.isNaN(edge.weight())) throw new IllegalArgumentException("weight is NaN");
        if (edge.weight() < 0.0) throw new IllegalArgumentException("weight must be non-negative for Dijkstra-style routing");

        // Ensure this edge hasn't already been assigned to some graph
        if (edge.edgeID() != -1) {
            throw new IllegalArgumentException("edge already has an id (" + edge.edgeID() + "); graph must assign ids sequentially");
        }

        int v = edge.firstEnd();
        int w = edge.otherEnd();
        validateVertex(v);
        validateVertex(w);

        int id = E;                 // sequential id: 0..E-1
        ensureEdgeCapacity(id + 1);

        edge.setiD(id);
        edgesById[id] = edge;

        edgeAdj[v].add(edge);
        indegree[w]++;

        E++;
    }

    /**
     * Convenience method to add an edge using vertex indices and a weight.
     * A new {@link Edge} is constructed and then added through {@link #addEdge(Edge)}.
     *
     * @return the edge ID assigned to the new edge
     */

    public int addEdge(int v, int w, double weight) {
        // You can validate here or let addEdge(codes.Edge) validate via endpoints+weight.
        Edge e = new Edge(v, w, weight);
        addEdge(e);          // this assigns id, stores in edgesById, updates indegree, etc.
        return e.edgeID();   // now guaranteed to be the sequential id
    }

    /**
     * Returns the number of incoming edges to vertex {@code v}.
     *
     * @param v vertex
     * @return indegree of {@code v}
     * @throws IllegalArgumentException if vertex index is invalid
     */

    public int indegree(int v){
        validateVertex(v);
        return indegree[v];
    }


    /**
     * Returns the number of outgoing edges from vertex {@code v}.
     *
     * @param v vertex
     * @return outdegree of {@code v}
     * @throws IllegalArgumentException if vertex index is invalid
     */

    public int outdegree(int v){
        validateVertex(v);
        return edgeAdj[v].size();
    }

    /**
     * Returns an iterable over all outgoing edges of vertex {@code v}.
     *
     * @param v vertex
     * @return iterable over outgoing edges
     * @throws IllegalArgumentException if vertex index is invalid
     */

    public Iterable<Edge> outEdges(int v){
        validateVertex(v);
        return edgeAdj[v];
    }

    /**
     * Retrieves an edge by its globally assigned ID.
     *
     * @param edgeId the ID of the edge
     * @return the corresponding {@code codes.Edge} object
     * @throws IllegalArgumentException if the ID is out of range
     */

    public Edge edgeByID(int edgeId){
        validateEdge(edgeId);
        return edgesById[edgeId];
    }

    /**
     * Returns a new {@code codes.WeightedDigraph} representing the reverse of this graph:
     * each edge {@code v -> w} becomes {@code w -> v} with the same weight.
     *
     * @return reversed digraph
     */

    public WeightedDigraph reverse(){
        WeightedDigraph wd = new WeightedDigraph(V);
        for (int v = 0; v <V; v++){
            for (Edge e : edgeAdj[v]) {
                wd.addEdge(e.otherEnd(), e.firstEnd(), e.weight());
            }
        }
        return wd;
    }

    /**
     * Ensures the internal {@code edgesById} array has capacity for at least
     * {@code minCapacity} edges. Grows using powers of two.
     *
     * @param minCapacity required minimum capacity
     */

    private void ensureEdgeCapacity(int minCapacity) {
        if (minCapacity <= edgesById.length) return;

        int newCap = edgesById.length;
        while (newCap < minCapacity) newCap *= 2;

        Edge[] next = new Edge[newCap];
        System.arraycopy(edgesById, 0, next, 0, edgesById.length);
        edgesById = next;
    }

    public Iterable<Edge> edges() {
        return () -> new java.util.Iterator<>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < E;
            }

            @Override
            public Edge next() {
                if (!hasNext()) throw new NoSuchElementException();
                return edgesById[i++];
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(V).append(" vertices, ").append(E).append(" edges\n");
        for (int v = 0; v < V; v++) {
            sb.append(v).append(": ");
            for (Edge e : edgeAdj[v]) {
                sb.append(e.edgeID()).append("(")
                        .append(e.otherEnd()).append(", ")
                        .append(e.weight()).append(") ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
