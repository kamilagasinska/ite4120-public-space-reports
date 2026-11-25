package ee.taltech.ite4120.reports.addressregister.wire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * WIRE RECORD. One address in the register's own vocabulary, shaped like the public
 * In-ADS gazetteer. A teaching stand-in, to be checked against the provider's
 * published contract before a real call.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdsAadress(String ads_oid, String taisaadress, String ehakov, String omavalitsus) {}
