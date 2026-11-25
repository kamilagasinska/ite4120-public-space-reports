package ee.taltech.ite4120.reports.service;

import ee.taltech.ite4120.reports.addressregister.AddressInfo;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterAdapter;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterException;
import ee.taltech.ite4120.reports.dto.ReportQueryParams;
import ee.taltech.ite4120.reports.model.Category;
import ee.taltech.ite4120.reports.model.Report;
import ee.taltech.ite4120.reports.model.ReportStatus;
import ee.taltech.ite4120.reports.model.StatusChange;
import ee.taltech.ite4120.reports.repository.ReportRepository;
import java.util.List;
import java.util.Locale;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.exception.ConflictException;
import org.helex.commons.exception.NotFoundException;
import org.helex.commons.model.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The business rules of REPORTS.01. */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final int REFERENCE_ATTEMPTS = 5;

    private final ReportRepository repository;
    private final AddressRegisterAdapter addressRegister;
    private final ReportAccess access;

    public ReportService(ReportRepository repository, AddressRegisterAdapter addressRegister, ReportAccess access) {
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
        return visible(require(referenceNumber));
    }

    /**
     * Deliberately not transactional. The register call must not hold a database
     * connection, and the single insert is atomic by itself.
     */
    public Submission submit(Report report) {
        validate(report);
        Report existing = bySubmissionKey(report.getSubmissionKey());
        if (existing != null) {
            return new Submission(visible(existing), false);
        }
        AddressInfo confirmed = confirm(report.getAddress());
        report.setAddressVerified(confirmed != null);
        report.setAddressCode(confirmed == null ? null : confirmed.code());
        for (int attempt = 1; ; attempt++) {
            report.setReferenceNumber(ReferenceNumbers.next());
            try {
                repository.insert(report);
                return new Submission(find(report.getReferenceNumber()), true);
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

    @Transactional
    public Report changeStatus(String referenceNumber, String toStatus, String reason) {
        access.requireCoordinator();
        if (!ReportStatus.isKnown(toStatus)) {
            throw new ApiClientException("unknown toStatus: " + toStatus);
        }
        Report report = require(referenceNumber);
        ReportStatus from = ReportStatus.valueOf(report.getStatus());
        ReportStatus to = ReportStatus.valueOf(toStatus);
        if (!from.canBecome(to)) {
            throw new ConflictException("report", "status cannot change from " + from + " to " + to);
        }
        String cleanReason = blankToNull(reason);
        if (to == ReportStatus.REJECTED && cleanReason == null) {
            throw new ApiClientException("reason is required when a report is rejected");
        }
        if (!repository.updateStatus(report.getId(), from.name(), to.name())) {
            throw new ConflictException("report", "status is no longer " + from + ", reload the report");
        }
        repository.insertStatusChange(report.getId(), from.name(), to.name(), cleanReason);
        return require(report.getReferenceNumber());
    }

    public List<StatusChange> history(String referenceNumber) {
        access.requireCoordinator();
        return repository.history(require(referenceNumber).getId());
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
        return access.isCoordinator() ? report : report.forResident();
    }

    private Report bySubmissionKey(String key) {
        return key == null ? null : repository.loadBySubmissionKey(key);
    }

    /** Fail open. A register that is down never blocks a report. */
    private AddressInfo confirm(String address) {
        try {
            return addressRegister.lookup(address);
        } catch (AddressRegisterException e) {
            log.warn("address not verified: {}", e.getMessage());
            return null;
        }
    }

    private void validate(Report r) {
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
