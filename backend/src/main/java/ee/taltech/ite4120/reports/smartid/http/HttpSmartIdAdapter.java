package ee.taltech.ite4120.reports.smartid.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.taltech.ite4120.reports.smartid.SmartIdAdapter;
import ee.taltech.ite4120.reports.smartid.SmartIdException;
import ee.taltech.ite4120.reports.smartid.SignatureCheck;
import ee.taltech.ite4120.reports.smartid.http.wire.SmartIdSessionStarted;
import ee.taltech.ite4120.reports.smartid.http.wire.SmartIdSessionStatus;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Smart-ID relying party API v3, notification based signing in the public demo environment. */
@Component
public class HttpSmartIdAdapter implements SmartIdAdapter {

    private static final Logger log = LoggerFactory.getLogger(HttpSmartIdAdapter.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String url;
    private final SignatureCheck check;

    public HttpSmartIdAdapter(
            @Value("${reports.smart-id.url:https://sid.demo.sk.ee/smart-id-rp/v3}") String url,
            @Value("${reports.smart-id.trusted-issuer:TEST of SK ID Solutions EID-Q 2024E}") String trustedIssuer) {
        this.url = url;
        this.check = new SignatureCheck(trustedIssuer);
    }

    @Override
    public Session start(String document, byte[] text) {
        try {
            String interactions = Base64.getEncoder().encodeToString(json.writeValueAsBytes(List.of(
                    Map.of("type", "displayTextAndPIN", "displayText60", "Sign a public space report"))));
            String body = json.writeValueAsString(Map.of(
                    "relyingPartyUUID", "00000000-0000-4000-8000-000000000000",
                    "relyingPartyName", "DEMO",
                    "certificateLevel", "QUALIFIED",
                    "signatureProtocol", "RAW_DIGEST_SIGNATURE",
                    "signatureProtocolParameters", Map.of("digest", digest(text), "signatureAlgorithm", "rsassa-pss",
                            "signatureAlgorithmParameters", Map.of("hashAlgorithm", "SHA-512")),
                    "interactions", interactions));
            SmartIdSessionStarted wire = json.readValue(send(request("/signature/notification/document/" + document)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))), SmartIdSessionStarted.class);
            if (wire == null || wire.sessionID() == null || wire.vc() == null) {
                throw new IOException("no session in the answer");
            }
            return new Session(wire.sessionID(), wire.vc().value());
        } catch (IOException e) {
            throw new SmartIdException("Smart-ID is not available, send the report without signing or try later", e);
        }
    }

    @Override
    public Poll poll(String sessionId, String document, byte[] text) {
        SmartIdSessionStatus wire;
        try {
            wire = json.readValue(send(request("/session/" + sessionId + "?timeoutMs=1000").GET()),
                    SmartIdSessionStatus.class);
        } catch (IOException e) {
            throw new SmartIdException("Smart-ID is not available, try again", e);
        }
        if (wire == null || !"COMPLETE".equals(wire.state())) {
            return new Poll(Result.RUNNING, null);
        }
        String end = wire.result() == null ? "" : String.valueOf(wire.result().endResult());
        if (end.startsWith("USER_REFUSED")) {
            return new Poll(Result.REFUSED, null);
        }
        if (end.equals("TIMEOUT")) {
            return new Poll(Result.EXPIRED, null);
        }
        if (!end.equals("OK")) {
            return new Poll(Result.FAILED, null);
        }
        String problem = check.problem(wire, document, text);
        if (problem != null) {
            log.warn("Smart-ID signature refused: {}", problem);
            return new Poll(Result.FAILED, null);
        }
        return new Poll(Result.SIGNED, check.name(wire));
    }

    @Override
    public String digest(byte[] text) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(text));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(url + path)).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
    }

    private String send(HttpRequest.Builder request) throws IOException {
        try {
            HttpResponse<String> r = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IOException("HTTP " + r.statusCode());
            }
            return r.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
