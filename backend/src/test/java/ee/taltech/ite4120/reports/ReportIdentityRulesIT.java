package ee.taltech.ite4120.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** Business tests 14 to 20 of REPORTS.01, identity, limits, geolocation and signing. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "reports.coordinators=coordinator")
@ActiveProfiles("test")
class ReportIdentityRulesIT {

    private static final String COORDINATOR = "coordinator";
    private static final String PERSON = "PNOEE-40504040001-DEM2-Q";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final FakeSmartId SMART_ID = new FakeSmartId("PNOEE-40504040001");
    private static volatile int registerStatus;
    private static volatile boolean outsideEstonia;
    private static final HttpServer REGISTER = startRegister();

    @Autowired
    Environment environment;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("reports.address-register.url", () -> "http://localhost:" + REGISTER.getAddress().getPort());
        registry.add("reports.smart-id.url", SMART_ID::url);
        registry.add("reports.smart-id.trusted-issuer", () -> FakeSmartId.ISSUER);
    }

    @BeforeEach
    void cleanSlate() {
        registerStatus = 200;
        SMART_ID.behaviour = FakeSmartId.Behaviour.OK;
        jdbc.update("delete from reports.signing");
        jdbc.update("delete from reports.sighting");
        jdbc.update("delete from reports.status_change");
        jdbc.update("delete from reports.report");
        jdbc.update("delete from reports.reporter");
    }

    /** 14. A guest may send one report a day. */
    @Test
    void aGuestMaySendOneReportADay() {
        assertThat(guest().status).isEqualTo(201);

        Answer second = guest();
        assertProblem(second, 429, "Smart-ID");
        assertThat(Long.parseLong(second.retryAfter)).isBetween(86_000L, 86_400L);
        assertThat(count()).isEqualTo(1);

        age("25 hours");
        assertThat(guest().status).isEqualTo(201);
    }

    /** 15. A device is limited by its trust level. */
    @Test
    void aDeviceIsLimitedByItsTrustLevel() {
        String device = UUID.randomUUID().toString();
        assertThat(report(device).status).isEqualTo(201);
        Answer tooSoon = report(device);
        assertProblem(tooSoon, 429, "try again");
        assertThat(Long.parseLong(tooSoon.retryAfter)).isBetween(500L, 600L);
        assertThat(report(UUID.randomUUID().toString()).status).as("another device is not affected").isEqualTo(201);

        age("11 minutes");
        for (int i = 0; i < 4; i++) {
            assertThat(report(device).status).isEqualTo(201);
            age("11 minutes");
        }
        assertProblem(report(device), 429, "try again");

        String trusted = trustedDevice();
        assertThat(report(trusted).status).isEqualTo(201);
        age("6 minutes");
        assertThat(report(trusted).status).as("level 1 waits 5 minutes").isEqualTo(201);
        age("4 minutes");
        assertProblem(report(trusted), 429, "try again");

        assertProblem(report("not-a-uuid"), 400, "X-Device-Id");
    }

    /** 15. Parallel requests cannot pass the limit. */
    @Test
    void parallelRequestsCannotPassTheLimit() throws Exception {
        String device = UUID.randomUUID().toString();
        List<Callable<Answer>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> report(device));
        }
        int created = 0;
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (Future<Answer> f : pool.invokeAll(calls)) {
                created += f.get().status == 201 ? 1 : 0;
            }
        }
        assertThat(created).isEqualTo(1);
    }

    /** 16. A false report resets trust. */
    @Test
    void aFalseReportResetsTrust() {
        String device = trustedDevice();
        String rejected = report(device).text("referenceNumber");
        assertThat(change(rejected, "REJECTED", "Private land", false).status).isEqualTo(200);
        age("6 minutes");
        String fake = report(device).text("referenceNumber");
        assertThat(fake).as("a normal rejection keeps level 1").isNotEmpty();

        assertThat(change(fake, "REJECTED", "Nothing there", true).status).isEqualTo(200);
        age("6 minutes");
        assertProblem(report(device), 429, "try again");

        String other = report(UUID.randomUUID().toString()).text("referenceNumber");
        assertProblem(change(other, "ACCEPTED", null, true), 400, "falseReport");
    }

    /** 17. Changing the device does not remove every limit. */
    @Test
    void changingTheDeviceDoesNotRemoveEveryLimit() {
        for (int i = 0; i < 20; i++) {
            assertThat(report(UUID.randomUUID().toString()).status).isEqualTo(201);
        }
        assertProblem(report(UUID.randomUUID().toString()), 429, "Smart-ID");
        assertThat(sign().text("state")).as("a signed resident is not affected").isEqualTo("SIGNED");
    }

    /** 18. A signed report is stored only after a verified signature. */
    @Test
    void aSignedReportIsStoredOnlyAfterAVerifiedSignature() {
        SMART_ID.behaviour = FakeSmartId.Behaviour.REFUSED;
        assertThat(sign().text("state")).isEqualTo("REFUSED");
        SMART_ID.behaviour = FakeSmartId.Behaviour.WRONG_SIGNATURE;
        assertThat(sign().text("state")).isEqualTo("FAILED");
        SMART_ID.behaviour = FakeSmartId.Behaviour.OTHER_PERSON;
        assertThat(sign().text("state")).isEqualTo("FAILED");
        SMART_ID.behaviour = FakeSmartId.Behaviour.RUNNING;
        assertThat(sign().text("state")).isEqualTo("PENDING");
        SMART_ID.behaviour = FakeSmartId.Behaviour.DOWN;
        assertProblem(startSigning(PERSON), 502, "Smart-ID");
        assertThat(count()).isZero();

        SMART_ID.behaviour = FakeSmartId.Behaviour.OK;
        Answer start = startSigning(PERSON);
        assertThat(start.status).as(start.raw).isEqualTo(201);
        assertThat(start.text("verificationCode")).isEqualTo("1234");
        String path = "/api/reports/signings/" + start.text("token");
        Answer done = get("resident", path);
        assertThat(done.text("state")).isEqualTo("SIGNED");
        assertThat(get("resident", path).text("referenceNumber")).isEqualTo(done.text("referenceNumber"));
        assertThat(count()).as("asking twice creates one report").isEqualTo(1);
        assertThat(done.text("name")).as("the name is told to the signing device only").isEqualTo("MARI MAASIKAS");
        assertThat(get("resident", path).body.has("name")).as("and it is not stored").isFalse();
        assertThat(get("resident", path).body.path("standing").path("points").asInt(-1)).isZero();

        Answer stored = get(COORDINATOR, "/api/reports/" + done.text("referenceNumber"));
        assertThat(stored.text("channel")).isEqualTo("SIGNED");
        String everything = jdbc.queryForList("select r::text from reports.report r").toString()
                + jdbc.queryForList("select r::text from reports.reporter r").toString()
                + jdbc.queryForList("select s::text from reports.signing s").toString();
        assertThat(everything).as("no personal code is stored").doesNotContain("40504040001", "MAASIKAS");
        assertThat(jdbc.queryForObject("select count(*) from reports.signing where state = 'SIGNED' and payload is not null",
                Integer.class)).isZero();

        assertProblem(startSigning("PNOEE-38001010000-ABCD-Q"), 400, "person");
        assertProblem(get("resident", "/api/reports/signings/" + UUID.randomUUID()), 404, "signing");
    }

    /** 18. A signed resident has own limits. */
    @Test
    void aSignedResidentWaitsAMinute() {
        assertThat(sign().text("state")).isEqualTo("SIGNED");
        assertProblem(startSigning(PERSON), 429, "try again");
        age("2 minutes");
        assertThat(sign().text("state")).isEqualTo("SIGNED");
    }

    /** 19. The municipality comes from the place. */
    @Test
    void theMunicipalityComesFromThePlace() {
        Answer byPoint = post("resident", "/api/reports", body().put("latitude", 59.32).put("longitude", 24.55),
                UUID.randomUUID().toString());
        assertThat(byPoint.text("municipalityName")).isEqualTo("Saue vald");
        assertThat(byPoint.text("municipalityCode")).isEqualTo("725");
        assertThat(byPoint.text("responsibleUnit")).isEqualTo("Environment Department");

        assertThat(report(UUID.randomUUID().toString()).text("municipalityName")).isEqualTo("Tallinn");

        Answer place = get("resident", "/api/reports/place?latitude=59.32&longitude=24.55");
        assertThat(place.text("municipalityName")).isEqualTo("Saue vald");
        assertProblem(get("resident", "/api/reports/place?latitude=95&longitude=24.55"), 400, "latitude");
        assertProblem(get("resident", "/api/reports/place?latitude=abc&longitude=24.55"), 400, "latitude");

        Answer list = get(COORDINATOR, "/api/reports?municipalityCode=725&limit=100");
        assertThat(list.body.findValuesAsText("referenceNumber")).containsExactly(byPoint.text("referenceNumber"));

        ObjectNode chosen = body().put("address", "Harju maakond, Saue vald, Toonekure").put("latitude", 59.32).put("longitude", 24.55);
        assertThat(post("resident", "/api/reports", chosen, UUID.randomUUID().toString()).text("addressVerified"))
                .as("the register itself gave this address for the point").isEqualTo("true");
        assertThat(post("resident", "/api/reports", body().put("address", "Toonekure"), UUID.randomUUID().toString())
                .text("addressVerified")).as("ambiguous text alone").isEqualTo("false");

        outsideEstonia = true;
        assertProblem(get("resident", "/api/reports/place?latitude=60.17&longitude=24.94"), 404, "municipality");
        outsideEstonia = false;

        registerStatus = 503;
        Answer down = report(UUID.randomUUID().toString());
        assertThat(down.status).isEqualTo(201);
        assertThat(down.body.has("municipalityName")).isFalse();
        assertProblem(get("resident", "/api/reports/place?latitude=59.32&longitude=24.55"), 502, "address register");
    }

    /** 20. A resident sees why a report was rejected. */
    @Test
    void aResidentSeesWhyAReportWasRejected() {
        String reference = guest().text("referenceNumber");
        change(reference, "REJECTED", "Private land", false);
        Answer seen = get("resident", "/api/reports/" + reference);
        assertThat(seen.text("rejectionReason")).isEqualTo("Private land");
        assertThat(seen.raw).doesNotContain(COORDINATOR);
    }

    // ---- helpers ---------------------------------------------------------

    private record Answer(int status, String contentType, String retryAfter, String raw, JsonNode body) {
        String text(String field) {
            return body.path(field).asText();
        }
    }

    private static void assertProblem(Answer a, int status, String detailPart) {
        assertThat(a.status).as(a.raw).isEqualTo(status);
        assertThat(a.contentType).startsWith("application/problem+json");
        assertThat(a.text("detail")).contains(detailPart);
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from reports.report", Integer.class);
    }

    /** Moves every stored report and status change into the past, around the sys trigger. */
    private void age(String interval) {
        jdbc.execute("set session_replication_role = replica;"
                + "update reports.report set sys_created_at = sys_created_at - interval '" + interval + "';"
                + "update reports.status_change set sys_created_at = sys_created_at - interval '" + interval + "';"
                + "set session_replication_role = origin");
    }

    /** A device with three resolved reports, all older than a day. */
    private String trustedDevice() {
        String device = UUID.randomUUID().toString();
        for (int i = 0; i < 3; i++) {
            String reference = report(device).text("referenceNumber");
            assertThat(change(reference, "ACCEPTED", null, false).status).isEqualTo(200);
            assertThat(change(reference, "IN_PROGRESS", null, false).status).isEqualTo(200);
            assertThat(change(reference, "RESOLVED", null, false).status).isEqualTo(200);
            age("25 hours");
        }
        return device;
    }

    private Answer guest() {
        return post("resident", "/api/reports", body(), null);
    }

    private Answer report(String device) {
        return post("resident", "/api/reports", body(), device);
    }

    private Answer startSigning(String person) {
        return post("resident", "/api/reports/signings", body().put("person", person), null);
    }

    private Answer sign() {
        Answer start = startSigning(PERSON);
        assertThat(start.status).as(start.raw).isEqualTo(201);
        return get("resident", "/api/reports/signings/" + start.text("token"));
    }

    private Answer change(String reference, String to, String reason, boolean falseReport) {
        ObjectNode node = json.createObjectNode().put("toStatus", to).put("falseReport", falseReport);
        if (reason != null) {
            node.put("reason", reason);
        }
        return post(solving(to) ? unit(reference) : COORDINATOR, "/api/reports/" + reference + "/status", node, null);
    }

    private static boolean solving(String to) {
        return "IN_PROGRESS".equals(to) || "RESOLVED".equals(to);
    }

    /** The inbox address of the unit the report was handed to, which is also its mock user. */
    private String unit(String reference) {
        return get(COORDINATOR, "/api/reports/" + reference).text("handoverInbox");
    }

    private ObjectNode body() {
        return json.createObjectNode().put("categoryCode", "LITTER").put("address", "Raja 15, Tallinn")
                .put("description", "Rubbish bags next to the footpath");
    }

    private Answer get(String user, String path) {
        return send(user, request(path).GET());
    }

    private Answer post(String user, String path, JsonNode body, String device) {
        HttpRequest.Builder r = request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return send(user, device == null ? r : r.header("X-Device-Id", device));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path));
    }

    private Answer send(String user, HttpRequest.Builder request) {
        try {
            HttpResponse<String> r = http.send(request.header("Authorization", "Bearer " + user).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new Answer(r.statusCode(), r.headers().firstValue("Content-Type").orElse(""),
                    r.headers().firstValue("Retry-After").orElse(null), r.body(),
                    r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body()));
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
                boolean point = exchange.getRequestURI().getQuery().startsWith("x=");
                String answer = point
                        ? "{\"addresses\":[{\"ads_oid\":\"KY1\",\"taisaadress\":\"Harju maakond, Saue vald, Toonekure\","
                                + "\"ehakov\":\"725\",\"omavalitsus\":\"Saue vald\"}]}"
                        : "{\"addresses\":[{\"ads_oid\":\"ME1\",\"taisaadress\":\"Harju maakond, Tallinn, Raja tn 15\","
                                + "\"ehakov\":\"784\",\"omavalitsus\":\"Tallinn\"}]}";
                if (!point && exchange.getRequestURI().getQuery().contains("Toonekure")) {
                    answer = "{\"addresses\":[{\"ads_oid\":\"A\",\"ehakov\":\"725\"},{\"ads_oid\":\"B\",\"ehakov\":\"725\"}]}";
                }
                if (outsideEstonia) {
                    answer = "{\"error\":\"EIEESTI\"}";
                }
                byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(outsideEstonia ? 500 : registerStatus, bytes.length);
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
