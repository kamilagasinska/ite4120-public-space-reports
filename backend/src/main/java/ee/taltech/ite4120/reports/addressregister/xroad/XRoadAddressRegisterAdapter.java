package ee.taltech.ite4120.reports.addressregister.xroad;

import ee.taltech.ite4120.reports.addressregister.AddressInfo;
import ee.taltech.ite4120.reports.addressregister.AddressMapper;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterAdapter;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterException;
import ee.taltech.ite4120.reports.addressregister.LEst97;
import ee.taltech.ite4120.reports.addressregister.wire.AdsAadress;
import ee.taltech.ite4120.reports.addressregister.xroad.wire.AdsAadressParing;
import ee.taltech.ite4120.reports.addressregister.xroad.wire.AdsAadressParingResponse;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.helex.forge.xroad.XRoadClient;
import org.helex.forge.xroad.XRoadClientConfig;
import org.helex.forge.xroad.XRoadClientId;
import org.helex.forge.xroad.XRoadException;
import org.helex.forge.xroad.XRoadServiceId;

/**
 * The X-Road transport on the forge-xroad library. The course has no security server,
 * so this is a skeleton that fails fast with a clear error.
 */
public class XRoadAddressRegisterAdapter implements AddressRegisterAdapter {

    private final XRoadClient client;
    private final XRoadServiceId service;

    public XRoadAddressRegisterAdapter(String securityServerUrl, XRoadClientId clientId, XRoadServiceId service) {
        this.client = new XRoadClient(new XRoadClientConfig(
                URI.create(securityServerUrl), clientId, "ITE4120", Duration.ofSeconds(3)));
        this.service = service;
    }

    @Override
    public AddressInfo lookup(String address) {
        return AddressMapper.toAddressInfo(ask(AdsAadressParing.of(address)));
    }

    @Override
    public AddressInfo lookupPoint(double latitude, double longitude) {
        double[] p = LEst97.fromDegrees(latitude, longitude);
        return AddressMapper.toPlace(ask(AdsAadressParing.ofPoint(Math.round(p[0]), Math.round(p[1]))));
    }

    private List<AdsAadress> ask(AdsAadressParing request) {
        try {
            AdsAadressParingResponse wire = client.send(service, request, AdsAadressParingResponse.class).body();
            return wire == null ? null : wire.aadressid();
        } catch (XRoadException e) {
            throw new AddressRegisterException("X-Road call failed: " + e.getMessage(), e);
        }
    }
}
