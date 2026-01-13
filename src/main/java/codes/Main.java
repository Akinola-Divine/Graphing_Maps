package codes;

import org.eclipse.collections.impl.map.mutable.primitive.LongIntHashMap;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.FloatArray;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Main entry point for the routing application.
 *
 * <p>Compiles OpenStreetMap (OSM) data into a routable graph structure using
 * the {@link OSMCompiler} class.</p>
 */
public class Main {

    /**
     * Application entry point.
     *
     * @param args command-line arguments (currently ignored)
     */
    public static void main(String[] args) {
        Path osmFile = Path.of("data/pei.osm");

        OSMCompiler compiler = new OSMCompiler();
        OSMCompiler.BuildResult result = compiler.compile(osmFile);

        System.out.println("V=" + result.graph.V() + " E=" + result.graph.E());
        System.out.println("attrs.edgeCount=" + result.attrs.edgeCount());
        System.out.println("vertexStore.V=" + result.vertexStore.V());
    }

    /**
     * Compiles OpenStreetMap XML files into a weighted directed graph for routing.
     *
     * <p>The compilation process uses three passes over the OSM file:
     * <ol>
     *   <li><b>Pass 1:</b> Read all {@code <node>} elements and store lat/lon coordinates</li>
     *   <li><b>Pass 2:</b> Read all {@code <way>} elements to identify routing vertices
     *       (nodes that are endpoints or shared by multiple roads)</li>
     *   <li><b>Pass 3:</b> Read ways again to emit edges, compute distances, and
     *       build edge geometry</li>
     * </ol>
     * </p>
     *
     * <p>The result is a compact graph where:
     * <ul>
     *   <li>Vertices are road intersections and endpoints</li>
     *   <li>Edges represent road segments between vertices</li>
     *   <li>Edge attributes include distance, travel time, and street names</li>
     *   <li>Edge geometry preserves the original road polyline shape</li>
     * </ul>
     * </p>
     *
     * <p>Example usage:
     * <pre>
     *     OSMCompiler compiler = new OSMCompiler();
     *     BuildResult result = compiler.compile(Path.of("map.osm"));
     *     WeightedDigraph graph = result.graph;
     * </pre>
     * </p>
     */
    public static final class OSMCompiler {

        // ========= Output types =========

        /**
         * Stores latitude/longitude coordinates for all routing vertices.
         *
         * <p>Parallel arrays where {@code lat[v]} and {@code lon[v]} are the
         * geographic coordinates of vertex {@code v}.</p>
         */
        static final class LatLonVertexStore {

            /** Latitude of each vertex (degrees, WGS84). */
            final double[] lat;

            /** Longitude of each vertex (degrees, WGS84). */
            final double[] lon;

            /**
             * Constructs a vertex store from coordinate arrays.
             *
             * @param lat latitude array
             * @param lon longitude array
             * @throws IllegalArgumentException if arrays are null or have different lengths
             */
            LatLonVertexStore(double[] lat, double[] lon) {
                if (lat == null || lon == null) throw new IllegalArgumentException("lat/lon arrays cannot be null");
                if (lat.length != lon.length) throw new IllegalArgumentException("lat/lon must have same length");
                this.lat = lat;
                this.lon = lon;
            }

            /**
             * Returns the number of vertices.
             *
             * @return vertex count
             */
            int V() { return lat.length; }
        }

        /**
         * The complete result of OSM compilation.
         *
         * <p>Contains all data structures needed for routing:
         * <ul>
         *   <li>The graph topology (vertices and directed edges)</li>
         *   <li>Edge attributes (distance, time, names)</li>
         *   <li>Vertex coordinates for A* heuristics</li>
         *   <li>Edge geometry for visualization and snapping</li>
         * </ul>
         * </p>
         */
        static final class BuildResult {

            /** The routing graph. */
            final WeightedDigraph graph;

            /** Edge attributes (distance, time, street names). */
            final EdgeAttributes attrs;

            /** Geographic coordinates of all vertices. */
            final LatLonVertexStore vertexStore;

            /** Polyline geometry for each edge. */
            final EdgeGeometry edgeGeometry;

            /**
             * Constructs a build result.
             *
             * @param graph        the routing graph
             * @param attrs        edge attributes
             * @param vertexStore  vertex coordinates
             * @param edgeGeometry edge polylines
             */
            BuildResult(WeightedDigraph graph,
                        EdgeAttributes attrs,
                        LatLonVertexStore vertexStore,
                        EdgeGeometry edgeGeometry) {
                this.graph = graph;
                this.attrs = attrs;
                this.vertexStore = vertexStore;
                this.edgeGeometry = edgeGeometry;
            }
        }

        // ========= Pass 1 data =========

        /**
         * Temporary storage for all OSM nodes during compilation.
         *
         * <p>Maps OSM node IDs (64-bit) to dense indices, and stores
         * coordinates in parallel arrays for cache-friendly access.</p>
         */
        private static final class NodeStore {

            /** Number of nodes stored. */
            int size;

            /** Latitude of each node. */
            double[] lat;

            /** Longitude of each node. */
            double[] lon;

            /** Maps OSM node ID → dense node index. */
            final LongIntHashMap idToIndex;

            /**
             * Constructs a node store with initial capacity.
             *
             * @param initCapacity expected number of nodes
             * @throws IllegalArgumentException if {@code initCapacity < 0}
             */
            NodeStore(int initCapacity) {
                if (initCapacity < 0) throw new IllegalArgumentException("initCapacity < 0");
                int cap = Math.max(4, initCapacity);
                this.size = 0;
                this.lat = new double[cap];
                this.lon = new double[cap];
                this.idToIndex = new LongIntHashMap(cap);
            }

            /**
             * Returns the number of nodes stored.
             *
             * @return node count
             */
            int size() { return size; }

            /**
             * Adds a node to the store.
             *
             * @param osmNodeId the OSM node ID
             * @param nodeLat   latitude (degrees)
             * @param nodeLon   longitude (degrees)
             * @throws IllegalArgumentException if the node ID already exists
             */
            void addNode(long osmNodeId, double nodeLat, double nodeLon) {
                if (idToIndex.containsKey(osmNodeId)) {
                    throw new IllegalArgumentException("Duplicate OSM node id encountered: " + osmNodeId);
                }

                // Grow arrays if needed
                if (lat.length == size) {
                    lat = Arrays.copyOf(lat, lat.length * 2);
                    lon = Arrays.copyOf(lon, lon.length * 2);
                }

                lat[size] = nodeLat;
                lon[size] = nodeLon;
                idToIndex.put(osmNodeId, size);
                size++;
            }

            /**
             * Returns the dense index for an OSM node ID.
             *
             * @param osmNodeId the OSM node ID
             * @return the dense index
             * @throws IllegalArgumentException if the node ID is not found
             */
            int nodeIndexOf(long osmNodeId) {
                if (!idToIndex.containsKey(osmNodeId)) {
                    throw new IllegalArgumentException("Missing node id referenced by way: " + osmNodeId);
                }
                return idToIndex.get(osmNodeId);
            }
        }

        // ========= Pass 2 data =========

        /**
         * Tracks which nodes should become routing vertices.
         *
         * <p>A node becomes a routing vertex if:
         * <ul>
         *   <li>It is an endpoint of any road (first or last node of a way)</li>
         *   <li>It is used by two or more roads (intersection)</li>
         * </ul>
         * </p>
         */
        private static final class VertexSignals {

            /** Number of roads using each node. */
            final int[] useCount;

            /** Whether each node is a road endpoint. */
            final boolean[] isEndpoint;

            /**
             * Constructs signals for the given number of nodes.
             *
             * @param nodeCount total number of nodes
             */
            VertexSignals(int nodeCount) {
                this.useCount = new int[nodeCount];
                this.isEndpoint = new boolean[nodeCount];
            }
        }

        // ========= Between pass 2 and 3 =========

        /**
         * Maps node indices to routing vertex IDs.
         *
         * <p>Only nodes that are endpoints or intersections become vertices.
         * Other nodes are intermediate points on edges (preserved in geometry).</p>
         */
        private static final class VertexMapping {

            /**
             * Maps node index → vertex ID.
             * Value is -1 if the node is not a routing vertex.
             */
            final int[] nodeIndexToVertexId;

            /** Latitude of each routing vertex. */
            final double[] vertexLat;

            /** Longitude of each routing vertex. */
            final double[] vertexLon;

            /**
             * Constructs a vertex mapping.
             *
             * @param nodeIndexToVertexId mapping array (-1 for non-vertices)
             * @param vertexLat           vertex latitudes
             * @param vertexLon           vertex longitudes
             */
            VertexMapping(int[] nodeIndexToVertexId, double[] vertexLat, double[] vertexLon) {
                this.nodeIndexToVertexId = nodeIndexToVertexId;
                this.vertexLat = vertexLat;
                this.vertexLon = vertexLon;
            }

            /**
             * Returns the number of routing vertices.
             *
             * @return vertex count
             */
            int V() { return vertexLat.length; }
        }

        // ========= Public entry =========

        /**
         * Compiles an OSM file into a routable graph.
         *
         * @param osmFile path to the OSM XML file
         * @return the complete build result
         * @throws RuntimeException if parsing fails
         */
        public BuildResult compile(Path osmFile) {
            NodeStore ns = pass1_readNodes(osmFile);
            VertexSignals sig = pass2_countRoadUsage(osmFile, ns);
            VertexMapping vm = buildVertexMapping(ns, sig);
            return pass3_buildEdges(osmFile, ns, vm);
        }

        // ========= Pass 1: read nodes =========

        /**
         * Pass 1: Reads all {@code <node>} elements from the OSM file.
         *
         * <p>Stores node IDs, latitudes, and longitudes for later reference
         * when processing ways.</p>
         *
         * @param osmFile path to the OSM file
         * @return the populated node store
         * @throws RuntimeException if parsing fails
         */
        private NodeStore pass1_readNodes(Path osmFile) {
            NodeStore ns = new NodeStore(1 << 20);  // ~1M initial capacity

            try {
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(false);
                SAXParser parser = factory.newSAXParser();

                DefaultHandler handler = new DefaultHandler() {
                    @Override
                    public void startElement(String uri, String localName, String qName, Attributes atts) {
                        if (!"node".equals(qName)) return;

                        long id = Long.parseLong(atts.getValue("id"));
                        double lat = Double.parseDouble(atts.getValue("lat"));
                        double lon = Double.parseDouble(atts.getValue("lon"));

                        ns.addNode(id, lat, lon);
                    }
                };

                parser.parse(osmFile.toFile(), handler);
                return ns;

            } catch (Exception e) {
                throw new RuntimeException("Pass 1 failed: " + e.getMessage(), e);
            }
        }

        /**
         * Checks if a highway tag value represents a routable road.
         *
         * <p>Includes major roads (motorway through tertiary), residential streets,
         * and link roads. Excludes footways, cycleways, paths, etc.</p>
         *
         * @param highway the highway tag value
         * @return {@code true} if routable; {@code false} otherwise
         */
        private static boolean isRoutableHighway(String highway) {
            if (highway == null) return false;
            return switch (highway) {
                case "motorway", "trunk", "primary", "secondary", "tertiary",
                     "unclassified", "residential", "living_street", "service",
                     "motorway_link", "trunk_link", "primary_link",
                     "secondary_link", "tertiary_link" -> true;
                default -> false;
            };
        }

        /**
         * A growable buffer for OSM node references within a way.
         *
         * <p>Avoids object allocation during parsing by reusing a primitive array.</p>
         */
        private static final class LongRefBuffer {
            long[] a = new long[16];
            int size = 0;

            /** Clears the buffer for reuse. */
            void clear() { size = 0; }

            /**
             * Adds a value to the buffer.
             *
             * @param x the value to add
             */
            void add(long x) {
                if (size == a.length) {
                    long[] b = new long[a.length * 2];
                    System.arraycopy(a, 0, b, 0, a.length);
                    a = b;
                }
                a[size++] = x;
            }
        }

        // ========= Pass 2: read ways (roads) and count usage =========

        /**
         * Pass 2: Counts road usage to identify routing vertices.
         *
         * <p>Scans all ways and marks nodes as:
         * <ul>
         *   <li>Endpoints if they are the first or last node of a routable way</li>
         *   <li>Intersections if they appear in multiple ways (useCount &ge; 2)</li>
         * </ul>
         * </p>
         *
         * @param osmFile path to the OSM file
         * @param ns      the node store from pass 1
         * @return vertex signals indicating which nodes become vertices
         * @throws RuntimeException if parsing fails
         */
        private VertexSignals pass2_countRoadUsage(Path osmFile, NodeStore ns) {
            VertexSignals sig = new VertexSignals(ns.size());

            try {
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(false);
                SAXParser parser = factory.newSAXParser();

                DefaultHandler handler = new DefaultHandler() {
                    boolean inWay = false;
                    final LongRefBuffer refs = new LongRefBuffer();
                    String highway = null;

                    @Override
                    public void startElement(String uri, String localName, String qName, Attributes atts) {
                        if ("way".equals(qName)) {
                            inWay = true;
                            refs.clear();
                            highway = null;
                            return;
                        }
                        if (!inWay) return;

                        if ("nd".equals(qName)) {
                            long ref = Long.parseLong(atts.getValue("ref"));
                            refs.add(ref);
                        } else if ("tag".equals(qName)) {
                            String k = atts.getValue("k");
                            String v = atts.getValue("v");
                            if ("highway".equals(k)) highway = v;
                        }
                    }

                    @Override
                    public void endElement(String uri, String localName, String qName) {
                        if (!"way".equals(qName)) return;
                        inWay = false;

                        if (!isRoutableHighway(highway)) return;
                        if (refs.size < 2) return;

                        // Mark first and last nodes as endpoints
                        int firstIdx = ns.nodeIndexOf(refs.a[0]);
                        int lastIdx  = ns.nodeIndexOf(refs.a[refs.size - 1]);
                        sig.isEndpoint[firstIdx] = true;
                        sig.isEndpoint[lastIdx] = true;

                        // Increment use count for all nodes in this way
                        for (int i = 0; i < refs.size; i++) {
                            int nodeIdx = ns.nodeIndexOf(refs.a[i]);
                            sig.useCount[nodeIdx]++;
                        }
                    }
                };

                parser.parse(osmFile.toFile(), handler);
                return sig;

            } catch (Exception e) {
                throw new RuntimeException("Pass 2 failed: " + e.getMessage(), e);
            }
        }

        // ========= Build vertex mapping =========

        /**
         * Builds the mapping from node indices to routing vertex IDs.
         *
         * <p>Only nodes that are endpoints or intersections (useCount &ge; 2)
         * become routing vertices. Returns dense vertex arrays.</p>
         *
         * @param ns  the node store
         * @param sig vertex signals from pass 2
         * @return the vertex mapping
         */
        private VertexMapping buildVertexMapping(NodeStore ns, VertexSignals sig) {
            int n = ns.size();

            int[] nodeIndexToVertexId = new int[n];
            Arrays.fill(nodeIndexToVertexId, -1);

            // Count vertices
            int vertexCount = 0;
            for (int nodeIndex = 0; nodeIndex < n; nodeIndex++) {
                if (sig.isEndpoint[nodeIndex] || sig.useCount[nodeIndex] >= 2) vertexCount++;
            }

            // Allocate vertex coordinate arrays
            double[] vLat = new double[vertexCount];
            double[] vLon = new double[vertexCount];

            // Assign vertex IDs and copy coordinates
            int vid = 0;
            for (int nodeIndex = 0; nodeIndex < n; nodeIndex++) {
                if (sig.isEndpoint[nodeIndex] || sig.useCount[nodeIndex] >= 2) {
                    nodeIndexToVertexId[nodeIndex] = vid;
                    vLat[vid] = ns.lat[nodeIndex];
                    vLon[vid] = ns.lon[nodeIndex];
                    vid++;
                }
            }

            return new VertexMapping(nodeIndexToVertexId, vLat, vLon);
        }

        /**
         * Parses the OSM oneway tag value.
         *
         * @param onewayValue the tag value (may be null)
         * @return 1 for forward-only, -1 for reverse-only, 0 for bidirectional
         */
        private static int parseOnewayDirection(String onewayValue) {
            if (onewayValue == null) return 0;
            return switch (onewayValue) {
                case "yes", "true", "1" -> 1;
                case "-1" -> -1;
                default -> 0;
            };
        }

        /**
         * Emits edges for a road segment between two routing vertices.
         *
         * <p>Creates one or two directed edges depending on the oneway direction:
         * <ul>
         *   <li>{@code onewayDir = 1}: forward edge only (fromV → toV)</li>
         *   <li>{@code onewayDir = -1}: reverse edge only (toV → fromV)</li>
         *   <li>{@code onewayDir = 0}: both directions</li>
         * </ul>
         * </p>
         *
         * @param G          the graph to add edges to
         * @param attrs      edge attributes storage
         * @param fromV      source vertex ID
         * @param toV        destination vertex ID
         * @param distMeters edge distance in meters
         * @param onewayDir  oneway direction code
         * @param name       street name (may be null)
         */
        private static void emitSegmentEdges(WeightedDigraph G,
                                             EdgeAttributes attrs,
                                             int fromV,
                                             int toV,
                                             double distMeters,
                                             int onewayDir,
                                             String name) {
            if (fromV == toV) return;
            if (Double.isNaN(distMeters) || distMeters < 0.0) return;

            if (onewayDir == 1) {
                int id = G.addEdge(fromV, toV, 0.0);
                attrs.setEdgeCount(G.E());
                attrs.setDistanceMeters(id, distMeters);
                attrs.setStreetName(id, name);
            } else if (onewayDir == -1) {
                int id = G.addEdge(toV, fromV, 0.0);
                attrs.setEdgeCount(G.E());
                attrs.setDistanceMeters(id, distMeters);
                attrs.setStreetName(id, name);
            } else {
                // Bidirectional: create both edges
                int id1 = G.addEdge(fromV, toV, 0.0);
                attrs.setEdgeCount(G.E());
                attrs.setDistanceMeters(id1, distMeters);
                attrs.setStreetName(id1, name);

                int id2 = G.addEdge(toV, fromV, 0.0);
                attrs.setEdgeCount(G.E());
                attrs.setDistanceMeters(id2, distMeters);
                attrs.setStreetName(id2, name);
            }
        }

        // ========= Pass 3: read ways again and emit edges + attributes =========

        /**
         * Pass 3: Builds edges and geometry from routable ways.
         *
         * <p>For each routable way:
         * <ol>
         *   <li>Iterates through nodes, accumulating distance</li>
         *   <li>When a routing vertex is encountered, emits edge(s)</li>
         *   <li>Stores the polyline geometry for each edge</li>
         *   <li>Handles bidirectional roads by emitting reverse geometry</li>
         * </ol>
         * </p>
         *
         * @param osmFile path to the OSM file
         * @param ns      node store from pass 1
         * @param vm      vertex mapping from pass 2
         * @return the complete build result
         * @throws RuntimeException if parsing fails
         */
        private BuildResult pass3_buildEdges(Path osmFile, NodeStore ns, VertexMapping vm) {

            IntArray edgeStart = new IntArray();
            FloatArray geomX = new FloatArray();
            FloatArray geomY = new FloatArray();

            // edgeStart must start with 0, and then add one entry per emitted edge
            edgeStart.add(0);

            WeightedDigraph G = new WeightedDigraph(vm.V());
            EdgeAttributes attrs = new EdgeAttributes();

            try {
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(false);
                SAXParser parser = factory.newSAXParser();

                DefaultHandler handler = new DefaultHandler() {

                    boolean inWay = false;
                    final LongRefBuffer refs = new LongRefBuffer();
                    String highway = null;
                    String oneway = null;
                    String name = null;

                    @Override
                    public void startElement(String uri, String localName, String qName, Attributes atts) {
                        if ("way".equals(qName)) {
                            inWay = true;
                            refs.clear();
                            highway = null;
                            oneway = null;
                            name = null;
                            return;
                        }
                        if (!inWay) return;

                        if ("nd".equals(qName)) {
                            long ref = Long.parseLong(atts.getValue("ref"));
                            refs.add(ref);
                        } else if ("tag".equals(qName)) {
                            String k = atts.getValue("k");
                            String v = atts.getValue("v");
                            if ("highway".equals(k)) highway = v;
                            else if ("oneway".equals(k)) oneway = v;
                            else if ("name".equals(k)) name = v;
                        }
                    }

                    @Override
                    public void endElement(String uri, String localName, String qName) {
                        if (!"way".equals(qName)) return;
                        inWay = false;

                        if (!isRoutableHighway(highway)) return;
                        if (refs.size < 2) return;

                        int onewayDir = parseOnewayDirection(oneway);

                        int startVertexId = -1;
                        int prevNodeIndex = -1;
                        double accum = 0.0;

                        // current segment geometry (lon/lat order stored as x/y)
                        FloatArray segX = new FloatArray();
                        FloatArray segY = new FloatArray();

                        for (int i = 0; i < refs.size; i++) {
                            int nodeIndex = ns.nodeIndexOf(refs.a[i]);
                            int vertexId = vm.nodeIndexToVertexId[nodeIndex];

                            // Find first routing vertex of this way
                            if (startVertexId == -1) {
                                if (vertexId != -1) {
                                    startVertexId = vertexId;
                                    prevNodeIndex = nodeIndex;
                                    accum = 0.0;
                                    segX.clear(); segY.clear();
                                    segX.add((float) ns.lon[nodeIndex]);
                                    segY.add((float) ns.lat[nodeIndex]);
                                }
                                continue;
                            }

                            // accumulate distance from prev node -> current node
                            accum += haversineMeters(
                                    ns.lat[prevNodeIndex], ns.lon[prevNodeIndex],
                                    ns.lat[nodeIndex],     ns.lon[nodeIndex]
                            );
                            prevNodeIndex = nodeIndex;

                            // add current point to segment geometry
                            segX.add((float) ns.lon[nodeIndex]);
                            segY.add((float) ns.lat[nodeIndex]);

                            // if we reached a routing vertex, emit edges and geometry
                            if (vertexId != -1) {

                                // avoid degenerate "segment" that starts and ends at same vertex
                                if (vertexId == startVertexId) {
                                    // restart segment from here
                                    segX.clear(); segY.clear();
                                    segX.add((float) ns.lon[nodeIndex]);
                                    segY.add((float) ns.lat[nodeIndex]);
                                    startVertexId = vertexId;
                                    accum = 0.0;
                                    continue;
                                }

                                int before = G.E();
                                emitSegmentEdges(G, attrs, startVertexId, vertexId, accum, onewayDir, name);
                                int after = G.E();

                                // For each emitted directed edge, append geometry in the correct direction
                                // relative to the way direction (startVertexId -> vertexId).
                                for (int eid = before; eid < after; eid++) {
                                    var ed = G.edgeByID(eid);

                                    int from = ed.firstEnd();
                                    int to   = ed.otherEnd();

                                    boolean matchesWayDirection = (from == startVertexId && to == vertexId);
                                    boolean reverse = !matchesWayDirection;

                                    appendGeometry(geomX, geomY, segX, segY, reverse);
                                    edgeStart.add(geomX.size);
                                }

                                // restart new segment from this vertex
                                segX.clear();
                                segY.clear();
                                segX.add((float) ns.lon[nodeIndex]);
                                segY.add((float) ns.lat[nodeIndex]);

                                startVertexId = vertexId;
                                accum = 0.0;
                            }
                        }
                    }
                };

                parser.parse(osmFile.toFile(), handler);

            } catch (Exception e) {
                throw new RuntimeException("Pass 3 failed", e);
            }

            // Sanity: edgeStart length must be E+1
            if (edgeStart.size != G.E() + 1) {
                throw new IllegalStateException("edgeStart.size=" + edgeStart.size +
                        " but expected E+1=" + (G.E() + 1));
            }

            EdgeGeometry edgeGeometry = new EdgeGeometry(
                    edgeStart.toArray(),
                    toDoubleArray(geomX),
                    toDoubleArray(geomY)
            );

            LatLonVertexStore vs = new LatLonVertexStore(vm.vertexLat, vm.vertexLon);
            return new BuildResult(G, attrs, vs, edgeGeometry);
        }

        /**
         * Appends segment geometry to the global geometry arrays.
         *
         * <p>Copies points from the segment arrays to the main geometry arrays,
         * optionally reversing the order for backward edges.</p>
         *
         * @param geomX   global x-coordinate array to append to
         * @param geomY   global y-coordinate array to append to
         * @param segX    segment x-coordinates to copy
         * @param segY    segment y-coordinates to copy
         * @param reverse if true, append points in reverse order (for backward edges)
         */
        private static void appendGeometry(FloatArray geomX, FloatArray geomY,
                                           FloatArray segX, FloatArray segY,
                                           boolean reverse) {
            if (segX.size == 0) return;

            if (!reverse) {
                for (int i = 0; i < segX.size; i++) {
                    geomX.add(segX.get(i));
                    geomY.add(segY.get(i));
                }
            } else {
                for (int i = segX.size - 1; i >= 0; i--) {
                    geomX.add(segX.get(i));
                    geomY.add(segY.get(i));
                }
            }
        }

        /**
         * Converts a libGDX FloatArray to a primitive double array.
         *
         * @param a the float array
         * @return a new double array with the same values
         */
        private static double[] toDoubleArray(FloatArray a) {
            double[] out = new double[a.size];
            for (int i = 0; i < a.size; i++) out[i] = a.get(i);
            return out;
        }

        /**
         * Computes the great-circle distance between two geographic points.
         *
         * <p>Uses the Haversine formula for accuracy on a spherical Earth model.</p>
         *
         * @param lat1 latitude of first point (degrees)
         * @param lon1 longitude of first point (degrees)
         * @param lat2 latitude of second point (degrees)
         * @param lon2 longitude of second point (degrees)
         * @return distance in meters
         */
        private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
            final double R = 6371000.0;  // Earth radius in meters
            double phi1 = Math.toRadians(lat1);
            double phi2 = Math.toRadians(lat2);
            double dPhi = Math.toRadians(lat2 - lat1);
            double dLam = Math.toRadians(lon2 - lon1);

            double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2) +
                    Math.cos(phi1) * Math.cos(phi2) *
                            Math.sin(dLam / 2) * Math.sin(dLam / 2);

            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return R * c;
        }
    }
}