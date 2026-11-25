package ee.taltech.ite4120.reports.addressregister;

/** The port to the address register. The transport behind it is configuration. */
public interface AddressRegisterAdapter {

    /**
     * What the register knows about the address, or null when it knows nothing. Several
     * matches are ambiguous and not confirmed, but may still share the municipality.
     *
     * @throws AddressRegisterException when the register cannot be reached or answers with a fault
     */
    AddressInfo lookup(String address);

    /**
     * The place at a map point, or null outside the register's area.
     *
     * @throws AddressRegisterException when the register cannot be reached or answers with a fault
     */
    AddressInfo lookupPoint(double latitude, double longitude);
}
