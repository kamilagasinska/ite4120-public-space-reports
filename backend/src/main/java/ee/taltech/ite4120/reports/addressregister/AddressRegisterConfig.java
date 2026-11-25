package ee.taltech.ite4120.reports.addressregister;

import ee.taltech.ite4120.reports.addressregister.http.HttpAddressRegisterAdapter;
import ee.taltech.ite4120.reports.addressregister.xroad.XRoadAddressRegisterAdapter;
import org.helex.forge.xroad.XRoadClientId;
import org.helex.forge.xroad.XRoadServiceId;
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
            @Value("${reports.address-register.xroad.security-server-url:http://localhost:8080}") String ssUrl,
            @Value("${reports.address-register.xroad.instance:ee-test}") String instance,
            @Value("${reports.address-register.xroad.client-member-class:EDU}") String clientClass,
            @Value("${reports.address-register.xroad.client-member-code:taltech}") String clientCode,
            @Value("${reports.address-register.xroad.client-subsystem:ite4120}") String clientSubsystem,
            @Value("${reports.address-register.xroad.service-member-class:GOV}") String serviceClass,
            @Value("${reports.address-register.xroad.service-member-code:00000000}") String serviceCode,
            @Value("${reports.address-register.xroad.service-subsystem:ads}") String serviceSubsystem) {
        if ("xroad".equalsIgnoreCase(mode)) {
            return new XRoadAddressRegisterAdapter(ssUrl,
                    new XRoadClientId(instance, clientClass, clientCode, clientSubsystem),
                    new XRoadServiceId(instance, serviceClass, serviceCode, serviceSubsystem, "aadressParing", "v1"));
        }
        return new HttpAddressRegisterAdapter(url);
    }
}
