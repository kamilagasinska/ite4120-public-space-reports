package ee.taltech.ite4120.reports.addressregister.http.wire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ee.taltech.ite4120.reports.addressregister.wire.AdsAadress;
import java.util.List;

/** WIRE RECORD. The gazetteer answer as the register sends it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdsGazetteerResponse(List<AdsAadress> addresses) {}
