package ee.taltech.ite4120.reports.photo;

import ee.taltech.ite4120.reports.model.PhotoInfo;
import java.util.List;
import org.helex.commons.db.repo.BaseRepository;
import org.springframework.stereotype.Repository;

/** Photos in the table reports.photo. */
@Repository
public class DatabasePhotoStorage extends BaseRepository implements PhotoStorage {

    @Override
    public Long add(Long reportId, String kind, byte[] content) {
        return jdbcTemplate.queryForObject(
                "insert into reports.photo (report_id, kind, content) values (?, ?, ?) returning id",
                Long.class, reportId, kind, content);
    }

    @Override
    public List<PhotoInfo> list(Long reportId) {
        return jdbcTemplate.query("select id, kind from reports.photo where report_id = ? and sys_status = 'A' order by id",
                (rs, i) -> new PhotoInfo(rs.getLong(1), rs.getString(2)), reportId);
    }

    @Override
    public byte[] load(Long reportId, Long photoId) {
        List<byte[]> found = jdbcTemplate.query(
                "select content from reports.photo where report_id = ? and id = ? and sys_status = 'A'",
                (rs, i) -> rs.getBytes(1), reportId, photoId);
        return found.isEmpty() ? null : found.getFirst();
    }
}
