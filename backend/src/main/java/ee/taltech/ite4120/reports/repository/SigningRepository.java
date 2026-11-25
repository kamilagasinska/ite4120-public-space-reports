package ee.taltech.ite4120.reports.repository;

import ee.taltech.ite4120.reports.model.Signing;
import org.helex.commons.db.bean.PgBeanProcessor;
import org.helex.commons.db.repo.BaseRepository;
import org.springframework.stereotype.Repository;

/** Data access for signings. */
@Repository
public class SigningRepository extends BaseRepository {

    private final PgBeanProcessor bp = new PgBeanProcessor(Signing.class);

    public Signing load(String token) {
        return getBean("select s.id, s.token, s.session_id, s.payload::text as payload, s.state, r.reference_number, "
                + "s.sys_created_at < clock_timestamp() - interval '5 minutes' as expired "
                + "from reports.signing s left join reports.report r on r.id = s.report_id "
                + "where s.token = ? and s.sys_status = 'A'", bp, token);
    }

    public void insert(String token, String sessionId, String payload) {
        jdbcTemplate.update("insert into reports.signing (token, session_id, payload) values (?, ?, ?::jsonb)",
                token, sessionId, payload);
    }

    /** Ends a pending signing. A signing that already ended stays as it is. */
    public void finish(Long id, String state, Long reportId) {
        jdbcTemplate.update("update reports.signing set state = ?, report_id = ?, payload = null "
                + "where id = ? and state = 'PENDING'", state, reportId, id);
    }
}
