package codes;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * A lightweight HTTP server providing a REST API for route computation.
 *
 * <p>Exposes the routing engine via HTTP endpoints, returning results as GeoJSON
 * for easy integration with web mapping libraries (Leaflet, Mapbox, OpenLayers).</p>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /} - Serves the web UI (index.html)</li>
 *   <li>{@code GET /route?lat1=&lon1=&lat2=&lon2=} - Computes a route and returns GeoJSON</li>
 * </ul>
 * </p>
 *
 * <p>Example request:
 * <pre>
 *     GET /route?lat1=46.2382&amp;lon1=-63.1311&amp;lat2=46.2500&amp;lon2=-63.1200
 * </pre>
 * </p>
 *
 * <p>Example response:
 * <pre>
 *     {
 *       "type": "Feature",
 *       "geometry": {
 *         "type": "LineString",
 *         "coordinates": [[-63.1311, 46.2382], [-63.1200, 46.2500], ...]
 *       },
 *       "properties": {
 *         "instructions": ["Head north on Main St", "Turn right onto Oak Ave", ...]
 *       }
 *     }
 * </pre>
 * </p>
 */
public class RouteServer {

    /** The compiled OSM network, shared across all requests. */
    private static Main.OSMCompiler.BuildResult result;

    /** Default server port. */
    private static final int PORT = 8080;

    /**
     * Starts the routing server.
     *
     * <p>Compiles the OSM file on startup, then listens for HTTP requests.
     * The server runs until terminated.</p>
     *
     * @param args command-line arguments (currently ignored)
     * @throws Exception if server fails to start
     */
    public static void main(String[] args) throws Exception {

        System.out.println("Compiling OSM...");
        Main.OSMCompiler compiler = new Main.OSMCompiler();
        result = compiler.compile(java.nio.file.Path.of("src/main/resources/pei.osm"));
        System.out.println("Graph ready: V=" + result.graph.V() + " E=" + result.graph.E());

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Register endpoints
        server.createContext("/route", RouteServer::handleRoute);
        server.createContext("/", RouteServer::handleIndex);

        server.setExecutor(null);  // Use default executor
        server.start();

        System.out.println("Server running at http://localhost:" + PORT);
    }

    /* ============================================================
     * Handlers
     * ============================================================ */

    /**
     * Serves the web UI (index.html) from classpath resources.
     *
     * @param ex the HTTP exchange
     */
    private static void handleIndex(HttpExchange ex) {
        try (var in = RouteServer.class.getResourceAsStream("/index.html")) {

            if (in == null) {
                ex.sendResponseHeaders(404, -1);
                return;
            }

            byte[] html = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html");
            ex.sendResponseHeaders(200, html.length);
            ex.getResponseBody().write(html);
            ex.close();

        } catch (Exception e) {
            e.printStackTrace();
            try {
                ex.sendResponseHeaders(500, -1);
            } catch (IOException ignored) {}
        }
    }

    /**
     * Returns API information as JSON.
     *
     * @param ex the HTTP exchange
     * @throws IOException if response fails
     */
    private static void handleRoot(HttpExchange ex) throws IOException {
        sendJson(ex, 200, """
                {
                  "status": "ok",
                  "endpoints": {
                    "/route": "GET ?lat1=&lon1=&lat2=&lon2="
                  }
                }
                """);
    }

    /**
     * Handles route computation requests.
     *
     * <p>Expects query parameters:
     * <ul>
     *   <li>{@code lat1} - Start latitude (degrees)</li>
     *   <li>{@code lon1} - Start longitude (degrees)</li>
     *   <li>{@code lat2} - Goal latitude (degrees)</li>
     *   <li>{@code lon2} - Goal longitude (degrees)</li>
     * </ul>
     * </p>
     *
     * <p>Returns a GeoJSON Feature with:
     * <ul>
     *   <li>LineString geometry representing the route</li>
     *   <li>Turn-by-turn instructions in properties</li>
     * </ul>
     * </p>
     *
     * @param ex the HTTP exchange
     * @throws IOException if response fails
     */
    private static void handleRoute(HttpExchange ex) throws IOException {

        // Only allow GET requests
        if (!"GET".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, error("Method not allowed"));
            return;
        }

        Map<String, String> q = parseQuery(ex.getRequestURI());

        try {
            // Parse coordinates from query string
            double lat1 = Double.parseDouble(q.get("lat1"));
            double lon1 = Double.parseDouble(q.get("lon1"));
            double lat2 = Double.parseDouble(q.get("lat2"));
            double lon2 = Double.parseDouble(q.get("lon2"));

            // Compute route
            RoutingResult rr =
                    RouteCLI.routeLatLonWithRoute(
                            lat1, lon1, lat2, lon2, result
                    );

            if (rr == null || rr.geometry().isEmpty()) {
                sendJson(ex, 200, error("No route found"));
                return;
            }

            // Generate turn-by-turn instructions
            List<Instruction> instructions =
                    InstructionGenerator.generate(rr.route(), result.edgeGeometry, result.attrs, true);

            // Return GeoJSON with embedded instructions
            sendJson(ex, 200, geoJson(rr.geometry(), instructions));

        } catch (NumberFormatException e) {
            sendJson(ex, 400, error("Invalid coordinates: " + e.getMessage()));
        } catch (NullPointerException e) {
            sendJson(ex, 400, error("Missing required parameters: lat1, lon1, lat2, lon2"));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 400, error(e.getMessage()));
        }
    }

    /* ============================================================
     * GeoJSON Formatting
     * ============================================================ */

    /**
     * Formats a route as a GeoJSON Feature with instructions.
     *
     * <p>Produces a valid GeoJSON Feature object with:
     * <ul>
     *   <li>{@code type}: "Feature"</li>
     *   <li>{@code geometry}: LineString with [lon, lat] coordinate pairs</li>
     *   <li>{@code properties.instructions}: Array of turn-by-turn text</li>
     * </ul>
     * </p>
     *
     * @param pts          the route points (lon, lat order)
     * @param instructions the turn-by-turn instructions
     * @return GeoJSON string
     */
    private static String geoJson(
            List<Point> pts,
            List<Instruction> instructions
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
            {
              "type": "Feature",
              "geometry": {
                "type": "LineString",
                "coordinates": [
            """);

        // Append coordinates as [lon, lat] pairs (GeoJSON order)
        for (int i = 0; i < pts.size(); i++) {
            Point p = pts.get(i);
            sb.append("[")
                    .append(p.x)   // longitude
                    .append(", ")
                    .append(p.y)   // latitude
                    .append("]");
            if (i < pts.size() - 1) sb.append(",");
        }

        sb.append("""
                ]
              },
              "properties": {
                "instructions": [
            """);

        // Append instructions as JSON string array
        for (int i = 0; i < instructions.size(); i++) {
            sb.append("\"")
                    .append(escapeJson(instructions.get(i).toText()))
                    .append("\"");
            if (i < instructions.size() - 1) sb.append(",");
        }

        sb.append("""
                ]
              }
            }
            """);

        return sb.toString();
    }

    /**
     * Formats an error message as JSON.
     *
     * @param msg the error message
     * @return JSON error object
     */
    private static String error(String msg) {
        return """
                {
                  "error": "%s"
                }
                """.formatted(escapeJson(msg));
    }

    /**
     * Escapes special characters for JSON string values.
     *
     * @param s the string to escape
     * @return the escaped string
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /* ============================================================
     * HTTP Utilities
     * ============================================================ */

    /**
     * Sends a JSON response with the specified status code.
     *
     * @param ex   the HTTP exchange
     * @param code the HTTP status code
     * @param json the JSON response body
     * @throws IOException if sending fails
     */
    private static void sendJson(HttpExchange ex, int code, String json)
            throws IOException {

        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");  // Enable CORS
        ex.sendResponseHeaders(code, data.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    /**
     * Parses query parameters from a URI.
     *
     * <p>Handles the format {@code ?key1=value1&key2=value2}.</p>
     *
     * @param uri the request URI
     * @return map of parameter names to values; empty map if no query string
     */
    private static Map<String, String> parseQuery(URI uri) {
        if (uri.getQuery() == null) return Map.of();

        return java.util.Arrays.stream(uri.getQuery().split("&"))
                .map(s -> s.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(
                        a -> a[0],
                        a -> a.length > 1 ? a[1] : ""
                ));
    }
}