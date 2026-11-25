package ee.taltech.ite4120.reports.addressregister;

/** Converts WGS84 degrees to the Estonian grid L-EST97 (EPSG:3301), which the register asks for. */
public final class LEst97 {

    private static final double A = 6378137.0;
    private static final double F = 1 / 298.257222101;
    private static final double E = Math.sqrt(2 * F - F * F);
    private static final double LAT1 = Math.toRadians(59 + 20 / 60.0);
    private static final double LAT2 = Math.toRadians(58);
    private static final double LAT0 = Math.toRadians(57 + 31 / 60.0 + 3.19415 / 3600);
    private static final double LON0 = Math.toRadians(24);
    private static final double N = (Math.log(m(LAT1)) - Math.log(m(LAT2))) / (Math.log(t(LAT1)) - Math.log(t(LAT2)));
    private static final double FF = m(LAT1) / (N * Math.pow(t(LAT1), N));
    private static final double RHO0 = A * FF * Math.pow(t(LAT0), N);

    private LEst97() {}

    /** Easting and northing in metres. */
    public static double[] fromDegrees(double latitude, double longitude) {
        double rho = A * FF * Math.pow(t(Math.toRadians(latitude)), N);
        double theta = N * (Math.toRadians(longitude) - LON0);
        return new double[] {500000 + rho * Math.sin(theta), 6375000 + RHO0 - rho * Math.cos(theta)};
    }

    private static double m(double lat) {
        return Math.cos(lat) / Math.sqrt(1 - Math.pow(E * Math.sin(lat), 2));
    }

    private static double t(double lat) {
        double s = E * Math.sin(lat);
        return Math.tan(Math.PI / 4 - lat / 2) / Math.pow((1 - s) / (1 + s), E / 2);
    }
}
