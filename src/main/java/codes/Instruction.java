package codes;

/**
 * Represents a single turn-by-turn navigation instruction.
 *
 * <p>Each instruction contains:
 * <ul>
 *   <li>The type of maneuver (start, turn, keep, continue, arrive)</li>
 *   <li>The street name to follow</li>
 *   <li>The distance to travel before the next instruction</li>
 * </ul>
 * </p>
 *
 * <p>Example instructions:
 * <ul>
 *   <li>"Start on Main Street"</li>
 *   <li>"Turn right onto Oak Avenue for 250 m"</li>
 *   <li>"Keep left on Highway 1 for 500 m"</li>
 *   <li>"Continue on Elm Street for 1200 m"</li>
 *   <li>"You have arrived"</li>
 * </ul>
 * </p>
 *
 * @see InstructionGenerator
 */
public class Instruction {

    /**
     * The type of navigation maneuver.
     *
     * <p>Types are categorized as:
     * <ul>
     *   <li><b>Route boundaries:</b> START, ARRIVE</li>
     *   <li><b>Street changes:</b> LEFT, RIGHT (turn onto different street)</li>
     *   <li><b>Same-street maneuvers:</b> KEEP_LEFT, KEEP_RIGHT (sharp bends)</li>
     *   <li><b>No action:</b> CONTINUE (straight ahead)</li>
     * </ul>
     * </p>
     */
    public enum Type {
        /** Begin the route on a street. */
        START,

        /** Continue straight (no significant turn). */
        CONTINUE,

        /** Turn left onto a (possibly different) street. */
        LEFT,

        /** Turn right onto a (possibly different) street. */
        RIGHT,

        /** Bear/keep left while staying on the same street (sharp bend). */
        KEEP_LEFT,

        /** Bear/keep right while staying on the same street (sharp bend). */
        KEEP_RIGHT,

        /** Destination reached. */
        ARRIVE
    }

    /** The maneuver type. */
    public final Type type;

    /** The street name (may be null or "unnamed road"). */
    public final String street;

    /** Distance in meters to travel after this instruction. */
    public final double distanceMeters;

    /**
     * Constructs a navigation instruction.
     *
     * @param type           the maneuver type
     * @param street         the street name (may be null)
     * @param distanceMeters distance to next instruction in meters
     */
    public Instruction(Type type, String street, double distanceMeters) {
        this.type = type;
        this.street = street;
        this.distanceMeters = distanceMeters;
    }

    /**
     * Formats this instruction as human-readable text.
     *
     * <p>Format varies by type:
     * <ul>
     *   <li>START: "Start on [street]" or "Start"</li>
     *   <li>CONTINUE: "Continue on [street] for X m"</li>
     *   <li>LEFT/RIGHT: "Turn [left/right] onto [street] for X m"</li>
     *   <li>KEEP_LEFT/KEEP_RIGHT: "Keep [left/right] on [street] for X m"</li>
     *   <li>ARRIVE: "You have arrived"</li>
     * </ul>
     * </p>
     *
     * <p>Distance is omitted if less than 1 meter. Street name is omitted
     * if null or "unnamed road".</p>
     *
     * @return the formatted instruction text
     */
    public String toText() {
        boolean unnamed = street == null || street.equalsIgnoreCase("unnamed road");

        String d = distanceMeters > 1
                ? String.format(" for %.0f m", distanceMeters)
                : "";

        return switch (type) {
            case START ->
                    unnamed ? "Start" : "Start on " + street;

            case CONTINUE ->
                    unnamed ? "Continue straight" + d
                            : "Continue on " + street + d;

            case LEFT ->
                    unnamed ? "Turn left" + d
                            : "Turn left onto " + street + d;

            case RIGHT ->
                    unnamed ? "Turn right" + d
                            : "Turn right onto " + street + d;

            case KEEP_LEFT ->
                    unnamed ? "Keep left" + d
                            : "Keep left on " + street + d;

            case KEEP_RIGHT ->
                    unnamed ? "Keep right" + d
                            : "Keep right on " + street + d;

            case ARRIVE ->
                    "You have arrived";
        };
    }

    /**
     * Returns an icon identifier for UI rendering.
     *
     * <p>Can be used to select appropriate navigation icons in a UI.</p>
     *
     * @return icon name string
     */
    public String icon() {
        return switch (type) {
            case START -> "depart";
            case CONTINUE -> "straight";
            case LEFT -> "turn-left";
            case RIGHT -> "turn-right";
            case KEEP_LEFT -> "bear-left";
            case KEEP_RIGHT -> "bear-right";
            case ARRIVE -> "arrive";
        };
    }

    /**
     * Returns true if this is a turn instruction (LEFT, RIGHT, KEEP_LEFT, KEEP_RIGHT).
     *
     * @return {@code true} if this is a turn; {@code false} otherwise
     */
    public boolean isTurn() {
        return type == Type.LEFT || type == Type.RIGHT ||
                type == Type.KEEP_LEFT || type == Type.KEEP_RIGHT;
    }

    /**
     * Returns true if this is a route boundary (START or ARRIVE).
     *
     * @return {@code true} if boundary instruction; {@code false} otherwise
     */
    public boolean isBoundary() {
        return type == Type.START || type == Type.ARRIVE;
    }

    /**
     * Returns a string representation for debugging.
     *
     * @return debug string with all fields
     */
    @Override
    public String toString() {
        return String.format("Instruction[%s, \"%s\", %.1fm]",
                type, street, distanceMeters);
    }
}