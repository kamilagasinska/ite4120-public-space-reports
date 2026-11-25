package ee.taltech.ite4120.reports.xroad;

import com.fasterxml.jackson.annotation.JsonInclude;
import ee.taltech.ite4120.reports.service.ReportAccess;
import ee.taltech.ite4120.reports.xroad.XRoadRest.XRoadException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * A live call to the X-Road test environment X-Road Playground of NIIS. It asks which
 * methods a test service has and sends no data of ours.
 */
@Service
public class XRoadCheck {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Status(boolean available, String securityServer, String service, String messageId, String detail) {}

    private final XRoadRest xroad;
    private final ReportAccess access;
    private final String url;
    private final String service;

    public XRoadCheck(ReportAccess access,
            @Value("${reports.xroad-check.security-server-url:http://testcomss01.playground.x-road.systems}") String url,
            @Value("${reports.xroad-check.client:PLAYGROUND/COM/1234567-8/TestClient}") String client,
            @Value("${reports.xroad-check.service:PLAYGROUND/GOV/8765432-1/TestService}") String service) {
        this.access = access;
        this.url = url;
        this.service = service;
        this.xroad = new XRoadRest(url, client, Duration.ofSeconds(8));
    }

    public Status check() {
        access.requireCoordinator();
        try {
            return new Status(true, url, service, xroad.get(service, "listMethods").messageId(), null);
        } catch (XRoadException e) {
            return new Status(false, url, service, null, e.getMessage());
        }
    }
}
