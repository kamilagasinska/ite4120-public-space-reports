package ee.taltech.ite4120.reports.api;

import jakarta.validation.constraints.NotBlank;

/** A report to sign and the demo person who signs it. */
public class SigningRequest extends ReportRequest {

    @NotBlank
    private String person;

    public String getPerson() { return person; }
    public void setPerson(String person) { this.person = person; }
}
