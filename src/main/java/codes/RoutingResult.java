package codes;

import java.util.List;

/**
 * The result of a routing computation, combining the geometric path
 * with route metadata.
 *
 * <p>This record bundles together:
 * <ul>
 *   <li>The visual polyline geometry (list of points for rendering)</li>
 *   <li>The underlying route data (edges, cost, algorithm used)</li>
 * </ul>
 * </p>
 *
 * <p>Example usage:
 * <pre>
 *     RoutingResult result = computeRoute(startX, startY, goalX, goalY);
 *     if (!result.isTrivial() &amp;&amp; result.route().found) {
 *         List&lt;Point&gt; path = result.geometry();
 *         double cost = result.route().totalCost;
 *     }
 * </pre>
 * </p>
 *
 * @param geometry the reconstructed polyline as a list of points (may be empty)
 * @param route    the route metadata including edges and cost; {@code null} if trivial
 */
public record RoutingResult(
        List<Point> geometry,
        RoutingEngine.Route route
){}