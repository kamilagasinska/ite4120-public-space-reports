package ee.taltech.ite4120.reports.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Where a report is forwarded and why. The name is needed unless it is the state roads. */
public class ForwardRequest {

    @NotBlank
    private String kind;

    @Size(max = 220)
    private String name;

    @NotBlank
    @Size(max = 500)
    private String reason;

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
