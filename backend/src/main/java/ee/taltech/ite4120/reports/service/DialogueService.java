package ee.taltech.ite4120.reports.service;

import ee.taltech.ite4120.reports.model.MapDetail;
import ee.taltech.ite4120.reports.model.MapPoint;
import ee.taltech.ite4120.reports.model.Message;
import ee.taltech.ite4120.reports.model.PhotoInfo;
import ee.taltech.ite4120.reports.model.Report;
import ee.taltech.ite4120.reports.model.ReportStatus;
import ee.taltech.ite4120.reports.photo.PhotoCleaner;
import ee.taltech.ite4120.reports.photo.PhotoStorage;
import ee.taltech.ite4120.reports.repository.DialogueRepository;
import ee.taltech.ite4120.reports.repository.ReportRepository;
import ee.taltech.ite4120.reports.repository.ReporterRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.exception.ConflictException;
import org.helex.commons.exception.ForbiddenException;
import org.helex.commons.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What happens between the sender and the coordinator after a report, and the public map. */
@Service
public class DialogueService {

    private static final int PHOTOS = 5;

    private final ReportRepository reports;
    private final ReporterRepository locks;
    private final DialogueRepository repository;
    private final PhotoStorage photos;
    private final PhotoCleaner cleaner;
    private final ReportAccess access;

    public DialogueService(ReportRepository reports, ReporterRepository locks, DialogueRepository repository,
            PhotoStorage photos, PhotoCleaner cleaner, ReportAccess access) {
        this.reports = reports;
        this.locks = locks;
        this.repository = repository;
        this.photos = photos;
        this.cleaner = cleaner;
        this.access = access;
    }

    /** The sender adds a photo of the problem, the coordinator one of the result. */
    @Transactional
    public PhotoInfo addPhoto(String referenceNumber, String key, byte[] file) {
        boolean coordinator = key == null && access.isCoordinator();
        Report report = coordinator ? require(referenceNumber) : own(referenceNumber, key);
        if (!coordinator && status(report).isClosed()) {
            throw new ConflictException("report", "the report is closed");
        }
        byte[] clean = cleaner.clean(file);
        String kind = coordinator ? "AFTER" : "BEFORE";
        locks.lock("photos:" + report.getId());
        if (photos.list(report.getId()).stream().filter(p -> p.kind().equals(kind)).count() >= PHOTOS) {
            throw new ApiClientException("a report has at most 5 photos");
        }
        return new PhotoInfo(photos.add(report.getId(), kind, clean), kind);
    }

    public List<PhotoInfo> photos(String referenceNumber, String key) {
        return photos.list(ownOrCoordinator(referenceNumber, key).getId());
    }

    public byte[] photo(String referenceNumber, String key, Long photoId) {
        return found(photos.load(ownOrCoordinator(referenceNumber, key).getId(), photoId));
    }

    public void update(String referenceNumber, String text, LocalDate expectedOn) {
        access.requireCoordinator();
        Report report = require(referenceNumber);
        if (status(report).isClosed()) {
            throw new ConflictException("report", "the report is closed");
        }
        repository.addMessage(report.getId(), "UPDATE", text.trim(), expectedOn);
    }

    public List<Message> messages(String referenceNumber) {
        List<Message> all = repository.messages(require(referenceNumber).getId());
        return access.isCoordinator() ? all : all.stream().map(Message::forResident).toList();
    }

    @Transactional
    public void remind(String referenceNumber, String key) {
        Report report = own(referenceNumber, key);
        if (status(report).isClosed()) {
            throw new ConflictException("report", "the report is closed");
        }
        locks.lock("reminder:" + report.getId());
        if (repository.remindedRecently(report.getId())) {
            throw new ConflictException("report", "a reminder is possible once in 3 days");
        }
        repository.addMessage(report.getId(), "REMINDER", null, null);
    }

    /** The sender reopens a resolved report. */
    @Transactional
    public Report remains(String referenceNumber, String key, String text) {
        Report report = own(referenceNumber, key);
        if (status(report) != ReportStatus.RESOLVED) {
            throw new ConflictException("report", "the report is not resolved");
        }
        if (!repository.resolvedWithin30Days(report.getId())) {
            throw new ConflictException("report", "it was resolved more than 30 days ago, send a new report");
        }
        if (!reports.updateStatus(report.getId(), "RESOLVED", "ACCEPTED")) {
            throw new ConflictException("report", "the report was changed meanwhile, reload it");
        }
        reports.insertStatusChange(report.getId(), "RESOLVED", "ACCEPTED", text.trim(), false);
        repository.addMessage(report.getId(), "REMAINS", text.trim(), null);
        Report changed = require(referenceNumber);
        return access.isCoordinator() ? changed : changed.forResident();
    }

    public List<MapPoint> map(BigDecimal west, BigDecimal south, BigDecimal east, BigDecimal north, String category) {
        if (category != null && !reports.categoryExists(category)) {
            throw new ApiClientException("unknown categoryCode: " + category);
        }
        return repository.points(west, south, east, north, category);
    }

    public MapDetail mapDetail(Long id) {
        MapPoint point = visible(id);
        Report report = reports.load(id);
        boolean open = published(report, point);
        return new MapDetail(point, report.getRecipientName(), open ? report.getAddress() : null,
                open ? report.getDescription() : null, open ? photos.list(id) : List.of());
    }

    public byte[] mapPhoto(Long id, Long photoId) {
        MapPoint point = visible(id);
        return found(published(reports.load(id), point) ? photos.load(id, photoId) : null);
    }

    private static boolean published(Report report, MapPoint point) {
        return Boolean.TRUE.equals(report.getPublished()) && !"REJECTED".equals(point.status());
    }

    private MapPoint visible(Long id) {
        MapPoint point = repository.point(id);
        if (point == null) {
            throw new NotFoundException("no such report on the map");
        }
        return point;
    }

    private static byte[] found(byte[] content) {
        if (content == null) {
            throw new NotFoundException("no such photo");
        }
        return content;
    }

    private Report ownOrCoordinator(String referenceNumber, String key) {
        if (access.isCoordinator()) {
            return require(referenceNumber);
        }
        if (key == null) {
            throw new ForbiddenException("only a coordinator or the sender may do this");
        }
        return own(referenceNumber, key);
    }

    /** The report, if the key is the one it was sent with. A wrong key looks like an unknown report. */
    private Report own(String referenceNumber, String key) {
        Report report = require(referenceNumber);
        byte[] given = (key == null ? "" : key).getBytes(StandardCharsets.UTF_8);
        if (report.getSubmissionKey() == null
                || !MessageDigest.isEqual(given, report.getSubmissionKey().getBytes(StandardCharsets.UTF_8))) {
            throw new NotFoundException("no report of yours with reference number " + referenceNumber);
        }
        return report;
    }

    private Report require(String referenceNumber) {
        Report report = reports.loadByReferenceNumber(referenceNumber.trim().toUpperCase(Locale.ROOT));
        if (report == null) {
            throw new NotFoundException("no report with reference number " + referenceNumber);
        }
        return report;
    }

    private static ReportStatus status(Report report) {
        return ReportStatus.valueOf(report.getStatus());
    }
}
