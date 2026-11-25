package ee.taltech.ite4120.reports.addressregister.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.taltech.ite4120.reports.addressregister.AddressInfo;
import ee.taltech.ite4120.reports.addressregister.AddressMapper;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterAdapter;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterException;
import ee.taltech.ite4120.reports.addressregister.http.wire.AdsGazetteerResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Plain HTTP and JSON. Locally the URL points at this application's own imitated register. */
public class HttpAddressRegisterAdapter implements AddressRegisterAdapter {

    /* Short timeouts, because a resident is waiting for the form to answer. */
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;

    public HttpAddressRegisterAdapter(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public AddressInfo lookup(String address) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/gazetteer?address=" + URLEncoder.encode(address, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(3))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AddressRegisterException("address register answered HTTP " + response.statusCode());
            }
            AdsGazetteerResponse wire = json.readValue(response.body(), AdsGazetteerResponse.class);
            return AddressMapper.toAddressInfo(wire == null ? null : wire.addresses());
        } catch (IOException e) {
            throw new AddressRegisterException("address register unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AddressRegisterException("address register call interrupted", e);
        }
    }
}
