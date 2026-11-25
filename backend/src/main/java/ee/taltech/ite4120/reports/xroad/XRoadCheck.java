package ee.taltech.ite4120.reports.xroad;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.taltech.ite4120.reports.service.ReportAccess;
import ee.taltech.ite4120.reports.xroad.XRoadRest.Answer;
import ee.taltech.ite4120.reports.xroad.XRoadRest.XRoadException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * A live call to the X-Road test environment X-Road Playground of NIIS. It asks which
 * methods a test service has and sends no data of ours.
 */
@Service
public class XRoadCheck {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Status(boolean available, String securityServer, String service, String messageId,
            String requestHash, List<String> services, String detail) {}

    private record Method(@JsonProperty("service_code") String code) {}

    private record Methods(List<Method> service) {}

    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
            Answer answer = xroad.get(service, "listMethods");
            return new Status(true, url, service, answer.messageId(), answer.requestHash(),
                    services(answer.body()), null);
        } catch (XRoadException e) {
            return new Status(false, url, service, null, null, null, e.getMessage());
        }
    }

    private List<String> services(String body) throws XRoadException {
        try {
            Methods methods = json.readValue(body, Methods.class);
            if (methods != null && methods.service() != null) {
                return methods.service().stream().map(Method::code).filter(Objects::nonNull).toList();
            }
        } catch (IOException e) {
            throw new XRoadException("the answer is not a list of services", e);
        }
        throw new XRoadException("the answer is not a list of services", null);
    }
}
