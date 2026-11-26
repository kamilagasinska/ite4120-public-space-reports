package ee.taltech.ite4120.reports.service;

import ee.taltech.ite4120.reports.addressregister.AddressInfo;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterAdapter;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterException;
import ee.taltech.ite4120.reports.dto.ReportQueryParams;
import ee.taltech.ite4120.reports.model.Category;
import ee.taltech.ite4120.reports.model.Forwarding;
import ee.taltech.ite4120.reports.model.Place;
import ee.taltech.ite4120.reports.model.Recipient;
import ee.taltech.ite4120.reports.model.Report;
import ee.taltech.ite4120.reports.model.ReportStatus;
import ee.taltech.ite4120.reports.model.Reporter;
import ee.taltech.ite4120.reports.model.Standing;
import ee.taltech.ite4120.reports.model.StatusChange;
import ee.taltech.ite4120.reports.repository.ReportRepository;
import ee.taltech.ite4120.reports.repository.ReporterRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.exception.ConflictException;
import org.helex.commons.exception.ForbiddenException;
import org.helex.commons.exception.NotFoundException;
import org.helex.commons.model.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** The business rules of REPORTS.01. */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final int REFERENCE_ATTEMPTS = 5;
    private static final Set<String> CHANNELS = Set.of("GUEST", "DEVICE", "SIGNED");
    private static final String SIGNING_KEY = "signing-";
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}");

    private final ReportRepository repository;
    private final AddressRegisterAdapter addressRegister;
    private final ReportAccess access;
    private final ReporterRepository reporters;
    private final ReportLimits limits;
    private final KeyedHash hash;
    private final RecipientFinder recipients;
    private final TransactionTemplate transaction;
    private final HandoverService handovers;

    public ReportService(ReportRepository repository, AddressRegisterAdapter addressRegister, ReportAccess access,
            ReporterRepository reporters, ReportLimits limits, KeyedHash hash, RecipientFinder recipients,
            PlatformTransactionManager transactions, HandoverService handovers) {
        this.handovers = handovers;
        this.recipients = recipients;
        this.reporters = reporters;
        this.limits = limits;
        this.hash = hash;
        this.transaction = new TransactionTemplate(transactions);
        this.repository = repository;
        this.addressRegister = addressRegister;
        this.access = access;
    }

    /** The stored report, and whether this submit created it. */
    public record Submission(Report report, boolean created) {}

    public QueryResult<Report> search(ReportQueryParams params) {
        access.requireCoordinator();
        validate(params);
        return repository.search(params);
    }

    public Report find(String referenceNumber) {
        Report report = require(referenceNumber);
        handovers.opened(report);
        if (access.isCoordinator() && report.getReporterId() != null) {
            int[] counts = repository.senderCounts(report.getReporterId());
            report.setSender(counts[0], counts[1], standing(report.getReporterId()).points());
        }
        return visible(report);
    }

    /** A guest or device report. The device identifier is null for a guest. */
    public Submission submit(Report report, String deviceId, String ip) {
        if (report.getSubmissionKey() != null && report.getSubmissionKey().trim().startsWith(SIGNING_KEY)) {
            throw new ApiClientException("submissionKey must not start with " + SIGNING_KEY);
        }
        validate(report);
        Reporter reporter = deviceId == null ? null
                : new Reporter(Reporter.Kind.DEVICE, hash.of("device", requireUuid(deviceId)));
        report.setChannel(reporter == null ? "GUEST" : "DEVICE");
        report.setIpHash(hash.of("ip", ip));
        return store(report, reporter);
    }

    /** A report whose signature has been verified. The token makes a repeated call harmless. */
    Report submitSigned(Report report, Reporter reporter, String token, String digest) {
        validate(report);
        report.setSubmissionKey(SIGNING_KEY + token);
        report.setChannel("SIGNED");
        report.setSignedDigest(digest);
        store(report, reporter);
        return repository.loadBySubmissionKey(report.getSubmissionKey());
    }

    Report signedReport(String token) {
        return repository.loadBySubmissionKey(SIGNING_KEY + token);
    }

    /** The hash a device is known by. */
    public String deviceHash(String deviceId) {
        return hash.of("device", requireUuid(deviceId));
    }

    Standing standing(Long reporterId) {
        return limits.standing(reporters.load(reporterId));
    }

    Standing standing(Reporter reporter) {
        return limits.standing(reporter);
    }

    /** Points and trust level of a device. */
    public Standing standing(String deviceId) {
        return limits.standing(new Reporter(Reporter.Kind.DEVICE, deviceHash(deviceId)));
    }

    /** An early answer before signing starts. The binding check is made when the report is stored. */
    void checkLimit(Reporter reporter) {
        limits.check(reporter, null);
    }

    /** The place at a map point. Unlike a submit, this has nothing to fall back to, so it fails. */
    public Place place(BigDecimal latitude, BigDecimal longitude, String categoryCode) {
        if (categoryCode != null) {
            requireKnownCategory(categoryCode);
        }
        if (latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0 || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new ApiClientException("latitude or longitude is out of range");
        }
        AddressInfo place = addressRegister.lookupPoint(latitude.doubleValue(), longitude.doubleValue());
        if (place == null) {
            throw new NotFoundException("no municipality at this point");
        }
        Recipient recipient = categoryCode == null ? null
                : recipients.find(repository.category(categoryCode), place.municipalityName(), latitude, longitude);
        return new Place(place.fullAddress(), place.municipalityCode(), place.municipalityName(),
                recipient == null ? null : recipient.kind(), recipient == null ? null : recipient.name());
    }

    /**
     * The register is asked before the transaction, so it never holds a database
     * connection or a lock. Inside, the limit check and the insert are one step.
     */
    private Submission store(Report report, Reporter reporter) {
        Report existing = bySubmissionKey(report.getSubmissionKey());
        if (existing != null) {
            return new Submission(visible(existing), false);
        }
        locate(report);
        for (int attempt = 1; ; attempt++) {
            report.setReferenceNumber(ReferenceNumbers.next());
            try {
                Report before = transaction.execute(status -> insertWithinLimit(report, reporter));
                return before != null ? new Submission(visible(before), false)
                        : new Submission(find(report.getReferenceNumber()), true);
            } catch (DuplicateKeyException e) {
                /* Either the same form was submitted at the same moment, or the random number is taken. */
                existing = bySubmissionKey(report.getSubmissionKey());
                if (existing != null) {
                    return new Submission(visible(existing), false);
                }
                if (attempt == REFERENCE_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    /** The existing report when the same form arrived while waiting for the lock, otherwise null. */
    private Report insertWithinLimit(Report report, Reporter reporter) {
        limits.lock(reporter, report.getIpHash());
        Report existing = bySubmissionKey(report.getSubmissionKey());
        if (existing != null) {
            return existing;
        }
        limits.check(reporter, report.getIpHash());
        report.setReporterId(reporter == null ? null : reporters.touch(reporter));
        repository.insert(report);
        return null;
    }

    @Transactional
    public Report changeStatus(String referenceNumber, String toStatus, String reason, boolean falseReport,
            boolean publish) {
        if (!access.isCoordinator() && access.unit() == null) {
            throw new ForbiddenException("only a coordinator or a unit may do this");
        }
        if (!ReportStatus.isKnown(toStatus)) {
            throw new ApiClientException("unknown toStatus: " + toStatus);
        }
        Report report = require(referenceNumber);
        ReportStatus from = ReportStatus.valueOf(report.getStatus());
        ReportStatus to = ReportStatus.valueOf(toStatus);
        if (!from.canBecome(to)) {
            throw new ConflictException("report", "status cannot change from " + from + " to " + to);
        }
        boolean solving = to == ReportStatus.IN_PROGRESS || to == ReportStatus.RESOLVED;
        if (solving && !access.isUnitOf(report)) {
            throw new ForbiddenException("only the unit the report was handed to may do this");
        }
        if (!solving && !access.isCoordinator()) {
            throw new ForbiddenException("only a coordinator may do this");
        }
        String cleanReason = blankToNull(reason);
        if (to == ReportStatus.REJECTED && cleanReason == null) {
            throw new ApiClientException("reason is required when a report is rejected");
        }
        if (falseReport && to != ReportStatus.REJECTED) {
            throw new ApiClientException("falseReport is only for a rejected report");
        }
        if (publish && to == ReportStatus.REJECTED) {
            throw new ApiClientException("a rejected report is not published");
        }
        if (!repository.updateStatus(report.getId(), from.name(), to.name())) {
            throw new ConflictException("report", "status is no longer " + from + ", reload the report");
        }
        repository.insertStatusChange(report.getId(), from.name(), to.name(), cleanReason, falseReport);
        if (publish || to == ReportStatus.REJECTED) {
            repository.setPublished(report.getId(), publish);
        }
        if (to == ReportStatus.ACCEPTED) {
            handovers.handOver(require(report.getReferenceNumber()));
        }
        return visible(require(report.getReferenceNumber()));
    }

    /** Sends the report to another recipient. The resident sees where and why. */
    @Transactional
    public Report forward(String referenceNumber, String kind, String name, String reason) {
        access.requireCoordinator();
        boolean state = "STATE_ROADS".equals(kind);
        if (!state && !"MUNICIPALITY".equals(kind) && !"OTHER".equals(kind)) {
            throw new ApiClientException("kind must be STATE_ROADS, MUNICIPALITY or OTHER");
        }
        String toName = state ? Recipient.STATE_ROADS_NAME : blankToNull(name);
        if (toName == null) {
            throw new ApiClientException("name is required for " + kind);
        }
        Report report = require(referenceNumber);
        if (ReportStatus.valueOf(report.getStatus()).isClosed()) {
            throw new ConflictException("report", "a report that is " + report.getStatus() + " cannot be forwarded");
        }
        if (toName.equals(report.getRecipientName())) {
            throw new ConflictException("report", "the report is already with " + toName);
        }
        if (!repository.updateRecipient(report, kind, toName)) {
            throw new ConflictException("report", "the report was changed meanwhile, reload it");
        }
        repository.insertForwarding(report, kind, toName, reason.trim());
        Report forwarded = require(report.getReferenceNumber());
        if (!"RECEIVED".equals(forwarded.getStatus())) {
            handovers.handOver(forwarded);
            forwarded = require(report.getReferenceNumber());
        }
        return forwarded;
    }

    public List<Forwarding> forwardings(String referenceNumber) {
        List<Forwarding> all = repository.forwardings(require(referenceNumber).getId());
        return access.isCoordinator() ? all : all.stream().map(Forwarding::forResident).toList();
    }

    public List<StatusChange> history(String referenceNumber) {
        Report report = require(referenceNumber);
        if (!access.isStaffOf(report)) {
            throw new ForbiddenException("only a coordinator or the unit of the report may do this");
        }
        return repository.history(report.getId());
    }

    public List<Category> categories() {
        return repository.categories();
    }

    private Report require(String referenceNumber) {
        Report report = repository.loadByReferenceNumber(referenceNumber.trim().toUpperCase(Locale.ROOT));
        if (report == null) {
            throw new NotFoundException("no report with reference number " + referenceNumber);
        }
        return report;
    }

    private Report visible(Report report) {
        return access.isStaffOf(report) ? report : report.forResident();
    }

    private Report bySubmissionKey(String key) {
        return key == null ? null : repository.loadBySubmissionKey(key);
    }

    /** Fills address code and municipality. Fail open, a register that is down never blocks a report. */
    private void locate(Report report) {
        AddressInfo byAddress = ask(() -> addressRegister.lookup(report.getAddress()));
        AddressInfo byPoint = report.getLatitude() == null ? null : ask(() -> addressRegister.lookupPoint(
                report.getLatitude().doubleValue(), report.getLongitude().doubleValue()));
        boolean chosenOnMap = byPoint != null && report.getAddress().equals(byPoint.fullAddress());
        report.setAddressVerified(chosenOnMap || byAddress != null && byAddress.code() != null);
        report.setAddressCode(byAddress == null ? null : byAddress.code());
        AddressInfo place = byPoint != null ? byPoint : byAddress;
        if (place != null) {
            report.setMunicipalityCode(place.municipalityCode());
            report.setMunicipalityName(place.municipalityName());
        }
        Recipient recipient = recipients.find(repository.category(report.getCategoryCode()),
                report.getMunicipalityName(), report.getLatitude(), report.getLongitude());
        report.setRecipientKind(recipient.kind().name());
        report.setRecipientName(recipient.name());
    }

    private static AddressInfo ask(Supplier<AddressInfo> question) {
        try {
            return question.get();
        } catch (AddressRegisterException e) {
            log.warn("place not verified: {}", e.getMessage());
            return null;
        }
    }

    private static String requireUuid(String deviceId) {
        if (deviceId == null || !UUID_PATTERN.matcher(deviceId).matches()) {
            throw new ApiClientException("X-Device-Id must be a UUID");
        }
        return deviceId.toLowerCase(Locale.ROOT);
    }

    /** Also cleans the text fields of the report. */
    void validate(Report r) {
        r.setAddress(r.getAddress().trim());
        r.setDescription(r.getDescription().trim());
        r.setReporterContact(blankToNull(r.getReporterContact()));
        r.setSubmissionKey(blankToNull(r.getSubmissionKey()));
        requireKnownCategory(r.getCategoryCode());
        if (r.getDescription().length() < 10) {
            throw new ApiClientException("description must be at least 10 characters");
        }
        if ((r.getLatitude() == null) != (r.getLongitude() == null)) {
            throw new ApiClientException("latitude and longitude must be given together");
        }
    }

    private void validate(ReportQueryParams p) {
        if (blankToNull(p.getCategoryCode()) != null) {
            requireKnownCategory(p.getCategoryCode());
        }
        if (blankToNull(p.getStatus()) != null && !ReportStatus.isKnown(p.getStatus())) {
            throw new ApiClientException("unknown status: " + p.getStatus());
        }
        if (blankToNull(p.getChannel()) != null && !CHANNELS.contains(p.getChannel())) {
            throw new ApiClientException("unknown channel: " + p.getChannel());
        }
        if (p.getLimit() != null && p.getLimit() < 0 || p.getOffset() != null && p.getOffset() < 0) {
            throw new ApiClientException("limit and offset must not be negative");
        }
    }

    private void requireKnownCategory(String code) {
        if (!repository.categoryExists(code)) {
            throw new ApiClientException("unknown categoryCode: " + code);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
