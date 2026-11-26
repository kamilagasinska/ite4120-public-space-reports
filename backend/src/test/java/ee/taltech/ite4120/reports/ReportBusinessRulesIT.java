package ee.taltech.ite4120.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.AfterAll;
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

/**
 * The business tests of docs/src/specifications/REPORTS.01, numbered as there.
 * They call the API over HTTP, so they also pin the status codes and the error
 * shape. The address register is a stub whose answer each test chooses.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "reports.coordinators=coordinator")
@ActiveProfiles("test")
class ReportBusinessRulesIT {

    private static final String RESIDENT = "resident";
    private static final String COORDINATOR = "coordinator";
    private static final String FOUND =
            "{\"addresses\":[{\"ads_oid\":\"ME00000001\",\"taisaadress\":\"Harju maakond, Tallinn, Raja 15\"}]}";
    private static final String NOT_FOUND = "{\"addresses\":[]}";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final HttpServer REGISTER = startRegister();
    private static volatile int registerStatus;
    private static volatile String registerBody;

    @Autowired
    Environment environment;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @DynamicPropertySource
    static void addressRegister(DynamicPropertyRegistry registry) {
        registry.add("reports.address-register.url",
                () -> "http://localhost:" + REGISTER.getAddress().getPort());
    }

    @BeforeEach
    void registerKnowsTheAddress() {
        registerStatus = 200;
        registerBody = FOUND;
        /* Every test sends from one IP address, whose own limit belongs to ReportIdentityRulesIT. */
        jdbc.update("update reports.report set ip_hash = null");
    }

    @AfterAll
    static void stopRegister() {
        REGISTER.stop(0);
    }

    /** 1. A report needs a category and an address. */
    @Test
    void aReportNeedsACategoryAndAnAddress() {
        assertProblem(post(RESIDENT, "/api/reports", body("categoryCode", null)), 400, "categoryCode");
        assertProblem(post(RESIDENT, "/api/reports", body("address", null)), 400, "address");
        assertProblem(post(RESIDENT, "/api/reports", body("categoryCode", "NOISE")), 400, "categoryCode");
    }

    /** 2. A description must be at least 10 characters. */
    @Test
    void aDescriptionMustBeAtLeastTenCharacters() {
        assertProblem(post(RESIDENT, "/api/reports", body("description", "hole")), 400, "description");
        assertProblem(post(RESIDENT, "/api/reports", body("description", "hole          ")), 400, "description");
    }

    /** 3. A new report is Received and has a reference number. */
    @Test
    void aNewReportIsReceivedAndHasAReferenceNumber() {
        Answer first = post(RESIDENT, "/api/reports", body());
        Answer second = post(RESIDENT, "/api/reports", body());

        assertThat(first.status).isEqualTo(201);
        String reference = first.text("referenceNumber");
        assertThat(reference).matches("R-\\d{4}-[A-Z0-9]{6}");
        assertThat(first.location).isEqualTo("/api/reports/" + reference);
        assertThat(first.text("status")).isEqualTo("RECEIVED");
        assertThat(second.text("referenceNumber")).isNotEqualTo(reference);
    }

    /** 4. A repeated submit does not create a duplicate. */
    @Test
    void aRepeatedSubmitDoesNotCreateADuplicate() {
        String key = UUID.randomUUID().toString();
        Answer first = post(RESIDENT, "/api/reports", body("submissionKey", key));
        Answer again = post(RESIDENT, "/api/reports", body("submissionKey", key));

        assertThat(first.status).isEqualTo(201);
        assertThat(again.status).isEqualTo(200);
        assertThat(again.text("referenceNumber")).isEqualTo(first.text("referenceNumber"));
        assertThat(jdbc.queryForObject("select count(*) from reports.report where submission_key = ?",
                Integer.class, key)).isEqualTo(1);
    }

    /** 5. A map point is both coordinates or none. */
    @Test
    void aMapPointIsBothCoordinatesOrNone() {
        assertProblem(post(RESIDENT, "/api/reports", body("latitude", "59.4")), 400, "latitude and longitude");
        assertProblem(post(RESIDENT, "/api/reports", body("latitude", "95", "longitude", "24.6")), 400, "latitude");
        assertThat(post(RESIDENT, "/api/reports", body("latitude", "59.4", "longitude", "24.6")).status)
                .isEqualTo(201);
    }

    /** 6. Each report goes to the responsible unit. */
    @Test
    void eachReportGoesToTheResponsibleUnit() {
        String litter = submit("LITTER");
        String road = submit("ROAD_DAMAGE");

        assertThat(get(RESIDENT, "/api/reports/" + litter).text("responsibleUnit")).isEqualTo("Environment Department");
        assertThat(get(RESIDENT, "/api/reports/" + road).text("responsibleUnit")).isEqualTo("Roads Department");
    }

    /** 7. A resident can look up a report by its reference number. */
    @Test
    void aResidentCanLookUpAReportByItsReferenceNumber() {
        String reference = post(RESIDENT, "/api/reports", body("reporterContact", "someone@example.com"))
                .text("referenceNumber");

        Answer resident = get("another-resident", "/api/reports/" + reference);
        assertThat(resident.status).isEqualTo(200);
        assertThat(resident.text("status")).isEqualTo("RECEIVED");
        assertThat(resident.raw).doesNotContain("someone@example.com", "Rubbish bags", "\"resident\"");
        assertThat(get(COORDINATOR, "/api/reports/" + reference).text("reporterContact"))
                .isEqualTo("someone@example.com");
        assertProblem(get(RESIDENT, "/api/reports/R-2026-NOSUCH"), 404, "R-2026-NOSUCH");
    }

    /** 8. Only allowed status changes are accepted. */
    @Test
    void onlyAllowedStatusChangesAreAccepted() {
        String reference = submit("LITTER");
        assertProblem(change(COORDINATOR, reference, "RESOLVED", null), 409, "RECEIVED");
        assertProblem(change(COORDINATOR, reference, "IN_PROGRESS", null), 409, "RECEIVED");
        assertThat(change(COORDINATOR, reference, "ACCEPTED", null).status).isEqualTo(200);
        assertThat(change(unit(reference), reference, "IN_PROGRESS", null).status).isEqualTo(200);
        assertProblem(change(COORDINATOR, reference, "REJECTED", "duplicate"), 409, "IN_PROGRESS");
        assertThat(change(unit(reference), reference, "RESOLVED", null).status).isEqualTo(200);

        assertProblem(change(COORDINATOR, reference, "RECEIVED", null), 409, "RESOLVED");
        assertThat(get(RESIDENT, "/api/reports/" + reference).text("status")).isEqualTo("RESOLVED");
        assertProblem(change(COORDINATOR, reference, "NONSENSE", null), 400, "toStatus");
    }

    /** 9. Rejecting needs a reason. */
    @Test
    void rejectingNeedsAReason() {
        String reference = submit("LITTER");
        assertProblem(change(COORDINATOR, reference, "REJECTED", null), 400, "reason");
        assertProblem(change(COORDINATOR, reference, "REJECTED", "   "), 400, "reason");

        Answer rejected = change(COORDINATOR, reference, "REJECTED", "Private land");
        assertThat(rejected.status).isEqualTo(200);
        assertThat(rejected.text("status")).isEqualTo("REJECTED");
    }

    /** 10. Every status change is recorded. */
    @Test
    void everyStatusChangeIsRecorded() {
        String reference = submit("LITTER");
        change(COORDINATOR, reference, "ACCEPTED", null);
        String unit = unit(reference);
        change(unit, reference, "IN_PROGRESS", null);
        change(unit, reference, "RESOLVED", "Cleaned");

        Answer history = get(COORDINATOR, "/api/reports/" + reference + "/history");
        assertThat(history.status).isEqualTo(200);
        assertThat(history.body.findValuesAsText("toStatus")).containsExactly("ACCEPTED", "IN_PROGRESS", "RESOLVED");
        assertThat(history.body.findValuesAsText("fromStatus")).containsExactly("RECEIVED", "ACCEPTED", "IN_PROGRESS");
        assertThat(history.body.findValuesAsText("sysCreatedBy")).containsExactly(COORDINATOR, unit, unit);
        assertThat(history.body.findValuesAsText("sysCreatedAt")).hasSize(3).doesNotContain("null");
    }

    /** 11. A report is accepted even when the address register is down. */
    @Test
    void aReportIsAcceptedEvenWhenTheAddressRegisterIsDown() {
        assertThat(post(RESIDENT, "/api/reports", body()).text("addressVerified")).isEqualTo("true");

        registerStatus = 503;
        Answer down = post(RESIDENT, "/api/reports", body());
        assertThat(down.status).isEqualTo(201);
        assertThat(down.text("addressVerified")).isEqualTo("false");

        registerStatus = 200;
        registerBody = "<html>not json";
        Answer broken = post(RESIDENT, "/api/reports", body());
        assertThat(broken.status).isEqualTo(201);
        assertThat(broken.text("addressVerified")).isEqualTo("false");

        registerBody = NOT_FOUND;
        Answer unknown = post(RESIDENT, "/api/reports", body());
        assertThat(unknown.status).isEqualTo(201);
        assertThat(unknown.text("addressVerified")).isEqualTo("false");
    }

    /** 12. The list can be filtered. */
    @Test
    void theListCanBeFiltered() {
        String litter = submit("LITTER");
        String road = submit("ROAD_DAMAGE");

        Answer list = get(COORDINATOR, "/api/reports?categoryCode=LITTER&limit=1000");
        assertThat(list.status).isEqualTo(200);
        assertThat(list.body.findValuesAsText("referenceNumber")).contains(litter).doesNotContain(road);
        assertThat(list.body.findValuesAsText("categoryCode")).containsOnly("LITTER");
        assertThat(list.body.findValuesAsText("daysOpen")).contains("0");

        assertThat(get(COORDINATOR, "/api/reports?status=RECEIVED&limit=1000").body.findValuesAsText("status"))
                .containsOnly("RECEIVED");
        assertProblem(get(COORDINATOR, "/api/reports?categoryCode=NOISE"), 400, "categoryCode");
        assertProblem(get(COORDINATOR, "/api/reports?status=LOST"), 400, "status");
        assertProblem(get(COORDINATOR, "/api/reports?sort=nothing"), 400, "sort");
        assertProblem(get(COORDINATOR, "/api/reports?limit=-1"), 400, "limit");
    }

    /** 13. Only a coordinator can change a status. */
    @Test
    void onlyACoordinatorCanChangeAStatus() {
        String reference = submit("LITTER");
        assertProblem(change(RESIDENT, reference, "ACCEPTED", null), 403, "coordinator");
        assertThat(get(RESIDENT, "/api/reports/" + reference).text("status")).isEqualTo("RECEIVED");
        assertProblem(get(RESIDENT, "/api/reports"), 403, "coordinator");
        assertProblem(get(RESIDENT, "/api/reports/" + reference + "/history"), 403, "coordinator");
    }

    /** API section: every error is a problem document, also for a body that cannot be read. */
    @Test
    void anUnreadableBodyIsAProblemDocument() {
        assertProblem(post(RESIDENT, "/api/reports", "{ this is not json"), 400, "body");
    }

    // ---- helpers ---------------------------------------------------------

    private record Answer(int status, String contentType, String location, String raw, JsonNode body) {
        String text(String field) {
            return body.path(field).asText();
        }
    }

    private static void assertProblem(Answer answer, int status, String detailPart) {
        assertThat(answer.status).as(answer.raw).isEqualTo(status);
        assertThat(answer.contentType).startsWith("application/problem+json");
        assertThat(answer.text("detail")).contains(detailPart);
    }

    private String submit(String category) {
        Answer answer = post(RESIDENT, "/api/reports", body("categoryCode", category));
        assertThat(answer.status).as(answer.raw).isEqualTo(201);
        return answer.text("referenceNumber");
    }

    /** The inbox address of the unit the report was handed to, which is also its mock user. */
    private String unit(String reference) {
        if (get(COORDINATOR, "/api/reports/" + reference).text("handoverInbox").isEmpty()) {
            post(COORDINATOR, "/api/reports/" + reference + "/forward", json.createObjectNode().put("kind", "MUNICIPALITY")
                    .put("name", "Tallinn, Environment Department").put("reason", "Known city").toString());
        }
        return get(COORDINATOR, "/api/reports/" + reference).text("handoverInbox");
    }

    private Answer change(String user, String reference, String toStatus, String reason) {
        var node = json.createObjectNode().put("toStatus", toStatus);
        if (reason != null) {
            node.put("reason", reason);
        }
        return post(user, "/api/reports/" + reference + "/status", node.toString());
    }

    /** A valid report with the given fields replaced; a null value removes the field. */
    private String body(String... replacements) {
        var node = json.createObjectNode()
                .put("categoryCode", "LITTER")
                .put("address", "Raja 15, Tallinn")
                .put("description", "Rubbish bags next to the footpath");
        for (int i = 0; i < replacements.length; i += 2) {
            if (replacements[i + 1] == null) {
                node.remove(replacements[i]);
            } else {
                node.put(replacements[i], replacements[i + 1]);
            }
        }
        return node.toString();
    }

    private Answer get(String user, String path) {
        return send(user, request(path).GET());
    }

    private Answer post(String user, String path, String body) {
        return send(user, request(path).header("Content-Type", "application/json")
                .header("X-Device-Id", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path));
    }

    private Answer send(String user, HttpRequest.Builder request) {
        try {
            HttpResponse<String> r = http.send(request.header("Authorization", "Bearer " + user).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body());
            return new Answer(r.statusCode(), r.headers().firstValue("Content-Type").orElse(""),
                    r.headers().firstValue("Location").orElse(null), r.body(), body);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startRegister() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/gazetteer", exchange -> {
                byte[] bytes = registerBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(registerStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
