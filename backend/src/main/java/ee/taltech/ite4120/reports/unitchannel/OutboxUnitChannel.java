package ee.taltech.ite4120.reports.unitchannel;

/** Keeps the handover in the application only. Nothing leaves it. */
public class OutboxUnitChannel implements UnitChannel {

    @Override
    public Delivery send(Letter letter) {
        return new Delivery("OUTBOX", null, null);
    }
}
