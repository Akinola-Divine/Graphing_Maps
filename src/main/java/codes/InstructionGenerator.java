package codes;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates turn-by-turn navigation instructions from a computed route.
 *
 * <p>Analyzes the route's edge sequence and geometry to produce human-readable
 * instructions like "Turn right onto Main Street" or "Keep left on Highway 1".</p>
 *
 * <p>Instruction emission policies:
 * <ul>
 *   <li><b>Policy A:</b> Always emit when street name changes</li>
 *   <li><b>Policy C:</b> Optionally emit on sharp bends even if street name stays
 *       the same, subject to a minimum distance threshold (spam guard)</li>
 * </ul>
 * </p>
 *
 * <p>Turn direction is determined by computing the angle between consecutive
 * edge geometries using cross/dot product of direction vectors.</p>
 *
 * <p>Example usage:
 * <pre>
 *     List&lt;Instruction&gt; instructions = InstructionGenerator.generate(
 *         route, edgeGeometry, edgeAttributes, true
 *     );
 *     for (Instruction inst : instructions) {
 *         System.out.println(inst.toText());
 *     }
 * </pre>
 * </p>
 */
public class InstructionGenerator {

    /**
     * Angle threshold (radians) for classifying a turn as a "sharp bend".
     * Turns with absolute angle >= this value trigger bend instructions.
     */
    private static final double BEND_THRESHOLD = Math.toRadians(50);

    /**
     * Default minimum distance (meters) between bend instructions.
     * Prevents spamming instructions on winding roads.
     */
    private static final double MIN_METERS = 120.0;

    /**
     * Generates turn-by-turn instructions with default spam guard distance.
     *
     * @param r                       the computed route
     * @param g                       edge geometry for turn angle computation
     * @param attrs                   edge attributes containing street names
     * @param emitSharpBendsSameStreet if true, emit instructions for sharp bends
     *                                 even when street name doesn't change
     * @return list of instructions in travel order; empty if route is null/empty
     */
    public static List<Instruction> generate(
            RoutingEngine.Route r,
            EdgeGeometry g,
            EdgeAttributes attrs,
            boolean emitSharpBendsSameStreet
    ) {
        return generate(r, g, attrs,
                emitSharpBendsSameStreet,
                MIN_METERS
        );
    }

    /**
     * Generates turn-by-turn instructions with configurable spam guard.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Emit START instruction with first street name</li>
     *   <li>For each edge transition:
     *     <ul>
     *       <li>If street name changes → emit turn instruction (Policy A)</li>
     *       <li>If sharp bend and distance threshold met → emit keep instruction (Policy C)</li>
     *     </ul>
     *   </li>
     *   <li>Emit ARRIVE instruction at end</li>
     * </ol>
     * </p>
     *
     * @param r          the computed route
     * @param g          edge geometry for turn angle computation
     * @param attrs      edge attributes containing street names and distances
     * @param emitSharp  if true, emit instructions for sharp bends on same street
     * @param minMeters  minimum distance since last instruction before emitting
     *                   a same-street bend instruction
     * @return list of instructions in travel order; empty if route is null/empty
     */
    public static List<Instruction> generate(
            RoutingEngine.Route r,
            EdgeGeometry g,
            EdgeAttributes attrs,
            boolean emitSharp,
            double minMeters
    ) {
        List<Instruction> out = new ArrayList<>();
        if (r == null || r.edgeIds == null || r.edgeIds.length == 0) return out;

        String currentStreet = safe(attrs.streetName(r.edgeIds[0]));
        out.add(new Instruction(Instruction.Type.START, currentStreet, 0));

        double acc = 0.0;  // meters since last emitted instruction

        for (int i = 0; i < r.edgeIds.length - 1; i++) {
            int e0 = r.edgeIds[i];
            int e1 = r.edgeIds[i + 1];

            acc += attrs.distanceMeters(e0);

            String nextStreet = safe(attrs.streetName(e1));
            TurnInfo turn = turnBetweenEdges(g, e0, e1);

            boolean nameChange = !nextStreet.equalsIgnoreCase(currentStreet);

            // Policy A: always emit on street-name change
            if (nameChange) {
                out.add(new Instruction(turn.turnType, nextStreet, acc));
                acc = 0.0;
                currentStreet = nextStreet;
                continue;
            }

            // Policy C: sharp bends even if name stays the same (spam-guarded)
            if (emitSharp && turn.isSharpBend && acc >= minMeters) {
                Instruction.Type keepType =
                        (turn.turnType == Instruction.Type.LEFT)  ? Instruction.Type.KEEP_LEFT :
                                (turn.turnType == Instruction.Type.RIGHT) ? Instruction.Type.KEEP_RIGHT :
                                        Instruction.Type.CONTINUE;

                out.add(new Instruction(keepType, currentStreet, acc));
                acc = 0.0;
            }
        }

        // Include last edge distance before arriving
        acc += attrs.distanceMeters(r.edgeIds[r.edgeIds.length - 1]);
        out.add(new Instruction(Instruction.Type.ARRIVE, "", acc));

        return out;
    }

    /**
     * Encapsulates turn analysis results.
     */
    private static final class TurnInfo {

        /** The classified turn type (LEFT, RIGHT, or CONTINUE). */
        final Instruction.Type turnType;

        /** True if the turn angle exceeds the bend threshold. */
        final boolean isSharpBend;

        /**
         * Constructs a turn info result.
         *
         * @param turnType    the turn type
         * @param isSharpBend whether this qualifies as a sharp bend
         */
        TurnInfo(Instruction.Type turnType, boolean isSharpBend) {
            this.turnType = turnType;
            this.isSharpBend = isSharpBend;
        }
    }

    /**
     * Computes the turn type and sharpness between two consecutive edges.
     *
     * <p>Uses the last segment of the previous edge and the first segment
     * of the next edge to compute the turn angle via cross/dot product.</p>
     *
     * @param g        the edge geometry
     * @param prevEdge the edge being exited
     * @param nextEdge the edge being entered
     * @return turn info with type and sharpness; CONTINUE if vectors unavailable
     */
    private static TurnInfo turnBetweenEdges(EdgeGeometry g, int prevEdge, int nextEdge) {
        double[] v1 = lastSegmentVector(g, prevEdge);
        double[] v2 = firstSegmentVector(g, nextEdge);

        if (v1 == null || v2 == null) {
            return new TurnInfo(Instruction.Type.CONTINUE, false);
        }

        // Cross product determines turn direction (positive = left)
        double cross = v1[0] * v2[1] - v1[1] * v2[0];
        // Dot product determines angle magnitude
        double dot = v1[0] * v2[0] + v1[1] * v2[1];
        double angle = Math.atan2(cross, dot);

        boolean sharp = Math.abs(angle) >= BEND_THRESHOLD;
        if (!sharp) {
            return new TurnInfo(Instruction.Type.CONTINUE, false);
        }

        return new TurnInfo(
                angle > 0 ? Instruction.Type.LEFT : Instruction.Type.RIGHT,
                true
        );
    }

    /**
     * Computes the direction vector of the first segment of an edge.
     *
     * <p>Used to determine the entry direction when joining an edge.</p>
     *
     * @param g      the edge geometry
     * @param edgeId the edge ID
     * @return [dx, dy] direction vector, or null if edge has fewer than 2 points
     */
    private static double[] firstSegmentVector(EdgeGeometry g, int edgeId) {
        int s = g.startIndex(edgeId);
        int e = g.endIndex(edgeId);

        if (e - s < 2) return null;

        return new double[]{
                g.x(s + 1) - g.x(s),
                g.y(s + 1) - g.y(s)
        };
    }

    /**
     * Computes the direction vector of the last segment of an edge.
     *
     * <p>Used to determine the exit direction when leaving an edge.</p>
     *
     * @param g      the edge geometry
     * @param edgeId the edge ID
     * @return [dx, dy] direction vector, or null if edge has fewer than 2 points
     */
    private static double[] lastSegmentVector(EdgeGeometry g, int edgeId) {
        int s = g.startIndex(edgeId);
        int e = g.endIndex(edgeId);

        if (e - s < 2) return null;

        return new double[]{
                g.x(e - 1) - g.x(e - 2),
                g.y(e - 1) - g.y(e - 2)
        };
    }

    /**
     * Returns a safe street name, substituting "unnamed road" for null values.
     *
     * @param s the street name (may be null)
     * @return the street name or "unnamed road"
     */
    private static String safe(String s) {
        return s == null ? "unnamed road" : s;
    }
}