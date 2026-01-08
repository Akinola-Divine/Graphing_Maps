package codes;

import static java.lang.Math.*;

/**
 * Local tangent-plane projection (equirectangular).
 *
 * Converts lat/lon (degrees) into x/y meters relative to a reference point.
 * Suitable for regional routing (PEI-scale and similar).
 */
public final class LocalProjection {

    // Earth radius (meters)
    private static final double R = 6371000.0;

    // Reference point (radians)
    private final double lat0;
    private final double lon0;
    private final double cosLat0;

    /**
     * Create a projection centered on the given reference latitude/longitude.
     *
     * @param lat0Deg reference latitude (degrees)
     * @param lon0Deg reference longitude (degrees)
     */
    public LocalProjection(double lat0Deg, double lon0Deg) {
        this.lat0 = toRadians(lat0Deg);
        this.lon0 = toRadians(lon0Deg);
        this.cosLat0 = cos(this.lat0);
    }

    /**
     * Project a latitude/longitude into x/y meters.
     *
     * @param latDeg latitude in degrees
     * @param lonDeg longitude in degrees
     * @param outXY  length-2 array: [0]=x, [1]=y
     */

    public void project(double latDeg, double lonDeg, double[] outXY) {
        double lat = toRadians(latDeg);
        double lon = toRadians(lonDeg);

        double x = R * (lon - lon0) * cosLat0;
        double y = R * (lat - lat0);

        outXY[0] = x;
        outXY[1] = y;
    }

    /**
     * Project arrays of lat/lon into x/y arrays.
     *
     * @param lat input latitudes (degrees)
     * @param lon input longitudes (degrees)
     * @param x   output x (meters)
     * @param y   output y (meters)
     */
    public void projectAll(double[] lat, double[] lon,
                           double[] x, double[] y) {

        if (lat.length != lon.length ||
                lat.length != x.length ||
                lat.length != y.length) {
            throw new IllegalArgumentException("Array length mismatch");
        }

        for (int i = 0; i < lat.length; i++) {
            double phi = toRadians(lat[i]);
            double lam = toRadians(lon[i]);

            x[i] = R * (lam - lon0) * cosLat0;
            y[i] = R * (phi - lat0);
        }
    }

    /**
     * Utility: compute a good reference latitude (mean latitude).
     */
    public static double meanLatitude(double[] latDeg) {
        double sum = 0.0;
        for (double v : latDeg) sum += v;
        return sum / latDeg.length;
    }

    /**
     * Utility: compute a good reference longitude (mean longitude).
     */
    public static double meanLongitude(double[] lonDeg) {
        double sum = 0.0;
        for (double v : lonDeg) sum += v;
        return sum / lonDeg.length;
    }

    /**
     * computes the inverse projection
     * */
    public void inverse(double x, double y, double[] outLatLon){
        double lon = lon0 + x / (R*cosLat0);
        double lat = lat0 + y / R;
        outLatLon[0] = Math.toDegrees(lat);
        outLatLon[1] = Math.toDegrees(lon);
    }
}