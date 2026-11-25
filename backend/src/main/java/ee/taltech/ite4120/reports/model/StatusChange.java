package ee.taltech.ite4120.reports.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;

/** One recorded change of a report status. */
public class StatusChange {

    private Boolean falseReport;
    @JsonIgnore
    private Long reportId;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private OffsetDateTime sysCreatedAt;
    private String sysCreatedBy;

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public OffsetDateTime getSysCreatedAt() { return sysCreatedAt; }
    public void setSysCreatedAt(OffsetDateTime sysCreatedAt) { this.sysCreatedAt = sysCreatedAt; }
    public String getSysCreatedBy() { return sysCreatedBy; }
    public void setSysCreatedBy(String sysCreatedBy) { this.sysCreatedBy = sysCreatedBy; }
    public Boolean getFalseReport() { return falseReport; }
    public void setFalseReport(Boolean falseReport) { this.falseReport = falseReport; }
}
