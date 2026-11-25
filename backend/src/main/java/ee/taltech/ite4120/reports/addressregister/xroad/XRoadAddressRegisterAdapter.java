package ee.taltech.ite4120.reports.addressregister.xroad;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.taltech.ite4120.reports.addressregister.AddressInfo;
import ee.taltech.ite4120.reports.addressregister.AddressMapper;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterAdapter;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterException;
import ee.taltech.ite4120.reports.addressregister.LEst97;
import ee.taltech.ite4120.reports.addressregister.http.wire.AdsGazetteerResponse;
import ee.taltech.ite4120.reports.addressregister.wire.AdsAadress;
import ee.taltech.ite4120.reports.xroad.XRoadRest;
import ee.taltech.ite4120.reports.xroad.XRoadRest.XRoadException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * The address register over the X-Road message protocol for REST. The member code and
 * the subsystem are those of the real register. The shape of the answer is the public
 * gazetteer's, a stand-in until the service description is read from a security server.
 */
public class XRoadAddressRegisterAdapter implements AddressRegisterAdapter {

    private final XRoadRest xroad;
    private final String service;
    private final ObjectMapper json = new ObjectMapper();

    public XRoadAddressRegisterAdapter(String securityServerUrl, String client, String service) {
        this.xroad = new XRoadRest(securityServerUrl, client, Duration.ofSeconds(4));
        this.service = service;
    }

    @Override
    public AddressInfo lookup(String address) {
        return AddressMapper.toAddressInfo(ask("address=" + URLEncoder.encode(address, StandardCharsets.UTF_8)));
    }

    @Override
    public AddressInfo lookupPoint(double latitude, double longitude) {
        double[] p = LEst97.fromDegrees(latitude, longitude);
        return AddressMapper.toPlace(ask("x=" + Math.round(p[0]) + "&y=" + Math.round(p[1])));
    }

    private List<AdsAadress> ask(String query) {
        try {
            AdsGazetteerResponse wire = json.readValue(
                    xroad.get(service, "gazetteer?" + query).body(), AdsGazetteerResponse.class);
            return wire == null ? null : wire.addresses();
        } catch (XRoadException | IOException e) {
            throw new AddressRegisterException("X-Road call failed: " + e.getMessage(), e);
        }
    }
}
