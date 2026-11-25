package ee.taltech.ite4120.reports.repository;

import ee.taltech.ite4120.reports.dto.ReportQueryParams;
import ee.taltech.ite4120.reports.model.Category;
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

    private static final String SELECT = "select r.*, r.sys_created_at as submitted_at, "
            + DAYS_OPEN + " as days_open, c.name as category_name, c.responsible_unit "
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
    private final PgBeanProcessor statusChangeBp = new PgBeanProcessor(StatusChange.class);

    public Report loadByReferenceNumber(String referenceNumber) {
        return getBean(SELECT + "where r.reference_number = ? and r.sys_status = 'A'", bp, referenceNumber);
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
        return sb;
    }

    public Long insert(Report r) {
        String sql = """
                insert into reports.report (reference_number, category_code, address, address_code, address_verified,
                                            latitude, longitude, description, reporter_contact, submission_key)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id""";
        return queryForObject(sql, Long.class, r.getReferenceNumber(), r.getCategoryCode(), r.getAddress(),
                r.getAddressCode(), r.getAddressVerified(), r.getLatitude(), r.getLongitude(),
                r.getDescription(), r.getReporterContact(), r.getSubmissionKey());
    }

    /** Changes the status only if it is still the expected one, so two coordinators cannot overwrite each other. */
    public boolean updateStatus(Long id, String from, String to) {
        return jdbcTemplate.update(
                "update reports.report set status = ? where id = ? and status = ? and sys_status = 'A'",
                to, id, from) == 1;
    }

    public void insertStatusChange(Long reportId, String from, String to, String reason) {
        jdbcTemplate.update(
                "insert into reports.status_change (report_id, from_status, to_status, reason) values (?, ?, ?, ?)",
                reportId, from, to, reason);
    }

    public List<StatusChange> history(Long reportId) {
        return getBeans("select s.* from reports.status_change s "
                + "where s.report_id = ? and s.sys_status = 'A' order by s.id", statusChangeBp, reportId);
    }

    public List<Category> categories() {
        return getBeans("select c.* from reports.category c order by c.name", categoryBp);
    }

    public boolean categoryExists(String code) {
        Integer n = queryForObject("select count(1) from reports.category where code = ?", Integer.class, code);
        return n != null && n > 0;
    }
}
