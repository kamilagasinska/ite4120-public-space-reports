package ee.taltech.ite4120.reports.addressregister;

import ee.taltech.ite4120.reports.addressregister.http.HttpAddressRegisterAdapter;
import ee.taltech.ite4120.reports.addressregister.xroad.XRoadAddressRegisterAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the transport by reports.address-register.mode, http (default) or xroad. */
@Configuration
public class AddressRegisterConfig {

    @Bean
    public AddressRegisterAdapter addressRegisterAdapter(
            @Value("${reports.address-register.mode:http}") String mode,
            @Value("${reports.address-register.url:http://localhost:18440/mock-registry/ads}") String url,
            @Value("${reports.address-register.xroad.security-server-url:http://localhost:18440/mock-registry/xroad}") String ssUrl,
            @Value("${reports.address-register.xroad.client:ee-test/EDU/taltech/ite4120}") String client,
            @Value("${reports.address-register.xroad.service:ee-test/GOV/70003098/aks-ads/ADSobjotsingV8}") String service) {
        return "xroad".equalsIgnoreCase(mode) ? new XRoadAddressRegisterAdapter(ssUrl, client, service)
                : new HttpAddressRegisterAdapter(url);
    }
}
