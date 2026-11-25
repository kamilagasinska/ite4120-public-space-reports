package ee.taltech.ite4120.reports.smartid.http.wire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** WIRE RECORD. The session status of the Smart-ID v3 API, with the provider's names. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmartIdSessionStatus(String state, Result result, Signature signature, Cert cert) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String endResult, String documentNumber) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Signature(String value, String signatureAlgorithm) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cert(String value, String certificateLevel) {}
}
