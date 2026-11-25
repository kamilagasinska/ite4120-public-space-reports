package ee.taltech.ite4120.reports.api;

import ee.taltech.ite4120.reports.service.SigningService;
import ee.taltech.ite4120.reports.smartid.TestPersons;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Signing a report with the Smart-ID demo. */
@RestController
@RequestMapping("/api/reports/signings")
@Tag(name = "Reports", description = "Public space reports, specification REPORTS.01")
public class SigningController {

    private final SigningService service;

    public SigningController(SigningService service) {
        this.service = service;
    }

    @Operation(summary = "The Smart-ID demo persons")
    @GetMapping("/persons")
    public List<TestPersons.TestPerson> persons() {
        return TestPersons.ALL;
    }

    @Operation(summary = "Start signing a report")
    @ApiResponse(responseCode = "201", description = "Started, show the verification code and ask for the state")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "429", description = "Over the limit")
    @ApiResponse(responseCode = "502", description = "Smart-ID is not available")
    @PostMapping
    public ResponseEntity<SigningService.Started> start(@Valid @RequestBody SigningRequest body) {
        SigningService.Started started = service.start(body.toModel(), body.getPerson());
        return ResponseEntity.created(URI.create("/api/reports/signings/" + started.token())).body(started);
    }

    @Operation(summary = "State of a signing, with the reference number when signed")
    @ApiResponse(responseCode = "200", description = "PENDING, SIGNED, REFUSED, EXPIRED or FAILED")
    @ApiResponse(responseCode = "404", description = "No such signing")
    @GetMapping("/{token}")
    public SigningService.State state(@PathVariable String token) {
        return service.state(token);
    }
}
