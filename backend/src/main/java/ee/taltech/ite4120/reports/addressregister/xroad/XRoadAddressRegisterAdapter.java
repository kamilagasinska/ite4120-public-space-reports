package ee.taltech.ite4120.reports.addressregister.xroad;

import ee.taltech.ite4120.reports.addressregister.AddressInfo;
import ee.taltech.ite4120.reports.addressregister.AddressMapper;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterAdapter;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterException;
import ee.taltech.ite4120.reports.addressregister.xroad.wire.AdsAadressParing;
import ee.taltech.ite4120.reports.addressregister.xroad.wire.AdsAadressParingResponse;
import java.net.URI;
import java.time.Duration;
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
        try {
            AdsAadressParingResponse wire = client
                    .send(service, AdsAadressParing.of(address), AdsAadressParingResponse.class).body();
            return AddressMapper.toAddressInfo(wire == null ? null : wire.aadressid());
        } catch (XRoadException e) {
            throw new AddressRegisterException("X-Road call failed: " + e.getMessage(), e);
        }
    }
}
