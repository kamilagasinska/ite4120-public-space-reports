package ee.taltech.ite4120.reports.api;

import ee.taltech.ite4120.reports.model.Report;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** The submitted report. Message shape is checked here, business rules in ReportService. */
public class ReportRequest {

    @NotBlank
    private String categoryCode;
    @NotBlank
    @Size(max = 255)
    private String address;
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal latitude;
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal longitude;
    @NotBlank
    @Size(min = 10, max = 1000)
    private String description;
    @Size(max = 255)
    private String reporterContact;
    @Size(max = 64)
    private String submissionKey;

    public Report toModel() {
        Report r = new Report();
        r.setCategoryCode(categoryCode);
        r.setAddress(address);
        r.setLatitude(latitude);
        r.setLongitude(longitude);
        r.setDescription(description);
        r.setReporterContact(reporterContact);
        r.setSubmissionKey(submissionKey);
        return r;
    }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReporterContact() { return reporterContact; }
    public void setReporterContact(String reporterContact) { this.reporterContact = reporterContact; }
    public String getSubmissionKey() { return submissionKey; }
    public void setSubmissionKey(String submissionKey) { this.submissionKey = submissionKey; }
}
