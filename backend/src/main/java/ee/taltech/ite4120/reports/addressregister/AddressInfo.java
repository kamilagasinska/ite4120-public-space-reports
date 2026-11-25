package ee.taltech.ite4120.reports.addressregister;

/**
 * What the register knows about a place, in our own vocabulary. The code is null when
 * the address was not confirmed, the municipality when it could not be determined.
 */
public record AddressInfo(String code, String fullAddress, String municipalityCode, String municipalityName) {}
