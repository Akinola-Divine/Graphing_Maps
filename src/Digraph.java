/**
 * A directed graph (digraph) implementation using adjacency lists.
 *
 * <p>Design inspired by Robert Sedgewick and Kevin Wayne's {@code Digraph} class
 * from <i>Algorithms, 4th Edition</i> (Addison-Wesley, 2011).</p>
 *
 * @see <a href="https://algs4.cs.princeton.edu/42digraph/">Directed Graphs - Algorithms, 4th Edition</a>
 */

public class Digraph {
    private final int V; // number of vertices
    private int E; // number of edges
    private Bag<Integer>[] adj; // adjacency list representation
    private int[] indegree; // indegree[v] = number of edges pointing to v.


    /**
     * Initializes an empty digraph with {@code V} vertices and 0 edges.
     *
     * @param V the number of vertices
     * @throws IllegalArgumentException if {@code V < 0}
     */

    @SuppressWarnings("unchecked")
    public Digraph(int V){
        if (V < 0) throw new IllegalArgumentException("Number of vertices in a Digraph cannot be less than zero");
        this.V = V;
        this.E = 0;
        indegree = new int[V];
        adj = (Bag<Integer>[]) new Bag[V];
        for (int i=0; i<V; i++)
            adj[i] = new Bag<Integer>();
    }

    /**
     * Returns the number of vertices in the digraph.
     *
     * @return the number of vertices
     */

    public int V(){
        return this.V;
    }

    /**
     * Returns the number of edges in the digraph.
     *
     * @return the number of edges
     */

    public int E(){
        return this.E;
    }

    private void validateVertex(int v){
        if (v < 0 || v >= V)   throw new IllegalArgumentException("Vertex must be between 0 and " + (V-1));
    }

    /**
     * Adds a directed edge from vertex {@code v} to vertex {@code w}.
     *
     * @param v the tail (source) vertex
     * @param w the head (destination) vertex
     * @throws IllegalArgumentException if either vertex is invalid
     */

    public void addEdge(int v, int w){
        validateVertex(v);
        validateVertex(w);
        adj[v].add(w);
        indegree[w]++;
        E++;
    }

    /**
     * Returns the number of directed edges incident to vertex {@code v}.
     * This is also known as the in-degree of the vertex.
     *
     * @param v the vertex
     * @return the indegree of vertex {@code v}
     * @throws IllegalArgumentException if {@code v} is invalid
     */

    public int indegree(int v){
        validateVertex(v);
        return indegree[v];
    }

    /**
     * Returns the number of directed edges incident from vertex {@code v}.
     * This is also known as the out-degree of the vertex.
     *
     * @param v the vertex
     * @return the outdegree of vertex {@code v}
     * @throws IllegalArgumentException if {@code v} is invalid
     */

    public int outdegree(int v){
        validateVertex(v);
        return adj[v].size();
    }


    /**
     * Returns the vertices adjacent from vertex {@code v} (i.e., endpoints
     * of edges leaving {@code v}).
     *
     * @param v the vertex
     * @return the vertices adjacent from vertex {@code v}, as an iterable
     * @throws IllegalArgumentException if {@code v} is invalid
     */

    public Iterable<Integer> adj(int v) {
        validateVertex(v);
        return adj[v];
    }


    /**
     * Returns the reverse of the digraph.
     *
     * <p>The reverse digraph has the same vertices as the original,
     * but with all edges reversed: for every edge v→w in the original,
     * the reverse contains an edge w→v.</p>
     *
     * @return the reverse of the digraph
     */

    public Digraph reverse(){
        Digraph rev = new Digraph(V);
        for (int v = 0; v <V; v++){
            for (int w: adj[v]){
                rev.addEdge(w, v);
            }
        }
        return rev;
    }


    /**
     * Returns true if there is a directed edge from vertex {@code v} to vertex {@code w}.
     *
     * @param v the source vertex
     * @param w the destination vertex
     * @return {@code true} if there is an edge from v to w; {@code false} otherwise
     * @throws IllegalArgumentException if either {@code v} or {@code w} is invalid
     */

    public boolean hasEdge(int v, int w) {
        validateVertex(v);
        validateVertex(w);
        for (int u : adj[v])
            if (u == w) return true;
        return false;
    }


    /**
     * Returns a string representation of the digraph.
     *
     * @return the number of vertices, followed by the number of edges,
     * followed by the adjacency lists
     */

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(V).append(" vertices, ").append(E).append(" edges\n");
        for (int v = 0; v < V; v++) {
            sb.append(v).append(": ");
            for (int w : adj[v])
                sb.append(w).append(" ");
            sb.append("\n");
        }
        return sb.toString();
    }
}