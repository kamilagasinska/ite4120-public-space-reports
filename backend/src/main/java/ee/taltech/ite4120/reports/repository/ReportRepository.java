package ee.taltech.ite4120.reports.repository;

import ee.taltech.ite4120.reports.dto.ReportQueryParams;
import ee.taltech.ite4120.reports.model.Category;
import ee.taltech.ite4120.reports.model.Forwarding;
import ee.taltech.ite4120.reports.model.Report;
import ee.taltech.ite4120.reports.model.StatusChange;
import java.util.List;
import java.util.Map;
import org.helex.commons.db.bean.PgBeanProcessor;
import org.helex.commons.db.repo.BaseRepository;
import org.helex.commons.db.sql.SqlBuilder;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.model.QueryResult;
import org.springframework.stereotype.Repository;

/** Data access for the reports schema. */
@Repository
public class ReportRepository extends BaseRepository {

    private static final String DAYS_OPEN =
            "floor(extract(epoch from (current_timestamp - r.sys_created_at)) / 86400)::int";

    /** The coordinator has reacted in some way. */
    private static final String ANSWERED = "(exists (select 1 from reports.status_change s where s.report_id = r.id "
            + "and s.sys_status = 'A') or exists (select 1 from reports.message m where m.report_id = r.id "
            + "and m.kind = 'UPDATE' and m.sys_status = 'A'))";

    private static final String SELECT = "select r.*, r.sys_created_at as submitted_at, "
            + DAYS_OPEN + " as days_open, c.name as category_name, c.responsible_unit, "
            + "(select s.reason from reports.status_change s where s.report_id = r.id and s.to_status = 'REJECTED' "
            + "and s.sys_status = 'A' order by s.id desc limit 1) as rejection_reason, "
            + "(select count(*)::int from reports.message m where m.report_id = r.id and m.kind = 'REMINDER' "
            + "and m.sys_status = 'A') as reminders, "
            + "(select 1 + count(*)::int from reports.sighting g where g.report_id = r.id and g.sys_status = 'A') as seen_by, "
            + "case when not " + ANSWERED + " then (r.sys_created_at + interval '15 days')::date end as respond_by, "
            + "case when not " + ANSWERED + " then 15 - " + DAYS_OPEN + " end as days_to_respond "
            + "from reports.report r join reports.category c on c.code = r.category_code ";

    private static final Map<String, String> SORT = Map.of(
            "referenceNumber", "r.reference_number",
            "categoryCode", "r.category_code",
            "address", "r.address",
            "status", "r.status",
            "submittedAt", "r.sys_created_at",
            "daysOpen", DAYS_OPEN);

    private final PgBeanProcessor bp = new PgBeanProcessor(Report.class);
    private final PgBeanProcessor categoryBp = new PgBeanProcessor(Category.class);
    private final PgBeanProcessor forwardingBp = new PgBeanProcessor(Forwarding.class);
    private final PgBeanProcessor statusChangeBp = new PgBeanProcessor(StatusChange.class);

    public Report loadByReferenceNumber(String referenceNumber) {
        return getBean(SELECT + "where r.reference_number = ? and r.sys_status = 'A'", bp, referenceNumber);
    }

    public Report load(Long id) {
        return getBean(SELECT + "where r.id = ? and r.sys_status = 'A'", bp, id);
    }

    public void setPublished(Long id, boolean published) {
        jdbcTemplate.update("update reports.report set published = ? where id = ? and published <> ?", published, id, published);
    }

    public Report loadBySubmissionKey(String key) {
        return getBean(SELECT + "where r.submission_key = ? and r.sys_status = 'A'", bp, key);
    }

    public QueryResult<Report> search(ReportQueryParams p) {
        return query(p, this::count, this::list);
    }

    private Integer count(ReportQueryParams p) {
        SqlBuilder sb = new SqlBuilder("select count(1) from reports.report r where r.sys_status = 'A'");
        sb.append(filter(p));
        return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }

    private List<Report> list(ReportQueryParams p) {
        SqlBuilder sb = new SqlBuilder(SELECT + "where r.sys_status = 'A'");
        sb.append(filter(p));
        if (p.getSort() == null || p.getSort().isEmpty()) {
            /* The report that has waited longest comes first. */
            sb.append("order by r.sys_created_at, r.id");
        } else {
            sb.append(order(p));
        }
        sb.append(limit(p));
        return getBeans(sb.getSql(), bp, sb.getParams());
    }

    /** An unknown sort key is the caller's fault, so it becomes a 400. */
    private SqlBuilder order(ReportQueryParams p) {
        try {
            return order(p, SORT);
        } catch (IllegalArgumentException e) {
            throw new ApiClientException("unknown sort key; sortable: "
                    + String.join(", ", SORT.keySet().stream().sorted().toList()));
        }
    }

    private SqlBuilder filter(ReportQueryParams p) {
        SqlBuilder sb = new SqlBuilder();
        if (p.getCategoryCode() != null && !p.getCategoryCode().isBlank()) {
            sb.and("r.category_code = ?", p.getCategoryCode());
        }
        if (p.getStatus() != null && !p.getStatus().isBlank()) {
            sb.and("r.status = ?", p.getStatus());
        }
        if (p.getMunicipalityCode() != null && !p.getMunicipalityCode().isBlank()) {
            sb.and("r.municipality_code = ?", p.getMunicipalityCode());
        }
        if (p.getChannel() != null && !p.getChannel().isBlank()) {
            sb.and("r.channel = ?", p.getChannel());
        }
        return sb;
    }

    public Long insert(Report r) {
        String sql = """
                insert into reports.report (reference_number, category_code, address, address_code, address_verified,
                                            latitude, longitude, description, reporter_contact, submission_key,
                                            municipality_code, municipality_name, channel, reporter_id, ip_hash, signed_digest,
                                            recipient_kind, recipient_name)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id""";
        return queryForObject(sql, Long.class, r.getReferenceNumber(), r.getCategoryCode(), r.getAddress(),
                r.getAddressCode(), r.getAddressVerified(), r.getLatitude(), r.getLongitude(),
                r.getDescription(), r.getReporterContact(), r.getSubmissionKey(), r.getMunicipalityCode(),
                r.getMunicipalityName(), r.getChannel(), r.getReporterId(), r.getIpHash(), r.getSignedDigest(),
                r.getRecipientKind(), r.getRecipientName());
    }

    /** Changes the status only if it is still the expected one, so two coordinators cannot overwrite each other. */
    public boolean updateStatus(Long id, String from, String to) {
        return jdbcTemplate.update(
                "update reports.report set status = ? where id = ? and status = ? and sys_status = 'A'",
                to, id, from) == 1;
    }

    public void insertStatusChange(Long reportId, String from, String to, String reason, boolean falseReport) {
        jdbcTemplate.update(
                "insert into reports.status_change (report_id, from_status, to_status, reason, false_report) "
                        + "values (?, ?, ?, ?, ?)", reportId, from, to, reason, falseReport);
    }

    public List<StatusChange> history(Long reportId) {
        return getBeans("select s.* from reports.status_change s "
                + "where s.report_id = ? and s.sys_status = 'A' order by s.id", statusChangeBp, reportId);
    }

    /** Changes the recipient only if nobody else did and the report is still open. */
    public boolean updateRecipient(Report r, String kind, String name) {
        return jdbcTemplate.update("update reports.report set recipient_kind = ?, recipient_name = ? "
                + "where id = ? and recipient_name = ? and status in ('RECEIVED', 'ACCEPTED', 'IN_PROGRESS') and sys_status = 'A'",
                kind, name, r.getId(), r.getRecipientName()) == 1;
    }

    public void insertForwarding(Report from, String kind, String name, String reason) {
        jdbcTemplate.update("insert into reports.forwarding (report_id, from_name, to_kind, to_name, reason) "
                + "values (?, ?, ?, ?, ?)", from.getId(), from.getRecipientName(), kind, name, reason);
    }

    public List<Forwarding> forwardings(Long reportId) {
        return getBeans("select f.* from reports.forwarding f "
                + "where f.report_id = ? and f.sys_status = 'A' order by f.id", forwardingBp, reportId);
    }

    public Category category(String code) {
        return getBean("select c.* from reports.category c where c.code = ?", categoryBp, code);
    }

    public List<Category> categories() {
        return getBeans("select c.* from reports.category c order by c.name", categoryBp);
    }

    public boolean categoryExists(String code) {
        Integer n = queryForObject("select count(1) from reports.category where code = ?", Integer.class, code);
        return n != null && n > 0;
    }
}
