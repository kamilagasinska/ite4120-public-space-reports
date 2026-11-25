package ee.taltech.ite4120.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Business tests 21 to 25 of REPORTS.01, the recipient of a report and X-Road. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"reports.coordinators=coordinator", "reports.address-register.mode=xroad"})
@ActiveProfiles("test")
class ReportRoutingRulesIT {

    private static final String COORDINATOR = "coordinator";
    private static final String STREET = "50";
    private static final String HIGHWAY = "10";
    private static final String OTHER_ROAD = "60";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static volatile String near;
    private static volatile String far;
    private static volatile int roadStatus;
    private static volatile int xroadStatus;
    private static volatile String lastClient;
    private static volatile String lastPath;
    private static volatile String lastId;
    private static final HttpServer OUTSIDE = startOutside();

    @Autowired
    Environment environment;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + OUTSIDE.getAddress().getPort();
        registry.add("reports.address-register.xroad.security-server-url", () -> url);
        registry.add("reports.road-map.url", () -> url + "/wfs");
        registry.add("reports.xroad-check.security-server-url", () -> url);
    }

    @BeforeEach
    void everythingWorks() {
        near = STREET;
        far = HIGHWAY;
        roadStatus = 200;
        xroadStatus = 200;
        jdbc.update("update reports.report set ip_hash = null");
    }

    /** 21. Road damage on a state road goes to the state. */
    @Test
    void roadDamageOnAStateRoadGoesToTheState() {
        near = HIGHWAY;
        far = STREET;
        Answer state = report("ROAD_DAMAGE");
        assertThat(state.status).as(state.raw).isEqualTo(201);
        assertThat(state.text("recipientKind")).isEqualTo("STATE_ROADS");
        assertThat(state.text("recipientName")).isEqualTo("Transport Administration");

        near = STREET;
        far = HIGHWAY;
        Answer street = report("ROAD_DAMAGE");
        assertThat(street.text("recipientKind")).isEqualTo("MUNICIPALITY");
        assertThat(street.text("recipientName")).isEqualTo("Saue vald, Roads Department");

        near = OTHER_ROAD;
        assertThat(report("ROAD_DAMAGE").text("recipientKind")).isEqualTo("UNDETERMINED");

        near = HIGHWAY;
        assertThat(report("LITTER").text("recipientName")).as("litter beside a highway").isEqualTo("Saue vald, Environment Department");
    }

    /** 22. The road map being down does not block a report. */
    @Test
    void theRoadMapBeingDownDoesNotBlockAReport() {
        roadStatus = 503;
        Answer down = report("ROAD_DAMAGE");
        assertThat(down.status).isEqualTo(201);
        assertThat(down.text("recipientKind")).isEqualTo("UNDETERMINED");
    }

    /** 23. A resident sees the recipient before sending. */
    @Test
    void aResidentSeesTheRecipientBeforeSending() {
        near = HIGHWAY;
        Answer place = get("resident", "/api/reports/place?latitude=59.32&longitude=24.55&categoryCode=ROAD_DAMAGE");
        assertThat(place.text("recipientName")).isEqualTo("Transport Administration");
        assertThat(place.text("municipalityName")).isEqualTo("Saue vald");
        assertThat(get("resident", "/api/reports/place?latitude=59.32&longitude=24.55").body.has("recipientName")).isFalse();
        assertProblem(get("resident", "/api/reports/place?latitude=59.32&longitude=24.55&categoryCode=NOISE"), 400, "categoryCode");
    }

    /** 24. A report that is not for its recipient is forwarded, and the resident sees where. */
    @Test
    void aReportIsForwardedAndTheResidentSeesWhere() {
        String reference = report("ROAD_DAMAGE").text("referenceNumber");
        String path = "/api/reports/" + reference;
        ObjectNode forward = json.createObjectNode().put("kind", "STATE_ROADS").put("reason", "State road 11185");

        assertProblem(post("resident", path + "/forward", forward), 403, "coordinator");
        assertProblem(post(COORDINATOR, path + "/forward", forward.deepCopy().put("reason", " ")), 400, "reason");
        assertProblem(post(COORDINATOR, path + "/forward", forward.deepCopy().put("kind", "MARS")), 400, "kind");
        assertProblem(post(COORDINATOR, path + "/forward", forward.deepCopy().put("kind", "OTHER")), 400, "name");

        assertThat(post(COORDINATOR, path + "/forward", forward).text("recipientName")).isEqualTo("Transport Administration");
        assertProblem(post(COORDINATOR, path + "/forward", forward), 409, "already");

        Answer seen = get("resident", path);
        assertThat(seen.text("recipientName")).isEqualTo("Transport Administration");
        Answer trail = get("resident", path + "/forwardings");
        assertThat(trail.body.findValuesAsText("fromName")).containsExactly("Saue vald, Roads Department");
        assertThat(trail.body.findValuesAsText("toName")).containsExactly("Transport Administration");
        assertThat(trail.body.findValuesAsText("reason")).containsExactly("State road 11185");
        assertThat(trail.raw).doesNotContain(COORDINATOR);
        assertThat(get(COORDINATOR, path + "/forwardings").body.findValuesAsText("sysCreatedBy")).containsExactly(COORDINATOR);

        post(COORDINATOR, path + "/status", json.createObjectNode().put("toStatus", "REJECTED").put("reason", "Not ours"));
        assertProblem(post(COORDINATOR, path + "/forward",
                forward.deepCopy().put("kind", "OTHER").put("name", "RMK")), 409, "REJECTED");
    }

    /** 25. The address register is asked over the X-Road message protocol. */
    @Test
    void theAddressRegisterIsAskedOverXRoad() {
        Answer ok = report("LITTER");
        assertThat(ok.text("addressVerified")).isEqualTo("true");
        assertThat(lastClient).isEqualTo("ee-test/EDU/taltech/ite4120");
        assertThat(lastPath).startsWith("/r1/ee-test/GOV/70003098/aks-ads/ADSobjotsingV8/");
        assertThat(UUID.fromString(lastId)).isNotNull();

        xroadStatus = 500;
        Answer down = report("LITTER");
        assertThat(down.status).isEqualTo(201);
        assertThat(down.text("addressVerified")).isEqualTo("false");
    }

    /** 25. The coordinator sees whether the X-Road test environment answers. */
    @Test
    void theCoordinatorSeesWhetherXRoadAnswers() {
        Answer up = get(COORDINATOR, "/api/reports/xroad-status");
        assertThat(up.text("available")).isEqualTo("true");
        assertThat(up.text("messageId")).isEqualTo("PLAYGROUND-1");

        xroadStatus = 500;
        Answer down = get(COORDINATOR, "/api/reports/xroad-status");
        assertThat(down.status).isEqualTo(200);
        assertThat(down.text("available")).isEqualTo("false");
        assertThat(down.text("detail")).contains("Server.ServerProxy.ServiceDown");
        assertProblem(get("resident", "/api/reports/xroad-status"), 403, "coordinator");
    }

    // ---- helpers ---------------------------------------------------------

    private record Answer(int status, String contentType, String raw, JsonNode body) {
        String text(String field) {
            return body.path(field).asText();
        }
    }

    private static void assertProblem(Answer a, int status, String detailPart) {
        assertThat(a.status).as(a.raw).isEqualTo(status);
        assertThat(a.contentType).startsWith("application/problem+json");
        assertThat(a.text("detail")).contains(detailPart);
    }

    private Answer report(String category) {
        return post("resident", "/api/reports", json.createObjectNode().put("categoryCode", category)
                .put("address", "Toonekure, Saue vald").put("latitude", 59.32).put("longitude", 24.55)
                .put("description", "A problem in public space"));
    }

    private Answer get(String user, String path) {
        return send(user, request(path).GET());
    }

    private Answer post(String user, String path, JsonNode body) {
        return send(user, request(path).header("Content-Type", "application/json")
                .header("X-Device-Id", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path));
    }

    private Answer send(String user, HttpRequest.Builder request) {
        try {
            HttpResponse<String> r = http.send(request.header("Authorization", "Bearer " + user).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new Answer(r.statusCode(), r.headers().firstValue("Content-Type").orElse(""), r.body(),
                    r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** The X-Road security server, the Playground and the road map in one stub. */
    private static HttpServer startOutside() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/r1/", x -> {
                lastClient = x.getRequestHeaders().getFirst("X-Road-Client");
                lastPath = x.getRequestURI().getPath();
                lastId = x.getRequestHeaders().getFirst("X-Road-Id");
                if (xroadStatus != 200) {
                    x.getResponseHeaders().add("X-Road-Error", "Server.ServerProxy.ServiceDown");
                    answer(x, xroadStatus, "{\"type\":\"Server.ServerProxy.ServiceDown\",\"message\":\"down\"}");
                } else if (lastPath.startsWith("/r1/PLAYGROUND/")) {
                    x.getResponseHeaders().add("x-road-id", "PLAYGROUND-1");
                    answer(x, 200, "{\"service\":[]}");
                } else {
                    answer(x, 200, "{\"addresses\":[{\"ads_oid\":\"KY1\",\"taisaadress\":\"Harju maakond, Saue vald, Toonekure\","
                            + "\"ehakov\":\"725\",\"omavalitsus\":\"Saue vald\"}]}");
                }
            });
            server.createContext("/wfs", x -> {
                /* The box of the request, its middle is the reported point. */
                String[] b = x.getRequestURI().getQuery().replaceAll(".*bbox=", "").split(",");
                double cx = (Double.parseDouble(b[0]) + Double.parseDouble(b[2])) / 2;
                double cy = (Double.parseDouble(b[1]) + Double.parseDouble(b[3])) / 2;
                answer(x, roadStatus, "{\"features\":[" + road(far, cx - 50, cy + 20, cx + 50, cy + 20) + ","
                        + road(near, cx - 50, cy + 3, cx + 50, cy + 3) + ","
                        + road("70", cx - 50, cy, cx + 50, cy) + "]}");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String road(String type, double x1, double y1, double x2, double y2) {
        return "{\"properties\":{\"tyyp\":" + type + ",\"tee\":1},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[["
                + x1 + "," + y1 + "],[" + x2 + "," + y2 + "]]}}";
    }

    private static void answer(HttpExchange x, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }
}
