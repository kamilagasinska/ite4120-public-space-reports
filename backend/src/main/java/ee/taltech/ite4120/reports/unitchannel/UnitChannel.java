package ee.taltech.ite4120.reports.unitchannel;

/**
 * Hands a report to the unit that solves it. The course has an X-Road adapter and an
 * outbox that only keeps the handover; e-mail or a document system would be other adapters.
 */
public interface UnitChannel {

    /** What is sent: who gets it and the letter itself, as JSON. */
    record Letter(String member, String inbox, String json) {}

    /** How it went out. The service and the message id are empty for the outbox. */
    record Delivery(String channel, String service, String messageId) {}

    /** The channel did not take the letter. */
    class UnitChannelException extends Exception {

        private final String service;

        public UnitChannelException(String message, String service, Throwable cause) {
            super(message, cause);
            this.service = service;
        }

        public String service() {
            return service;
        }
    }

    Delivery send(Letter letter) throws UnitChannelException;
}
