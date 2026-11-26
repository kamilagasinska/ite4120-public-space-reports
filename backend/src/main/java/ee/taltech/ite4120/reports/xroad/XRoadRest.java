package ee.taltech.ite4120.reports.xroad;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/** A consumer of the X-Road message protocol for REST, version r1. */
public class XRoadRest {

    /** The call failed, or X-Road answered with a fault. */
    public static class XRoadException extends Exception {

        public XRoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The id is ours and comes back, the hash of the request is made by the security server. */
    public record Answer(String body, String messageId, String requestHash) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String securityServer;
    private final String client;
    private final Duration timeout;

    public XRoadRest(String securityServer, String client, Duration timeout) {
        this.securityServer = securityServer;
        this.client = client;
        this.timeout = timeout;
    }

    /** The service is INSTANCE/CLASS/MEMBER/SUBSYSTEM/SERVICE. */
    public Answer get(String service, String path) throws XRoadException {
        return send(builder(securityServer + "/r1/" + service + "/" + path).GET().build());
    }

    /** Sends a JSON body to the service itself, INSTANCE/CLASS/MEMBER/SUBSYSTEM/SERVICE. */
    public Answer post(String service, String json) throws XRoadException {
        return send(builder(securityServer + "/r1/" + service).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    private HttpRequest.Builder builder(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("X-Road-Client", client)
                .header("X-Road-Id", UUID.randomUUID().toString())
                .header("Accept", "application/json");
    }

    private Answer send(HttpRequest request) throws XRoadException {
        try {
            HttpResponse<String> r = http.send(request, HttpResponse.BodyHandlers.ofString());
            String fault = r.headers().firstValue("X-Road-Error").orElse(null);
            if (fault != null) {
                throw new XRoadException(fault, null);
            }
            if (r.statusCode() != 200) {
                throw new XRoadException("HTTP " + r.statusCode(), null);
            }
            return new Answer(r.body(), r.headers().firstValue("X-Road-Id").orElse(null),
                    r.headers().firstValue("X-Road-Request-Hash").orElse(null));
        } catch (IOException e) {
            throw new XRoadException("security server unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new XRoadException("interrupted", e);
        }
    }
}
