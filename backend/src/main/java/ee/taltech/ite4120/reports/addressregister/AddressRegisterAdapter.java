package ee.taltech.ite4120.reports.addressregister;

/** The port to the address register. The transport behind it is configuration. */
public interface AddressRegisterAdapter {

    /**
     * The address the register confirms, or null when it does not know it or knows
     * several, because an ambiguous address is not confirmed.
     *
     * @throws AddressRegisterException when the register cannot be reached or answers with a fault
     */
    AddressInfo lookup(String address);
}
