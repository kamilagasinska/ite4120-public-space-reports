package ee.taltech.ite4120.reports.smartid;

/** Smart-ID could not answer. Answers 502, the caller fails closed. */
public class SmartIdException extends RuntimeException {

    public SmartIdException(String message, Throwable cause) {
        super(message, cause);
    }
}
