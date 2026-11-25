package ee.taltech.ite4120.reports.smartid;

/** The port to Smart-ID. */
public interface SmartIdAdapter {

    enum Result { RUNNING, SIGNED, REFUSED, EXPIRED, FAILED }

    record Session(String id, String verificationCode) {}

    /** Asks the person to sign the text. */
    Session start(String document, byte[] text);

    /** SIGNED only when the signature over this text by this person has been verified. */
    Result poll(String sessionId, String document, byte[] text);

    /** The digest that is sent for signing, base64. */
    String digest(byte[] text);
}
