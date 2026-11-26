package ee.taltech.ite4120.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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

/** Business tests 37 to 40 of REPORTS.01, the handover of a report to the responsible unit. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"reports.coordinators=coordinator", "reports.address-register.mode=xroad",
                "reports.unit-channel.mode=xroad", "reports.unit-channel.xroad.client=ee-test/EDU/taltech/ite4120"})
@ActiveProfiles("test")
class ReportHandoverRulesIT {

    private static final String COORDINATOR = "coordinator";
    private static final String UNIT = "environment@saue-vald.example";
    private static final String OTHER_UNIT = "roads@saue-vald.example";
    private static final String SERVICE = "/r1/ee-test/GOV/saue-vald/reports-inbox/receiveReport";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static volatile int inboxStatus;
    private static volatile String lastClient;
    private static volatile String lastPath;
    private static volatile String lastBody;
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
        registry.add("reports.unit-channel.xroad.security-server-url", () -> url);
        registry.add("reports.road-map.url", () -> url + "/wfs");
    }

    @BeforeEach
    void everythingWorks() {
        inboxStatus = 200;
        lastPath = null;
        jdbc.update("update reports.report set ip_hash = null");
    }

    /** 37. An accepted report is handed to the unit over X-Road. */
    @Test
    void anAcceptedReportIsHandedToTheUnitOverXRoad() {
        String reference = report();
        assertThat(change(COORDINATOR, reference, "ACCEPTED").status).isEqualTo(200);

        assertThat(lastPath).isEqualTo(SERVICE);
        assertThat(lastClient).isEqualTo("ee-test/EDU/taltech/ite4120");
        assertThat(lastBody).contains(reference).contains("LITTER");

        Answer seen = get(COORDINATOR, "/api/reports/" + reference);
        assertThat(seen.text("handoverUnit")).isEqualTo("Environment Department");
        assertThat(seen.text("handoverInbox")).isEqualTo(UNIT);
        assertThat(seen.text("handoverState")).isEqualTo("SENT");
        assertThat(seen.text("handoverMessageId")).isEqualTo("INBOX-1");
        assertThat(seen.text("handoverService")).isEqualTo("ee-test/GOV/saue-vald/reports-inbox/receiveReport");

        Answer resident = get("resident", "/api/reports/" + reference);
        assertThat(resident.text("handoverUnit")).isEqualTo("Environment Department");
        assertThat(resident.text("handedAt")).isNotEmpty();
        assertThat(resident.raw).doesNotContain(UNIT).doesNotContain("INBOX-1");
    }

    /** 38. Only the unit solves. */
    @Test
    void onlyTheUnitSolves() throws IOException {
        String reference = report();
        change(COORDINATOR, reference, "ACCEPTED");

        assertProblem(change(COORDINATOR, reference, "IN_PROGRESS"), 403, "unit");
        assertProblem(change(OTHER_UNIT, reference, "IN_PROGRESS"), 403, "unit");
        assertThat(get(OTHER_UNIT, "/api/reports/handed").raw).doesNotContain(reference);

        assertThat(get(UNIT, "/api/reports/handed").raw).contains(reference);
        assertThat(get(UNIT, "/api/reports/" + reference).text("description")).contains("Rubbish");
        assertThat(get(COORDINATOR, "/api/reports/" + reference).text("handoverState")).isEqualTo("OPENED");

        assertThat(change(UNIT, reference, "IN_PROGRESS").status).isEqualTo(200);
        assertThat(upload(UNIT, "/api/reports/" + reference + "/photos", jpeg()).status).isEqualTo(201);
        assertThat(post(UNIT, "/api/reports/" + reference + "/updates",
                json.createObjectNode().put("text", "We come on Monday")).status).isEqualTo(201);
        assertThat(change(UNIT, reference, "RESOLVED").status).isEqualTo(200);
        assertThat(get("resident", "/api/reports/" + reference).text("status")).isEqualTo("RESOLVED");
    }

    /** 39. The unit returns a report that is not theirs. */
    @Test
    void theUnitReturnsAReportThatIsNotTheirs() {
        String reference = report();
        change(COORDINATOR, reference, "ACCEPTED");

        assertProblem(post(UNIT, "/api/reports/" + reference + "/handover/return",
                json.createObjectNode().put("reason", " ")), 400, "reason");
        assertThat(post(UNIT, "/api/reports/" + reference + "/handover/return",
                json.createObjectNode().put("reason", "This is a private yard")).status).isEqualTo(200);

        Answer seen = get(COORDINATOR, "/api/reports/" + reference);
        assertThat(seen.text("handoverState")).isEqualTo("RETURNED");
        assertThat(seen.text("handoverReason")).isEqualTo("This is a private yard");
        assertProblem(change(UNIT, reference, "IN_PROGRESS"), 403, "unit");

        assertThat(post(COORDINATOR, "/api/reports/" + reference + "/forward", json.createObjectNode()
                .put("kind", "OTHER").put("name", "Saue Property Office").put("reason", "Private yard")).status)
                .isEqualTo(200);
        Answer forwarded = get(COORDINATOR, "/api/reports/" + reference);
        assertThat(forwarded.text("handoverState")).isEqualTo("SENT");
        assertThat(forwarded.text("handoverInbox")).isEqualTo("environment@saue-property-office.example");
    }

    /** 40. A channel that does not answer does not undo the acceptance. */
    @Test
    void aChannelThatDoesNotAnswerDoesNotUndoTheAcceptance() {
        String reference = report();
        inboxStatus = 500;
        assertThat(change(COORDINATOR, reference, "ACCEPTED").status).isEqualTo(200);
        Answer failed = get(COORDINATOR, "/api/reports/" + reference);
        assertThat(failed.text("status")).isEqualTo("ACCEPTED");
        assertThat(failed.text("handoverState")).isEqualTo("FAILED");

        assertProblem(post("resident", "/api/reports/" + reference + "/handover", json.createObjectNode()),
                403, "coordinator");
        inboxStatus = 200;
        assertThat(post(COORDINATOR, "/api/reports/" + reference + "/handover", json.createObjectNode()).status)
                .isEqualTo(200);
        assertThat(get(COORDINATOR, "/api/reports/" + reference).text("handoverState")).isEqualTo("SENT");
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

    private String report() {
        Answer a = post("resident", "/api/reports", json.createObjectNode().put("categoryCode", "LITTER")
                .put("address", "Toonekure, Saue vald").put("latitude", 59.32).put("longitude", 24.55)
                .put("description", "Rubbish bags next to the footpath"));
        assertThat(a.status).as(a.raw).isEqualTo(201);
        return a.text("referenceNumber");
    }

    private Answer change(String user, String reference, String to) {
        return post(user, "/api/reports/" + reference + "/status", json.createObjectNode().put("toStatus", to));
    }

    private Answer get(String user, String path) {
        return send(user, request(path).GET());
    }

    private Answer post(String user, String path, JsonNode body) {
        return send(user, request(path).header("Content-Type", "application/json")
                .header("X-Device-Id", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
    }

    private Answer upload(String user, String path, byte[] content) {
        String boundary = "b" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(content);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return send(user, request(path).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())));
    }

    private static byte[] jpeg() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path));
    }

    private Answer send(String user, HttpRequest.Builder builder) {
        try {
            HttpResponse<String> r = http.send(builder.header("Authorization", "Bearer " + user).build(),
                    HttpResponse.BodyHandlers.ofString());
            String raw = r.body();
            return new Answer(r.statusCode(), r.headers().firstValue("Content-Type").orElse(""), raw,
                    raw == null || raw.isBlank() ? json.createObjectNode() : json.readTree(raw));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** The security server, the inbox of the unit and the road map in one stub. */
    private static HttpServer startOutside() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/r1/", x -> {
                if ("POST".equals(x.getRequestMethod())) {
                    lastClient = x.getRequestHeaders().getFirst("X-Road-Client");
                    lastPath = x.getRequestURI().getPath();
                    lastBody = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (inboxStatus != 200) {
                        x.getResponseHeaders().add("X-Road-Error", "Server.ServerProxy.ServiceFailed");
                        answer(x, inboxStatus, "{\"type\":\"Server.ServerProxy.ServiceFailed\"}");
                    } else {
                        x.getResponseHeaders().add("X-Road-Id", "INBOX-1");
                        answer(x, 200, "{\"received\":true}");
                    }
                    return;
                }
                answer(x, 200, "{\"addresses\":[{\"ads_oid\":\"KY1\",\"taisaadress\":\"Harju maakond, Saue vald, Toonekure\","
                        + "\"ehakov\":\"725\",\"omavalitsus\":\"Saue vald\"}]}");
            });
            server.createContext("/wfs", x -> answer(x, 200, "{\"features\":[]}"));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void answer(HttpExchange x, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }
}
