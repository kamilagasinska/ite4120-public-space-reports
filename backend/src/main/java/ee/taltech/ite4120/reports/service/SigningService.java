package ee.taltech.ite4120.reports.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.taltech.ite4120.reports.model.Report;
import ee.taltech.ite4120.reports.model.Reporter;
import ee.taltech.ite4120.reports.model.Signing;
import ee.taltech.ite4120.reports.model.Standing;
import ee.taltech.ite4120.reports.repository.SigningRepository;
import ee.taltech.ite4120.reports.smartid.SmartIdAdapter;
import ee.taltech.ite4120.reports.smartid.TestPersons;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.exception.NotFoundException;
import org.springframework.stereotype.Service;

/** Signing a report with Smart-ID. Fail closed, nothing is stored as signed without a verified signature. */
@Service
public class SigningService {

    private final SigningRepository repository;
    private final SmartIdAdapter smartId;
    private final ReportService reports;
    private final KeyedHash hash;
    private final ObjectMapper json = new ObjectMapper();

    public SigningService(SigningRepository repository, SmartIdAdapter smartId, ReportService reports, KeyedHash hash) {
        this.repository = repository;
        this.smartId = smartId;
        this.reports = reports;
        this.hash = hash;
    }

    /** What is kept between start and signature. The person is the demo document, never typed by anyone. */
    private record Payload(int person, String categoryCode, String address, BigDecimal latitude, BigDecimal longitude,
            String description, String reporterContact) {

        Report toReport() {
            Report r = new Report();
            r.setCategoryCode(categoryCode);
            r.setAddress(address);
            r.setLatitude(latitude);
            r.setLongitude(longitude);
            r.setDescription(description);
            r.setReporterContact(reporterContact);
            return r;
        }

        /** The exact text that is signed. */
        byte[] signedText() {
            return String.join("\n", "REPORTS.01", categoryCode, address, number(latitude), number(longitude),
                    description, reporterContact == null ? "" : reporterContact).getBytes(StandardCharsets.UTF_8);
        }

        private static String number(BigDecimal n) {
            return n == null ? "" : n.stripTrailingZeros().toPlainString();
        }
    }

    public record Started(String token, String verificationCode) {}

    /** The standing only when signed. The submission key is the sender's proof for later. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record State(String state, String referenceNumber, String submissionKey, Standing standing, String name) {

        State(String state, String referenceNumber) {
            this(state, referenceNumber, null, null, null);
        }
    }

    public Started start(Report report, String document) {
        int person = TestPersons.index(document);
        if (person < 0) {
            throw new ApiClientException("person must be one of the Smart-ID demo persons");
        }
        reports.validate(report);
        reports.checkLimit(reporter(document));
        Payload p = new Payload(person, report.getCategoryCode(), report.getAddress(), report.getLatitude(),
                report.getLongitude(), report.getDescription(), report.getReporterContact());
        SmartIdAdapter.Session session = smartId.start(document, p.signedText());
        String token = UUID.randomUUID().toString();
        repository.insert(token, session.id(), write(p));
        return new Started(token, session.verificationCode());
    }

    public State state(String token) {
        Signing s = repository.load(token);
        if (s == null) {
            throw new NotFoundException("no such signing");
        }
        if ("SIGNED".equals(s.getState())) {
            Report report = reports.signedReport(token);
            return new State("SIGNED", s.getReferenceNumber(), report.getSubmissionKey(),
                    report.getReporterId() == null ? null : reports.standing(report.getReporterId()), null);
        }
        if (!"PENDING".equals(s.getState())) {
            return new State(s.getState(), s.getReferenceNumber());
        }
        if (Boolean.TRUE.equals(s.getExpired())) {
            return finish(s, "EXPIRED");
        }
        Payload p = read(s.getPayload());
        String document = TestPersons.document(p.person());
        SmartIdAdapter.Poll poll = smartId.poll(s.getSessionId(), document, p.signedText());
        return switch (poll.result()) {
            case RUNNING -> new State("PENDING", null);
            case REFUSED -> finish(s, "REFUSED");
            case EXPIRED -> finish(s, "EXPIRED");
            case FAILED -> finish(s, "FAILED");
            case SIGNED -> signed(s, p, document, poll.name());
        };
    }

    private State signed(Signing s, Payload p, String document, String name) {
        try {
            Report report = reports.submitSigned(p.toReport(), reporter(document), s.getToken(),
                    smartId.digest(p.signedText()));
            repository.finish(s.getId(), "SIGNED", report.getId());
            return new State("SIGNED", report.getReferenceNumber(), report.getSubmissionKey(),
                    reports.standing(reporter(document)), name);
        } catch (LimitExceededException e) {
            repository.finish(s.getId(), "FAILED", null);
            throw e;
        }
    }

    private State finish(Signing s, String state) {
        repository.finish(s.getId(), state, null);
        Signing now = repository.load(s.getToken());
        return new State(now.getState(), now.getReferenceNumber());
    }

    /** The person part of the document number, so a new device of the same person is the same reporter. */
    private Reporter reporter(String document) {
        return new Reporter(Reporter.Kind.SIGNED, hash.of("person", TestPersons.person(document)));
    }

    private String write(Payload p) {
        try {
            return json.writeValueAsString(p);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Payload read(String text) {
        try {
            return json.readValue(text, Payload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
