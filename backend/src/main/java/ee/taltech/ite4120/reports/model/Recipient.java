package ee.taltech.ite4120.reports.model;

/** Who a report is sent to. */
public record Recipient(Kind kind, String name) {

    public enum Kind { STATE_ROADS, MUNICIPALITY, OTHER, UNDETERMINED }

    public static final String STATE_ROADS_NAME = "Transport Administration";
    public static final Recipient STATE_ROADS = new Recipient(Kind.STATE_ROADS, STATE_ROADS_NAME);
    public static final Recipient UNDETERMINED = new Recipient(Kind.UNDETERMINED, "Coordinator decides");
}
