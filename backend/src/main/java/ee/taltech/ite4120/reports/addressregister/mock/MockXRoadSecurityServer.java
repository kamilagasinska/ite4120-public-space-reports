package ee.taltech.ite4120.reports.addressregister.mock;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An imitated X-Road security server, local profile only. It knows one service, the
 * address register, and answers it with the public address service of Estonia, so the
 * application speaks X-Road and gets real addresses. Nothing is signed or logged as a
 * real security server does. "register-down" in the address answers with a fault.
 */
@RestController
@RequestMapping("/mock-registry/xroad/r1")
@ConditionalOnProperty(name = "reports.address-register.mock-server", havingValue = "true")
public class MockXRoadSecurityServer {

    private static final String KNOWN = "GOV/70003098/aks-ads/ADSobjotsingV8";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String provider;

    public MockXRoadSecurityServer(
            @Value("${reports.address-register.mock-provider:https://inaadress.maaamet.ee/inaadress}") String provider) {
        this.provider = provider;
    }

    @GetMapping("/{instance}/{memberClass}/{member}/{subsystem}/{service}/gazetteer")
    public ResponseEntity<String> call(@PathVariable String instance, @PathVariable String memberClass,
            @PathVariable String member, @PathVariable String subsystem, @PathVariable String service,
            @RequestHeader(name = "X-Road-Client", required = false) String client, HttpServletRequest request) {
        String id = String.join("/", memberClass, member, subsystem, service);
        if (client == null) {
            return fault(400, "Client.MissingHeader", "X-Road-Client is missing");
        }
        if (!KNOWN.equals(id)) {
            return fault(500, "Server.ServerProxy.UnknownService", "Unknown service: " + instance + "/" + id);
        }
        String query = request.getQueryString();
        if (query == null || query.toLowerCase().contains("register-down")) {
            return fault(500, "Server.ServerProxy.ServiceFailed", "the provider did not answer");
        }
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(provider + "/gazetteer?" + query))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            /* The provider says "not Estonia" with a 500. For the consumer that is an empty answer. */
            boolean outside = r.statusCode() == 500 && r.body().contains("\"EIEESTI\"");
            if (r.statusCode() != 200 && !outside) {
                return fault(500, "Server.ServerProxy.ServiceFailed", "the provider answered " + r.statusCode());
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .header("X-Road-Id", instance + "-" + UUID.randomUUID())
                    .header("X-Road-Client", client).header("X-Road-Service", instance + "/" + id)
                    .body(outside ? "{\"addresses\":[]}" : r.body());
        } catch (IOException e) {
            return fault(500, "Server.ServerProxy.ServiceFailed", "the provider is unreachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fault(500, "Server.ServerProxy.ServiceFailed", "interrupted");
        }
    }

    /**
     * The service receiveReport of the imitated unit inboxes. The unit is the member code in
     * the path, the letter stays in the application, which keeps the handover itself.
     */
    @PostMapping("/{instance}/{memberClass}/{member}/reports-inbox/receiveReport")
    public ResponseEntity<String> receive(@PathVariable String instance, @PathVariable String memberClass,
            @PathVariable String member, @RequestHeader(name = "X-Road-Client", required = false) String client,
            @RequestBody(required = false) String letter) {
        if (client == null) {
            return fault(400, "Client.MissingHeader", "X-Road-Client is missing");
        }
        if (letter == null || !letter.contains("referenceNumber")) {
            return fault(400, "Server.ServerProxy.ServiceFailed", "the letter has no reference number");
        }
        String service = instance + "/" + memberClass + "/" + member + "/reports-inbox/receiveReport";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header("X-Road-Id", instance + "-" + UUID.randomUUID())
                .header("X-Road-Client", client).header("X-Road-Service", service)
                .body("{\"received\":true}");
    }

    private static ResponseEntity<String> fault(int status, String type, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).header("X-Road-Error", type)
                .body("{\"type\":\"%s\",\"message\":\"%s\"}".formatted(type, message.replaceAll("[\"\\\\]", "")));
    }
}
