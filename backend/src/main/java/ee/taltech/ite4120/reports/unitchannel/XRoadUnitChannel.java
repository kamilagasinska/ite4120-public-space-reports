package ee.taltech.ite4120.reports.unitchannel;

import ee.taltech.ite4120.reports.xroad.XRoadRest;
import ee.taltech.ite4120.reports.xroad.XRoadRest.XRoadException;
import java.time.Duration;

/**
 * Sends the report as an X-Road message (protocol for REST) to the service receiveReport of
 * the subsystem reports-inbox of the recipient. The member codes are invented, as no list of
 * the systems of the units was found.
 */
public class XRoadUnitChannel implements UnitChannel {

    private final XRoadRest xroad;
    private final String instance;

    public XRoadUnitChannel(String securityServerUrl, String client, String instance) {
        this.xroad = new XRoadRest(securityServerUrl, client, Duration.ofSeconds(4));
        this.instance = instance;
    }

    @Override
    public Delivery send(Letter letter) throws UnitChannelException {
        String service = instance + "/GOV/" + letter.member() + "/reports-inbox/receiveReport";
        try {
            return new Delivery("XROAD", service, xroad.post(service, letter.json()).messageId());
        } catch (XRoadException e) {
            throw new UnitChannelException("X-Road: " + e.getMessage(), service, e);
        }
    }
}
