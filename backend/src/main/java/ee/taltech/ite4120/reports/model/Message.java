package ee.taltech.ite4120.reports.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** An update of the coordinator, or a reminder or "the problem remains" of the sender. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    private String kind;
    private String text;
    private LocalDate expectedOn;
    private OffsetDateTime sysCreatedAt;
    private String sysCreatedBy;

    public Message forResident() {
        sysCreatedBy = null;
        return this;
    }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public LocalDate getExpectedOn() { return expectedOn; }
    public void setExpectedOn(LocalDate expectedOn) { this.expectedOn = expectedOn; }
    public OffsetDateTime getSysCreatedAt() { return sysCreatedAt; }
    public void setSysCreatedAt(OffsetDateTime sysCreatedAt) { this.sysCreatedAt = sysCreatedAt; }
    public String getSysCreatedBy() { return sysCreatedBy; }
    public void setSysCreatedBy(String sysCreatedBy) { this.sysCreatedBy = sysCreatedBy; }
}
