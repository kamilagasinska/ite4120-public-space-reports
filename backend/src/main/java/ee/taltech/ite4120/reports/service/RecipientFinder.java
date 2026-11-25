package ee.taltech.ite4120.reports.service;

import ee.taltech.ite4120.reports.model.Category;
import ee.taltech.ite4120.reports.model.Recipient;
import ee.taltech.ite4120.reports.roadmap.RoadMapAdapter;
import ee.taltech.ite4120.reports.roadmap.RoadMapAdapter.RoadOwner;
import ee.taltech.ite4120.reports.roadmap.RoadMapException;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Decides who a report is sent to (REPORTS.01, Recipient). */
@Component
public class RecipientFinder {

    private static final Logger log = LoggerFactory.getLogger(RecipientFinder.class);
    private static final String ROAD_DAMAGE = "ROAD_DAMAGE";
    private static final String OTHER = "OTHER";

    private final RoadMapAdapter roads;

    public RecipientFinder(RoadMapAdapter roads) {
        this.roads = roads;
    }

    public Recipient find(Category category, String municipalityName, BigDecimal latitude, BigDecimal longitude) {
        if (OTHER.equals(category.getCode())) {
            return Recipient.UNDETERMINED;
        }
        if (ROAD_DAMAGE.equals(category.getCode()) && latitude != null) {
            switch (owner(latitude, longitude)) {
                case STATE:
                    return Recipient.STATE_ROADS;
                case UNCLEAR:
                    return Recipient.UNDETERMINED;
                default:
                    break;
            }
        }
        return municipalityName == null ? Recipient.UNDETERMINED
                : new Recipient(Recipient.Kind.MUNICIPALITY, municipalityName + ", " + category.getResponsibleUnit());
    }

    /** Without the map it is not known whose road it is, so the coordinator decides. */
    private RoadOwner owner(BigDecimal latitude, BigDecimal longitude) {
        try {
            return roads.owner(latitude.doubleValue(), longitude.doubleValue());
        } catch (RoadMapException e) {
            log.warn("road owner not determined: {}", e.getMessage());
            return RoadOwner.UNCLEAR;
        }
    }
}
