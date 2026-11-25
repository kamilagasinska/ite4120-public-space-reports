package ee.taltech.ite4120.reports.addressregister.xroad.wire;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import ee.taltech.ite4120.reports.addressregister.wire.AdsAadress;
import java.util.List;

/** WIRE RECORD. The X-Road answer. Element names stay as the provider defines them. */
@JacksonXmlRootElement(localName = "aadressParingResponse", namespace = AdsAadressParing.NS)
public record AdsAadressParingResponse(
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "aadress", namespace = AdsAadressParing.NS) List<AdsAadress> aadressid) {}
