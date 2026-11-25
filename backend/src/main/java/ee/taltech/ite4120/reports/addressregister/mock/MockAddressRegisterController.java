package ee.taltech.ite4120.reports.addressregister.mock;

import ee.taltech.ite4120.reports.addressregister.http.wire.AdsGazetteerResponse;
import ee.taltech.ite4120.reports.addressregister.wire.AdsAadress;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The imitated address register with made-up identifiers, local profile only. Every
 * part of the asked address must be a part of the known one. An address containing
 * "register-down" answers 503, so the failure policy can be shown live.
 */
@RestController
@RequestMapping("/mock-registry/ads")
@ConditionalOnProperty(name = "reports.address-register.mock-server", havingValue = "true")
public class MockAddressRegisterController {

    private static final List<AdsAadress> KNOWN = List.of(
            new AdsAadress("ME00000001", "Harju maakond, Tallinn, Mustamäe linnaosa, Ehitajate tee 5"),
            new AdsAadress("ME00000002", "Harju maakond, Tallinn, Mustamäe linnaosa, Raja 15"),
            new AdsAadress("ME00000003", "Tartu maakond, Tartu, Raekoja plats 1"));

    @GetMapping("/gazetteer")
    public ResponseEntity<AdsGazetteerResponse> gazetteer(@RequestParam String address) {
        if (address.toLowerCase().contains("register-down")) {
            return ResponseEntity.status(503).build();
        }
        Set<String> asked = parts(address);
        return ResponseEntity.ok(new AdsGazetteerResponse(KNOWN.stream()
                .filter(a -> !asked.isEmpty() && parts(a.taisaadress()).containsAll(asked))
                .toList()));
    }

    private static Set<String> parts(String address) {
        return Arrays.stream(address.toLowerCase().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }
}
