package ee.taltech.ite4120.reports.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ee.taltech.ite4120.reports.model.Report;
import ee.taltech.ite4120.reports.repository.ReportRepository;
import ee.taltech.ite4120.reports.unitchannel.UnitChannel;
import ee.taltech.ite4120.reports.unitchannel.UnitChannel.Delivery;
import ee.taltech.ite4120.reports.unitchannel.UnitChannel.Letter;
import ee.taltech.ite4120.reports.unitchannel.UnitChannel.UnitChannelException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.exception.ConflictException;
import org.helex.commons.exception.ForbiddenException;
import org.helex.commons.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The handover of an accepted report to the unit that solves it (REPORTS.01, Handover to the unit). */
@Service
public class HandoverService {

    private static final Logger log = LoggerFactory.getLogger(HandoverService.class);

    private final ReportRepository repository;
    private final UnitChannel channel;
    private final ReportAccess access;
    private final ObjectMapper json = new ObjectMapper();

    public HandoverService(ReportRepository repository, UnitChannel channel, ReportAccess access) {
        this.repository = repository;
        this.channel = channel;
        this.access = access;
    }

    /**
     * Hands the report to its current recipient. A channel that fails leaves a FAILED
     * handover and never undoes what the caller changed. "Coordinator decides" has no unit.
     */
    void handOver(Report report) {
        if ("UNDETERMINED".equals(report.getRecipientKind())) {
            return;
        }
        String place = report.getRecipientName();
        String unit = report.getRecipientName();
        if ("MUNICIPALITY".equals(report.getRecipientKind()) && place.contains(", ")) {
            unit = place.substring(place.indexOf(", ") + 2);
            place = place.substring(0, place.indexOf(", "));
        }
        String member = slug(place);
        String inbox = slug(report.getResponsibleUnit().split(" ")[0]) + "@" + member + ".example";
        Letter letter = new Letter(member, inbox, letter(report, unit, inbox));
        try {
            Delivery d = channel.send(letter);
            repository.insertHandover(report.getId(), report.getRecipientName(), unit, inbox, d.channel(), d.service(),
                    "SENT", d.messageId(), null);
        } catch (UnitChannelException e) {
            log.warn("handover of {} failed: {}", report.getReferenceNumber(), e.getMessage());
            repository.insertHandover(report.getId(), report.getRecipientName(), unit, inbox, "XROAD", e.service(),
                    "FAILED", null, cut(e.getMessage()));
        }
    }

    /** The coordinator sends a failed or returned handover again. */
    @Transactional
    public Report sendAgain(String referenceNumber) {
        access.requireCoordinator();
        Report report = require(referenceNumber);
        if (!"ACCEPTED".equals(report.getStatus()) && !"IN_PROGRESS".equals(report.getStatus())) {
            throw new ConflictException("report", "only an accepted report is handed to a unit");
        }
        if (!"FAILED".equals(report.getHandoverState()) && !"RETURNED".equals(report.getHandoverState())) {
            throw new ConflictException("report", "the report is already with the unit");
        }
        handOver(report);
        return require(referenceNumber);
    }

    /** The unit says the report is not theirs. The coordinator forwards or rejects it. */
    @Transactional
    public Report giveBack(String referenceNumber, String reason) {
        Report report = require(referenceNumber);
        if (!access.isUnitOf(report)) {
            throw new ForbiddenException("only the unit the report was handed to may do this");
        }
        if (reason == null || reason.isBlank()) {
            throw new ApiClientException("reason is required when a unit returns a report");
        }
        if (!repository.updateHandover(report.getHandoverId(), "RETURNED", reason.trim(), "SENT", "OPENED")) {
            throw new ConflictException("report", "the report was changed meanwhile, reload it");
        }
        return require(referenceNumber);
    }

    /** The unit has seen the report. */
    void opened(Report report) {
        if ("SENT".equals(report.getHandoverState()) && access.isUnitOf(report)) {
            repository.updateHandover(report.getHandoverId(), "OPENED", null, "SENT");
            report.setHandoverState("OPENED");
        }
    }

    /** The reports handed to the signed-in unit. */
    public List<Report> handed() {
        String unit = access.unit();
        if (unit == null) {
            throw new ForbiddenException("only a unit may do this");
        }
        return repository.handedTo(unit);
    }

    private String letter(Report r, String unit, String inbox) {
        ObjectNode n = json.createObjectNode().put("referenceNumber", r.getReferenceNumber())
                .put("categoryCode", r.getCategoryCode()).put("category", r.getCategoryName())
                .put("recipient", r.getRecipientName()).put("unit", unit).put("inbox", inbox)
                .put("address", r.getAddress()).put("description", r.getDescription())
                .put("submittedAt", String.valueOf(r.getSubmittedAt()));
        if (r.getLatitude() != null) {
            n.put("latitude", r.getLatitude()).put("longitude", r.getLongitude());
        }
        return n.toString();
    }

    private Report require(String referenceNumber) {
        Report report = repository.loadByReferenceNumber(referenceNumber.trim().toUpperCase(Locale.ROOT));
        if (report == null) {
            throw new NotFoundException("no report with reference number " + referenceNumber);
        }
        return report;
    }

    /** "Põhja-Tallinn linnaosa" becomes "pohja-tallinn-linnaosa". */
    static String slug(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static String cut(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }
}
