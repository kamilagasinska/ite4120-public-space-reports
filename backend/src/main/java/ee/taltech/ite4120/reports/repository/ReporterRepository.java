package ee.taltech.ite4120.reports.repository;

import ee.taltech.ite4120.reports.model.Reporter;
import java.util.List;
import org.helex.commons.db.repo.BaseRepository;
import org.springframework.stereotype.Repository;

/** Data access for reporters and the numbers the limits need. */
@Repository
public class ReporterRepository extends BaseRepository {

    /** Ages in seconds of the reports of the last 24 hours, newest first. */
    public record Usage(List<Long> ages) {

        public int lastDay() {
            return ages.size();
        }

        public Long secondsSinceLast() {
            return ages.isEmpty() ? null : ages.getFirst();
        }

        public long secondsSinceNth(int n) {
            return ages.get(n - 1);
        }
    }

    private static final String AGES = "select floor(extract(epoch from (clock_timestamp() - r.sys_created_at)))::bigint "
            + "from reports.report r where r.sys_status = 'A' "
            + "and r.sys_created_at > clock_timestamp() - interval '24 hours' and ";
    private static final String NEWEST_FIRST = " order by r.sys_created_at desc, r.id desc";
    private static final String OF_REPORTER = "r.reporter_id = (select p.id from reports.reporter p "
            + "where p.kind = ? and p.key_hash = ? and p.sys_status = 'A')";

    public void lock(String name) {
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, "reports:" + name);
    }

    public Usage guestUsage(String ipHash) {
        return ages("r.channel = 'GUEST' and r.ip_hash = ?", ipHash);
    }

    public Usage unsignedUsage(String ipHash) {
        return ages("r.channel in ('GUEST', 'DEVICE') and r.ip_hash = ?", ipHash);
    }

    public Usage usage(Reporter reporter) {
        return ages(OF_REPORTER, reporter.kind().name(), reporter.keyHash());
    }

    private Usage ages(String condition, Object... params) {
        return new Usage(jdbcTemplate.queryForList(AGES + condition + NEWEST_FIRST, Long.class, params));
    }

    /** Resolved reports of the reporter, counted from the last false report. */
    public int resolvedSinceFalseReport(Reporter reporter) {
        String sql = """
                with changes as (
                    select s.to_status, s.false_report, s.id
                      from reports.status_change s join reports.report r on r.id = s.report_id
                     where s.sys_status = 'A' and r.sys_status = 'A' and %s)
                select count(*) from changes
                 where to_status = 'RESOLVED'
                   and id > coalesce((select max(id) from changes where false_report), 0)""".formatted(OF_REPORTER);
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, reporter.kind().name(), reporter.keyHash());
        return n == null ? 0 : n;
    }

    /** The id of the reporter, stored on first use. Call under the reporter's lock. */
    public Long touch(Reporter reporter) {
        List<Long> ids = jdbcTemplate.queryForList(
                "update reports.reporter set last_report_at = clock_timestamp() "
                        + "where kind = ? and key_hash = ? and sys_status = 'A' returning id",
                Long.class, reporter.kind().name(), reporter.keyHash());
        return !ids.isEmpty() ? ids.getFirst() : queryForObject(
                "insert into reports.reporter (kind, key_hash, last_report_at) values (?, ?, clock_timestamp()) returning id",
                Long.class, reporter.kind().name(), reporter.keyHash());
    }

    /** IP hashes are needed for one day only. */
    public int clearOldIpHashes() {
        return jdbcTemplate.update("update reports.report set ip_hash = null "
                + "where ip_hash is not null and sys_created_at < clock_timestamp() - interval '24 hours'");
    }

    /** Forgets devices without a report for 6 months. The reports stay, without the link. */
    public int forgetOldDevices() {
        String old = "select id from reports.reporter where kind = 'DEVICE' "
                + "and last_report_at < clock_timestamp() - interval '6 months'";
        jdbcTemplate.update("update reports.report set reporter_id = null where reporter_id in (" + old + ")");
        return jdbcTemplate.update("delete from reports.reporter where id in (" + old + ")");
    }

    public int expireSignings() {
        return jdbcTemplate.update("update reports.signing set state = 'EXPIRED', payload = null "
                + "where state = 'PENDING' and sys_created_at < clock_timestamp() - interval '5 minutes'");
    }
}
