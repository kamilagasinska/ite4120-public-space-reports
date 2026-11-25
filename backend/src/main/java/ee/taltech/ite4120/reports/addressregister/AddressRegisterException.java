package ee.taltech.ite4120.reports.addressregister;

/** The register could not answer. The caller fails open (REPORTS.01). */
public class AddressRegisterException extends RuntimeException {

    public AddressRegisterException(String message) {
        super(message);
    }

    public AddressRegisterException(String message, Throwable cause) {
        super(message, cause);
    }
}
