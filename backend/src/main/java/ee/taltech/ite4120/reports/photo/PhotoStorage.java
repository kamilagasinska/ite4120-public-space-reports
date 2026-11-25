package ee.taltech.ite4120.reports.photo;

import ee.taltech.ite4120.reports.model.PhotoInfo;
import java.util.List;

/** Where photos are kept. In production this is object storage. */
public interface PhotoStorage {

    Long add(Long reportId, String kind, byte[] content);

    List<PhotoInfo> list(Long reportId);

    /** Null when the report has no such photo. */
    byte[] load(Long reportId, Long photoId);
}
