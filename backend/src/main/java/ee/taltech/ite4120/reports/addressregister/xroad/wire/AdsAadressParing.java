package ee.taltech.ite4120.reports.addressregister.xroad.wire;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/** WIRE RECORD. The X-Road request body, a teaching stand-in in the provider's vocabulary. */
@JacksonXmlRootElement(localName = "aadressParing", namespace = AdsAadressParing.NS)
public record AdsAadressParing(
        @JacksonXmlProperty(localName = "request", namespace = NS) Request request) {

    public static final String NS = "http://ads.x-road.eu";

    public record Request(
            @JacksonXmlProperty(localName = "aadress", namespace = NS) String aadress) {}

    public static AdsAadressParing of(String aadress) {
        return new AdsAadressParing(new Request(aadress));
    }
}
