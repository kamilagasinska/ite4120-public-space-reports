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

    /**
     * 10 for a confirmed report, 5 more when it came with a photo, 20 more when it is
     * solved. Only reports that changed after the last false report count.
     */
    public int points(Reporter reporter) {
        String sql = """
                with mine as (
                    select r.id, r.status, (select max(s.id) from reports.status_change s
                                             where s.report_id = r.id and s.sys_status = 'A') as last_change
                      from reports.report r where r.sys_status = 'A' and %s),
                reset as (
                    select coalesce(max(s.id), 0) as id from reports.status_change s join mine on mine.id = s.report_id
                     where s.false_report and s.sys_status = 'A')
                select coalesce(sum(
                    case when status in ('ACCEPTED', 'IN_PROGRESS', 'RESOLVED') then 10
                         + case when exists (select 1 from reports.photo p where p.report_id = mine.id
                                                and p.kind = 'BEFORE' and p.sys_status = 'A') then 5 else 0 end
                         + case when status = 'RESOLVED' then 20 else 0 end
                         else 0 end), 0)::int
                  from mine, reset where last_change > reset.id""".formatted(OF_REPORTER);
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, reporter.kind().name(), reporter.keyHash());
        return n == null ? 0 : n;
    }

    public Reporter load(Long id) {
        return jdbcTemplate.queryForObject("select kind, key_hash from reports.reporter where id = ?",
                (rs, i) -> new Reporter(Reporter.Kind.valueOf(rs.getString(1)), rs.getString(2)), id);
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

    /** Photos are kept for 6 months after the report was closed. */
    public int deleteOldPhotos() {
        return jdbcTemplate.update("delete from reports.photo p using reports.report r where r.id = p.report_id "
                + "and r.status in ('RESOLVED', 'REJECTED') and (select max(s.sys_created_at) from reports.status_change s "
                + "where s.report_id = r.id) < clock_timestamp() - interval '6 months'");
    }

    public int expireSignings() {
        return jdbcTemplate.update("update reports.signing set state = 'EXPIRED', payload = null "
                + "where state = 'PENDING' and sys_created_at < clock_timestamp() - interval '5 minutes'");
    }
}
