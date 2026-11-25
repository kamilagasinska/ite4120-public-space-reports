package ee.taltech.ite4120.reports.smartid.http.wire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** WIRE RECORD. The answer to a signature request of the Smart-ID v3 API. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmartIdSessionStarted(String sessionID, Vc vc) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vc(String type, String value) {}
}
