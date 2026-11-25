package ee.taltech.ite4120.reports.roadmap;

/** The port to the national road map. */
public interface RoadMapAdapter {

    /** Derived from the road type. The public map has no owner field. */
    enum RoadOwner { STATE, MUNICIPALITY, UNCLEAR, NO_ROAD }

    /**
     * The owner of the road nearest to the point, within 30 metres.
     *
     * @throws RoadMapException when the map cannot be reached
     */
    RoadOwner owner(double latitude, double longitude);
}
