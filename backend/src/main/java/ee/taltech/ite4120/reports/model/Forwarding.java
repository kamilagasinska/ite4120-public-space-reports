package ee.taltech.ite4120.reports.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** One forwarding of a report from one recipient to another. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Forwarding {

    private String fromName;
    private String toKind;
    private String toName;
    private String reason;
    private OffsetDateTime sysCreatedAt;
    private String sysCreatedBy;

    public Forwarding forResident() {
        sysCreatedBy = null;
        return this;
    }

    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }
    public String getToKind() { return toKind; }
    public void setToKind(String toKind) { this.toKind = toKind; }
    public String getToName() { return toName; }
    public void setToName(String toName) { this.toName = toName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public OffsetDateTime getSysCreatedAt() { return sysCreatedAt; }
    public void setSysCreatedAt(OffsetDateTime sysCreatedAt) { this.sysCreatedAt = sysCreatedAt; }
    public String getSysCreatedBy() { return sysCreatedBy; }
    public void setSysCreatedBy(String sysCreatedBy) { this.sysCreatedBy = sysCreatedBy; }
}
