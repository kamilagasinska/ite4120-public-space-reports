package ee.taltech.ite4120.reports.unitchannel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the unit channel by reports.unit-channel.mode, outbox (default) or xroad. */
@Configuration
public class UnitChannelConfig {

    @Bean
    public UnitChannel unitChannel(
            @Value("${reports.unit-channel.mode:outbox}") String mode,
            @Value("${reports.unit-channel.xroad.security-server-url:http://localhost:18440/mock-registry/xroad}") String url,
            @Value("${reports.unit-channel.xroad.client:ee-test/EDU/taltech/ite4120}") String client,
            @Value("${reports.unit-channel.xroad.instance:ee-test}") String instance) {
        return "xroad".equalsIgnoreCase(mode) ? new XRoadUnitChannel(url, client, instance) : new OutboxUnitChannel();
    }
}
