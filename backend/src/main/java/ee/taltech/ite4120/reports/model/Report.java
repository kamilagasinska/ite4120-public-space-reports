package ee.taltech.ite4120.reports.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A report about a problem in public space, with the category data the API shows beside it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Report {

    private String recipientKind;
    private String recipientName;
    private String municipalityCode;
    private String municipalityName;
    private String channel;
    /** Read from the last rejection, shown to the resident too. */
    private String rejectionReason;
    @JsonIgnore
    private Long reporterId;
    @JsonIgnore
    private String ipHash;
    @JsonIgnore
    private String signedDigest;
    /** Internal key, replaced by the reference number in the API. */
    @JsonIgnore
    private Long id;
    private String referenceNumber;
    private String categoryCode;
    /** Read from the category, never stored on the report. */
    private String categoryName;
    /** Read from the category, never stored on the report. */
    private String responsibleUnit;
    private String address;
    private String addressCode;
    private Boolean addressVerified;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String description;
    private String reporterContact;
    private String status;
    @JsonIgnore
    private String submissionKey;
    private OffsetDateTime submittedAt;
    /** Calculated from the creation time, never stored. */
    private Integer daysOpen;
    private OffsetDateTime sysCreatedAt;
    private String sysCreatedBy;
    private OffsetDateTime sysModifiedAt;
    private String sysModifiedBy;
    private Long sysVersion;

    /** Removes everything a resident must not see, in particular the contact and the user name. */
    public Report forResident() {
        description = null;
        reporterContact = null;
        sysCreatedAt = null;
        sysCreatedBy = null;
        sysModifiedAt = null;
        sysModifiedBy = null;
        sysVersion = null;
        return this;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getResponsibleUnit() { return responsibleUnit; }
    public void setResponsibleUnit(String responsibleUnit) { this.responsibleUnit = responsibleUnit; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getAddressCode() { return addressCode; }
    public void setAddressCode(String addressCode) { this.addressCode = addressCode; }
    public Boolean getAddressVerified() { return addressVerified; }
    public void setAddressVerified(Boolean addressVerified) { this.addressVerified = addressVerified; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReporterContact() { return reporterContact; }
    public void setReporterContact(String reporterContact) { this.reporterContact = reporterContact; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubmissionKey() { return submissionKey; }
    public void setSubmissionKey(String submissionKey) { this.submissionKey = submissionKey; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Integer getDaysOpen() { return daysOpen; }
    public void setDaysOpen(Integer daysOpen) { this.daysOpen = daysOpen; }
    public OffsetDateTime getSysCreatedAt() { return sysCreatedAt; }
    public void setSysCreatedAt(OffsetDateTime sysCreatedAt) { this.sysCreatedAt = sysCreatedAt; }
    public String getSysCreatedBy() { return sysCreatedBy; }
    public void setSysCreatedBy(String sysCreatedBy) { this.sysCreatedBy = sysCreatedBy; }
    public OffsetDateTime getSysModifiedAt() { return sysModifiedAt; }
    public void setSysModifiedAt(OffsetDateTime sysModifiedAt) { this.sysModifiedAt = sysModifiedAt; }
    public String getSysModifiedBy() { return sysModifiedBy; }
    public void setSysModifiedBy(String sysModifiedBy) { this.sysModifiedBy = sysModifiedBy; }
    public Long getSysVersion() { return sysVersion; }
    public void setSysVersion(Long sysVersion) { this.sysVersion = sysVersion; }
    public String getMunicipalityCode() { return municipalityCode; }
    public void setMunicipalityCode(String municipalityCode) { this.municipalityCode = municipalityCode; }
    public String getMunicipalityName() { return municipalityName; }
    public void setMunicipalityName(String municipalityName) { this.municipalityName = municipalityName; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
    public String getIpHash() { return ipHash; }
    public void setIpHash(String ipHash) { this.ipHash = ipHash; }
    public String getSignedDigest() { return signedDigest; }
    public void setSignedDigest(String signedDigest) { this.signedDigest = signedDigest; }
    public String getRecipientKind() { return recipientKind; }
    public void setRecipientKind(String recipientKind) { this.recipientKind = recipientKind; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
}
