package ee.taltech.ite4120.reports.roadmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.taltech.ite4120.reports.addressregister.LEst97;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Roads of the Estonian topographic database ETAK, layer e_501_tee_j, over the public WFS. */
@Component
public class EtakRoadMapAdapter implements RoadMapAdapter {

    private static final double REACH = 30;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String url;

    public EtakRoadMapAdapter(@Value("${reports.road-map.url:}") String url) {
        this.url = url;
    }

    @Override
    public RoadOwner owner(double latitude, double longitude) {
        if (url.isBlank()) {
            throw new RoadMapException("reports.road-map.url is not set", null);
        }
        double[] p = LEst97.fromDegrees(latitude, longitude);
        String box = String.format(Locale.ROOT, "%.1f,%.1f,%.1f,%.1f",
                p[0] - REACH, p[1] - REACH, p[0] + REACH, p[1] + REACH);
        String query = url + "?service=WFS&version=2.0.0&request=GetFeature&typeNames=etak:e_501_tee_j"
                + "&outputFormat=application/json&srsName=EPSG:3301&count=200&bbox=" + box + ",EPSG:3301";
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(query))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new RoadMapException("road map answered HTTP " + r.statusCode(), null);
            }
            return nearest(json.readTree(r.body()), p[0], p[1]);
        } catch (IOException e) {
            throw new RoadMapException("road map unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RoadMapException("road map call interrupted", e);
        }
    }

    private static RoadOwner nearest(JsonNode answer, double x, double y) {
        RoadOwner owner = RoadOwner.NO_ROAD;
        double best = REACH;
        for (JsonNode road : answer.path("features")) {
            RoadOwner of = ofType(road.path("properties"));
            double d = distance(road.path("geometry"), x, y);
            if (of != RoadOwner.NO_ROAD && d <= best) {
                best = d;
                owner = of;
            }
        }
        return owner;
    }

    /** ETAK "tyyp". Paths (70) and cycle paths (80) are not roads for cars and are left out. */
    private static RoadOwner ofType(JsonNode properties) {
        int number = properties.path("tee").asInt(0);
        return switch (properties.path("tyyp").asInt()) {
            case 10, 20, 30 -> RoadOwner.STATE;
            /* A ramp belongs to the road it serves. State roads have at most five digits. */
            case 40 -> number > 0 && number < 100_000 ? RoadOwner.STATE : RoadOwner.UNCLEAR;
            case 50 -> RoadOwner.MUNICIPALITY;
            case 60 -> RoadOwner.UNCLEAR;
            default -> RoadOwner.NO_ROAD;
        };
    }

    private static double distance(JsonNode geometry, double x, double y) {
        double best = Double.MAX_VALUE;
        boolean multi = geometry.path("type").asText().startsWith("Multi");
        for (JsonNode line : multi ? geometry.path("coordinates") : List.of(geometry.path("coordinates"))) {
            for (int i = 1; i < line.size(); i++) {
                best = Math.min(best, toSegment(x, y, line.get(i - 1), line.get(i)));
            }
        }
        return best;
    }

    private static double toSegment(double x, double y, JsonNode a, JsonNode b) {
        double ax = a.get(0).asDouble();
        double ay = a.get(1).asDouble();
        double dx = b.get(0).asDouble() - ax;
        double dy = b.get(1).asDouble() - ay;
        double length = dx * dx + dy * dy;
        double t = length == 0 ? 0 : Math.clamp(((x - ax) * dx + (y - ay) * dy) / length, 0, 1);
        return Math.hypot(x - ax - t * dx, y - ay - t * dy);
    }
}
