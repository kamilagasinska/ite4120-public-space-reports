package ee.taltech.ite4120.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Business tests 26 to 33 of REPORTS.01, categories, photos, map, steps, reminders and points. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"reports.coordinators=coordinator", "reports.address-register.url=http://127.0.0.1:9"})
@ActiveProfiles("test")
class ReportDialogueRulesIT {

    private static final String COORDINATOR = "coordinator";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    Environment environment;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void cleanSlate() {
        for (String table : new String[] {"signing", "sighting", "photo", "message", "forwarding", "status_change", "report", "reporter"}) {
            jdbc.update("delete from reports." + table);
        }
    }

    /** 26. There are nine categories. */
    @Test
    void thereAreNineCategories() {
        Answer list = get("resident", "/api/reports/categories");
        assertThat(list.body.findValuesAsText("code")).containsExactlyInAnyOrder("ROAD_DAMAGE", "LITTER", "LIGHTING",
                "TREES", "DANGEROUS_OBJECT", "DEAD_ANIMAL", "VANDALISM", "PUBLIC_ORDER", "OTHER");
        assertThat(report("OTHER", device()).text("recipientName")).isEqualTo("Coordinator decides");
    }

    /** 27. A photo is cleaned. */
    @Test
    void aPhotoIsCleaned() throws IOException {
        Sent s = send("LITTER");
        Answer added = photo("resident", s, jpegWithGps(2400, 1200));
        assertThat(added.status).as(added.raw).isEqualTo(201);

        byte[] stored = jdbc.queryForObject("select content from reports.photo", byte[].class);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("SECRET-GPS", "Exif");
        assertThat(ImageIO.read(new ByteArrayInputStream(stored)).getWidth()).isEqualTo(1600);

        assertProblem(photo("resident", s, "this is text".getBytes(StandardCharsets.UTF_8)), 400, "JPEG or PNG");
        assertProblem(photo("resident", new Sent(s.reference, "wrong-key"), jpegWithGps(10, 10)), 404, "no report");
        for (int i = 0; i < 4; i++) {
            assertThat(photo("resident", s, jpegWithGps(10, 10)).status).isEqualTo(201);
        }
        assertProblem(photo("resident", s, jpegWithGps(10, 10)), 400, "5 photos");
        assertProblem(photo("resident", s, new byte[9 * 1024 * 1024]), 400, "8 MB");
    }

    /** 28. A resident sees other people's photos and text only after publication. */
    @Test
    void textAndPhotosArePublicOnlyAfterPublication() throws IOException {
        Sent s = send("LITTER");
        photo("resident", s, jpegWithGps(20, 20));
        String id = mapId();
        Answer before = get("resident", "/api/reports/map/" + id);
        assertThat(before.body.has("description")).isFalse();
        assertThat(before.body.has("address")).isFalse();
        assertThat(before.body.path("photos")).isEmpty();
        String photoId = jdbc.queryForObject("select id from reports.photo", Long.class).toString();
        assertThat(get("resident", "/api/reports/map/" + id + "/photos/" + photoId).status).isEqualTo(404);
        assertThat(status(get(COORDINATOR, "/api/reports/" + s.reference + "/photos/" + photoId))).isEqualTo(200);
        assertThat(get("resident", "/api/reports/" + s.reference + "/photos/" + photoId).status).isEqualTo(403);

        assertThat(change(s, "ACCEPTED", null, true).status).isEqualTo(200);
        Answer after = get("resident", "/api/reports/map/" + id);
        assertThat(after.text("description")).isEqualTo("Rubbish bags next to the footpath");
        assertThat(after.text("address")).isEqualTo("Raja 15, Tallinn");
        assertThat(after.body.path("photos")).hasSize(1);
        assertThat(status(get("resident", "/api/reports/map/" + id + "/photos/" + photoId))).isEqualTo(200);
        assertThat(after.raw).doesNotContain(s.reference, "secret@example.com", "\"resident\"", "coordinator");
    }

    /** 29. The public map shows no personal data and drops closed reports. */
    @Test
    void thePublicMapShowsNoPersonalData() {
        Sent litter = send("LITTER");
        send("PUBLIC_ORDER");
        Answer map = map();
        assertThat(map.body).hasSize(1);
        assertThat(map.raw).doesNotContain(litter.reference, "Raja 15", "Rubbish", "secret@example.com");
        assertThat(map.body.get(0).path("latitude").decimalValue().scale()).isLessThanOrEqualTo(4);

        change(litter, "REJECTED", "Not found", false);
        assertThat(map().body.findValuesAsText("status")).containsExactly("REJECTED");
        age("4 days");
        assertThat(map().body).isEmpty();

        assertProblem(get("resident", "/api/reports/map?west=24&south=59&east=abc&north=60"), 400, "east");
        assertThat(get("resident", "/api/reports/map?west=1&south=1&east=2&north=2").body).isEmpty();
    }

    /** 30. The resident gets five steps, updates and a date to wait for. */
    @Test
    void theResidentGetsStepsUpdatesAndADate() {
        Sent s = send("LITTER");
        Answer fresh = get("resident", "/api/reports/" + s.reference);
        assertThat(fresh.text("daysToRespond")).isEqualTo("15");
        assertThat(fresh.body.has("respondBy")).isTrue();

        change(s, "ACCEPTED", null, false);
        assertThat(get("resident", "/api/reports/" + s.reference).body.has("daysToRespond")).as("answered").isFalse();

        ObjectNode update = json.createObjectNode().put("text", "The crew comes on Monday")
                .put("expectedOn", LocalDate.now().plusDays(5).toString());
        assertProblem(post("resident", "/api/reports/" + s.reference + "/updates", update, null), 403, "coordinator");
        assertThat(post(COORDINATOR, "/api/reports/" + s.reference + "/updates", update, null).status).isEqualTo(201);
        Answer messages = get("resident", "/api/reports/" + s.reference + "/messages");
        assertThat(messages.body.findValuesAsText("text")).containsExactly("The crew comes on Monday");
        assertThat(messages.body.findValuesAsText("expectedOn")).containsExactly(LocalDate.now().plusDays(5).toString());
        assertThat(messages.raw).doesNotContain(COORDINATOR);
        assertProblem(post(COORDINATOR, "/api/reports/" + s.reference + "/updates",
                json.createObjectNode().put("text", " "), null), 400, "text");
    }

    /** 31. The sender can remind. */
    @Test
    void theSenderCanRemind() {
        Sent s = send("LITTER");
        assertProblem(ping(new Sent(s.reference, "someone-else")), 404, "no report");
        assertProblem(ping(s), 409, "3 days");
        age("4 days");
        assertThat(ping(s).status).isEqualTo(201);
        assertProblem(ping(s), 409, "3 days");
        Answer list = get(COORDINATOR, "/api/reports?limit=10");
        assertThat(list.body.findValuesAsText("reminders")).containsExactly("1");
        assertThat(list.body.findValuesAsText("daysToRespond")).containsExactly("11");
        age("13 days");
        assertThat(get(COORDINATOR, "/api/reports/" + s.reference).text("daysToRespond")).isEqualTo("-2");

        change(s, "REJECTED", "No", false);
        assertProblem(ping(s), 409, "closed");
    }

    /** 32. The sender can say that the problem remains. */
    @Test
    void theSenderCanSayThatTheProblemRemains() throws IOException {
        Sent s = send("LITTER");
        assertProblem(remains(s, "Still there"), 409, "resolved");
        change(s, "ACCEPTED", null, true);
        change(s, "IN_PROGRESS", null, false);
        assertThat(upload(COORDINATOR, "/api/reports/" + s.reference + "/photos", jpegWithGps(10, 10), null).status)
                .as("the coordinator shows the result").isEqualTo(201);
        change(s, "RESOLVED", null, false);
        assertThat(get("resident", "/api/reports/map/" + mapId()).body.findValuesAsText("kind")).containsExactly("AFTER");

        assertProblem(remains(new Sent(s.reference, "other"), "Still there"), 404, "no report");
        assertProblem(remains(s, " "), 400, "text");
        assertThat(remains(s, "Half of the bags are still there").text("status")).isEqualTo("ACCEPTED");
        assertThat(get(COORDINATOR, "/api/reports/" + s.reference + "/history").body.findValuesAsText("toStatus"))
                .containsExactly("ACCEPTED", "IN_PROGRESS", "RESOLVED", "ACCEPTED");

        change(s, "IN_PROGRESS", null, false);
        change(s, "RESOLVED", null, false);
        age("31 days");
        assertProblem(remains(s, "Again"), 409, "30 days");
        assertProblem(change(s, "ACCEPTED", null, false), 409, "RESOLVED");
    }

    /** 33. Points are given for confirmed and for solved reports. */
    @Test
    void pointsAreGivenForConfirmedAndSolvedReports() throws IOException {
        String device = device();
        Sent s = send("LITTER", device);
        assertThat(me(device).text("points")).isEqualTo("0");
        photo("resident", s, jpegWithGps(10, 10));
        assertThat(me(device).text("points")).as("not confirmed yet").isEqualTo("0");
        change(s, "ACCEPTED", null, false);
        assertThat(me(device).text("points")).isEqualTo("15");
        change(s, "IN_PROGRESS", null, false);
        change(s, "RESOLVED", null, false);
        assertThat(me(device).text("points")).isEqualTo("35");
        assertThat(me(device).text("level")).isEqualTo("0");
        assertThat(me(device).text("nextLevelAt")).isEqualTo("50");
        remains(s, "Still there");
        assertThat(me(device).text("points")).as("solved points wait for the solution").isEqualTo("15");

        age("25 hours");
        Sent fake = send("LITTER", device);
        post(COORDINATOR, "/api/reports/" + fake.reference + "/status", json.createObjectNode()
                .put("toStatus", "REJECTED").put("reason", "Nothing there").put("falseReport", true), null);
        assertThat(me(device).text("points")).isEqualTo("0");
        assertProblem(get("resident", "/api/reports/me"), 400, "X-Device-Id");
    }

    /** 34. Others can say that they see it too. */
    @Test
    void othersCanSayThatTheySeeItToo() {
        String sender = device();
        send("LITTER", sender);
        String id = mapId();
        assertThat(map().body.get(0).path("seenBy").asInt()).isEqualTo(1);
        String other = device();
        assertThat(sight(id, other).text("seenBy")).isEqualTo("2");
        assertThat(sight(id, other).text("seenBy")).as("once per device").isEqualTo("2");
        assertThat(sight(id, sender).text("seenBy")).as("the sender is counted already").isEqualTo("2");
        assertThat(sight(id, device()).text("seenBy")).isEqualTo("3");
        assertThat(get(COORDINATOR, "/api/reports?limit=5").body.findValuesAsText("seenBy")).containsExactly("3");
        assertProblem(sight("999999", other), 404, "map");
        assertProblem(exchange("resident", request("/api/reports/map/" + id + "/sightings")
                .POST(HttpRequest.BodyPublishers.noBody())), 400, "X-Device-Id");
    }

    /** 35. The coordinator finds late reports and searches by text. */
    @Test
    void theCoordinatorFindsLateReportsAndSearches() {
        Sent late = send("LITTER");
        Sent answered = send("TREES");
        change(answered, "ACCEPTED", null, false);
        age("16 days");
        Sent fresh = send("ROAD_DAMAGE");
        assertThat(found("late=true")).containsExactly(late.reference);
        assertThat(found("late=false")).containsExactlyInAnyOrder(late.reference, answered.reference, fresh.reference);

        assertThat(found("q=" + fresh.reference.toLowerCase())).containsExactly(fresh.reference);
        assertThat(found("q=RAJA")).as("a part of the address").hasSize(3);
        assertThat(found("q=footpath&categoryCode=TREES")).as("a part of the text").containsExactly(answered.reference);
        assertThat(found("q=%25")).as("a percent sign is only a character").isEmpty();
        assertThat(get("resident", "/api/reports?q=raja").status).isEqualTo(403);
    }

    private java.util.List<String> found(String query) {
        Answer a = get(COORDINATOR, "/api/reports?limit=50&" + query);
        assertThat(a.status).as(a.raw).isEqualTo(200);
        return a.body.findValuesAsText("referenceNumber");
    }

    /** 36. The coordinator sees how far the sender can be trusted, not who it is. */
    @Test
    void theCoordinatorSeesTheTrustOfTheSender() {
        String device = device();
        Sent first = send("LITTER", device);
        change(first, "ACCEPTED", null, false);
        age("25 hours");
        Sent second = send("TREES", device);
        Answer seen = get(COORDINATOR, "/api/reports/" + second.reference);
        assertThat(seen.text("senderReports")).isEqualTo("2");
        assertThat(seen.text("senderPoints")).isEqualTo("10");
        assertThat(seen.text("senderFalseReports")).isEqualTo("0");
        assertThat(seen.raw).doesNotContain(device);
        post(COORDINATOR, "/api/reports/" + second.reference + "/status", json.createObjectNode()
                .put("toStatus", "REJECTED").put("reason", "Nothing there").put("falseReport", true), null);
        seen = get(COORDINATOR, "/api/reports/" + second.reference);
        assertThat(seen.text("senderPoints")).isEqualTo("0");
        assertThat(seen.text("senderFalseReports")).isEqualTo("1");

        assertThat(get("resident", "/api/reports/" + second.reference).body.has("senderReports")).isFalse();
        Answer guest = report("LITTER", null);
        assertThat(get(COORDINATOR, "/api/reports/" + guest.text("referenceNumber")).body.has("senderReports"))
                .as("a guest has no history").isFalse();
    }

    private Answer sight(String id, String device) {
        return exchange("resident", request("/api/reports/map/" + id + "/sightings").header("X-Device-Id", device)
                .POST(HttpRequest.BodyPublishers.noBody()));
    }

    // ---- helpers ---------------------------------------------------------

    private record Sent(String reference, String key) {}

    private record Answer(int status, String contentType, String raw, JsonNode body) {
        String text(String field) {
            return body.path(field).asText();
        }
    }

    private static int status(Answer a) {
        return a.status;
    }

    private static void assertProblem(Answer a, int status, String detailPart) {
        assertThat(a.status).as(a.raw).isEqualTo(status);
        assertThat(a.contentType).startsWith("application/problem+json");
        assertThat(a.text("detail")).contains(detailPart);
    }

    private static String device() {
        return UUID.randomUUID().toString();
    }

    private void age(String interval) {
        jdbc.execute("set session_replication_role = replica;"
                + "update reports.report set sys_created_at = sys_created_at - interval '" + interval + "';"
                + "update reports.status_change set sys_created_at = sys_created_at - interval '" + interval + "';"
                + "update reports.message set sys_created_at = sys_created_at - interval '" + interval + "';"
                + "set session_replication_role = origin");
    }

    private Sent send(String category) {
        return send(category, device());
    }

    private Sent send(String category, String device) {
        String key = UUID.randomUUID().toString();
        Answer a = post("resident", "/api/reports", body(category).put("submissionKey", key), device);
        assertThat(a.status).as(a.raw).isEqualTo(201);
        return new Sent(a.text("referenceNumber"), key);
    }

    private Answer report(String category, String device) {
        return post("resident", "/api/reports", body(category), device);
    }

    private ObjectNode body(String category) {
        return json.createObjectNode().put("categoryCode", category).put("address", "Raja 15, Tallinn")
                .put("latitude", 59.394673).put("longitude", 24.661627).put("reporterContact", "secret@example.com")
                .put("description", "Rubbish bags next to the footpath");
    }

    private String mapId() {
        return map().body.get(0).path("id").asText();
    }

    private Answer map() {
        return get("resident", "/api/reports/map?west=24&south=59&east=25&north=60");
    }

    private Answer me(String device) {
        return exchange("resident", request("/api/reports/me").header("X-Device-Id", device).GET());
    }

    private Answer ping(Sent s) {
        return exchange("resident", request("/api/reports/" + s.reference + "/reminders")
                .header("X-Submission-Key", s.key).POST(HttpRequest.BodyPublishers.noBody()));
    }

    private Answer remains(Sent s, String text) {
        return exchange("resident", request("/api/reports/" + s.reference + "/remains")
                .header("X-Submission-Key", s.key).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.createObjectNode().put("text", text).toString())));
    }

    private Answer change(Sent s, String to, String reason, boolean publish) {
        ObjectNode node = json.createObjectNode().put("toStatus", to).put("publish", publish);
        if (reason != null) {
            node.put("reason", reason);
        }
        return post(COORDINATOR, "/api/reports/" + s.reference + "/status", node, null);
    }

    private Answer photo(String user, Sent s, byte[] content) {
        return upload(user, "/api/reports/" + s.reference + "/photos", content, s.key);
    }

    private Answer upload(String user, String path, byte[] content, String key) {
        String boundary = "b" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(content);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        HttpRequest.Builder r = request(path).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
        return exchange(user, key == null ? r : r.header("X-Submission-Key", key));
    }

    /** A real JPEG with an APP1 segment that carries a marker, as a camera writes GPS. */
    private static byte[] jpegWithGps(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "jpg", out);
        byte[] jpeg = out.toByteArray();
        byte[] exif = "Exif\0\0SECRET-GPS-59.394673".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.write(jpeg, 0, 2);
        all.writeBytes(new byte[] {(byte) 0xff, (byte) 0xe1, 0, (byte) (exif.length + 2)});
        all.writeBytes(exif);
        all.write(jpeg, 2, jpeg.length - 2);
        return all.toByteArray();
    }

    private Answer get(String user, String path) {
        return exchange(user, request(path).GET());
    }

    private Answer post(String user, String path, JsonNode body, String device) {
        HttpRequest.Builder r = request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return exchange(user, device == null ? r : r.header("X-Device-Id", device));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path));
    }

    private Answer exchange(String user, HttpRequest.Builder request) {
        try {
            HttpResponse<String> r = http.send(request.header("Authorization", "Bearer " + user).build(),
                    HttpResponse.BodyHandlers.ofString());
            String type = r.headers().firstValue("Content-Type").orElse("");
            return new Answer(r.statusCode(), type, r.body(),
                    type.contains("json") && !r.body().isBlank() ? json.readTree(r.body()) : json.createObjectNode());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
