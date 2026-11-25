package ee.taltech.ite4120.reports.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The requested status change. */
public class StatusChangeRequest {

    /** Only with REJECTED, resets the trust of the sender. */
    private boolean falseReport;
    @NotBlank
    private String toStatus;
    @Size(max = 500)
    private String reason;

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isFalseReport() { return falseReport; }
    public void setFalseReport(boolean falseReport) { this.falseReport = falseReport; }
}
