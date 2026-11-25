package ee.taltech.ite4120.reports.repository;

import ee.taltech.ite4120.reports.model.MapPoint;
import ee.taltech.ite4120.reports.model.Message;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.helex.commons.db.bean.PgBeanProcessor;
import org.helex.commons.db.repo.BaseRepository;
import org.springframework.stereotype.Repository;

/** Data access for messages and the public map. */
@Repository
public class DialogueRepository extends BaseRepository {

    /** What the public may see at all (REPORTS.01, Public map). */
    private static final String VISIBLE = """
            from reports.report r
            join reports.category c on c.code = r.category_code
            left join lateral (select max(s.sys_created_at) as at from reports.status_change s
                                where s.report_id = r.id and s.sys_status = 'A') last on true
            where r.sys_status = 'A' and c.on_map and r.latitude is not null
              and (r.status not in ('REJECTED', 'RESOLVED')
                   or (r.status = 'REJECTED' and last.at > clock_timestamp() - interval '3 days')
                   or (r.status = 'RESOLVED' and last.at > clock_timestamp() - interval '30 days'))
            """;
    private static final String POINT = "select r.id, round(r.latitude, 4) as latitude, round(r.longitude, 4) as longitude, "
            + "r.category_code, r.status, r.municipality_name, r.sys_created_at::date as submitted_on ";

    private final PgBeanProcessor messageBp = new PgBeanProcessor(Message.class);

    public void addMessage(Long reportId, String kind, String text, LocalDate expectedOn) {
        jdbcTemplate.update("insert into reports.message (report_id, kind, text, expected_on) values (?, ?, ?, ?)",
                reportId, kind, text, expectedOn);
    }

    public List<Message> messages(Long reportId) {
        return getBeans("select m.* from reports.message m where m.report_id = ? and m.sys_status = 'A' order by m.id",
                messageBp, reportId);
    }

    /** Whether the report was sent, or last reminded of, less than three days ago. */
    public boolean remindedRecently(Long reportId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                select greatest(r.sys_created_at, (select max(m.sys_created_at) from reports.message m
                                                    where m.report_id = r.id and m.kind = 'REMINDER' and m.sys_status = 'A'))
                       > clock_timestamp() - interval '3 days'
                  from reports.report r where r.id = ?""", Boolean.class, reportId));
    }

    public boolean resolvedWithin30Days(Long reportId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select coalesce(max(s.sys_created_at) > clock_timestamp() - interval '30 days', false) "
                        + "from reports.status_change s where s.report_id = ? and s.to_status = 'RESOLVED' and s.sys_status = 'A'",
                Boolean.class, reportId));
    }

    public List<MapPoint> points(BigDecimal west, BigDecimal south, BigDecimal east, BigDecimal north, String category) {
        List<Object> params = new ArrayList<>(List.of(west, east, south, north));
        String sql = POINT + VISIBLE + " and r.longitude between ? and ? and r.latitude between ? and ?";
        if (category != null) {
            sql += " and r.category_code = ?";
            params.add(category);
        }
        return jdbcTemplate.query(sql + " order by r.id desc limit 500", (rs, i) -> point(rs), params.toArray());
    }

    public MapPoint point(Long id) {
        List<MapPoint> found = jdbcTemplate.query(POINT + VISIBLE + " and r.id = ?", (rs, i) -> point(rs), id);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static MapPoint point(ResultSet rs) throws SQLException {
        return new MapPoint(rs.getLong("id"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
                rs.getString("category_code"), rs.getString("status"), rs.getString("municipality_name"),
                rs.getObject("submitted_on", LocalDate.class));
    }
}
