package ee.taltech.ite4120.reports.api;

import jakarta.validation.constraints.Size;

/** Why the unit returns a report. Blank is refused by the service with a clear message. */
public class ReturnRequest {

    @Size(max = 500)
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
