package codes;

/**
 * An immutable 2D point with double precision coordinates.
 *
 * <p>Used throughout the routing system to represent geographic positions
 * in projected coordinate space (meters).</p>
 */
public final class Point {

    /** The x-coordinate (easting) in meters. */
    public final double x;

    /** The y-coordinate (northing) in meters. */
    public final double y;

    /**
     * Constructs a point with the given coordinates.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     */
    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Computes the Euclidean distance to another point.
     *
     * @param other the other point
     * @return the distance in meters
     */
    public double distanceTo(Point other) {
        return Math.hypot(x - other.x, y - other.y);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point p)) return false;
        return Double.compare(p.x, x) == 0 && Double.compare(p.y, y) == 0;
    }

    @Override
    public int hashCode() {
        long hx = Double.doubleToLongBits(x);
        long hy = Double.doubleToLongBits(y);
        return (int) (hx ^ (hx >>> 32)) * 31 + (int) (hy ^ (hy >>> 32));
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f)", x, y);
    }
}