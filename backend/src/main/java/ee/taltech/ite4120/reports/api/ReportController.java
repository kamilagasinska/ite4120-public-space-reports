package ee.taltech.ite4120.reports.api;

import ee.taltech.ite4120.reports.dto.ReportQueryParams;
import ee.taltech.ite4120.reports.model.Category;
import ee.taltech.ite4120.reports.model.Forwarding;
import ee.taltech.ite4120.reports.model.Place;
import ee.taltech.ite4120.reports.model.Report;
import ee.taltech.ite4120.reports.model.StatusChange;
import ee.taltech.ite4120.reports.service.ReportService;
import ee.taltech.ite4120.reports.xroad.XRoadCheck;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.helex.commons.model.QueryResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The public space reports API. HTTP only, the rules are in ReportService. */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Public space reports, specification REPORTS.01")
public class ReportController {

    private final ReportService service;
    private final XRoadCheck xroadCheck;

    public ReportController(ReportService service, XRoadCheck xroadCheck) {
        this.xroadCheck = xroadCheck;
        this.service = service;
    }

    @Operation(summary = "List reports with days open and responsible unit (coordinator)")
    @ApiResponse(responseCode = "200", description = "One page of reports")
    @ApiResponse(responseCode = "400", description = "Unknown category, status or sort key")
    @ApiResponse(responseCode = "403", description = "Not a coordinator")
    @GetMapping
    public QueryResult<Report> search(@ModelAttribute ReportQueryParams params) {
        return service.search(params);
    }

    @Operation(summary = "One report by reference number",
            description = "The description, the contact and the sys fields are returned only to a coordinator.")
    @ApiResponse(responseCode = "200", description = "The report")
    @ApiResponse(responseCode = "404", description = "No report with that reference number")
    @GetMapping("/{referenceNumber}")
    public Report byReferenceNumber(@PathVariable String referenceNumber) {
        return service.find(referenceNumber);
    }

    @Operation(summary = "Submit a report")
    @ApiResponse(responseCode = "201", description = "Created, the Location header points at the report")
    @ApiResponse(responseCode = "200", description = "This submissionKey was used before, the existing report")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "429", description = "Over the limit, see Retry-After")
    @PostMapping
    public ResponseEntity<Report> submit(@Valid @RequestBody ReportRequest body,
            @Parameter(description = "Random UUID of the device, absent for a guest")
            @RequestHeader(name = "X-Device-Id", required = false) String deviceId, HttpServletRequest request) {
        /* The address of the connection. X-Forwarded-For can be written by anyone, so it is not used. */
        ReportService.Submission result = service.submit(body.toModel(), deviceId, request.getRemoteAddr());
        Report report = result.report();
        return result.created()
                ? ResponseEntity.created(URI.create("/api/reports/" + report.getReferenceNumber())).body(report)
                : ResponseEntity.ok(report);
    }

    @Operation(summary = "Change the status of a report (coordinator)")
    @ApiResponse(responseCode = "200", description = "The changed report")
    @ApiResponse(responseCode = "400", description = "Unknown status, or a rejection without a reason")
    @ApiResponse(responseCode = "403", description = "Not a coordinator")
    @ApiResponse(responseCode = "404", description = "No report with that reference number")
    @ApiResponse(responseCode = "409", description = "This status change is not allowed")
    @PostMapping("/{referenceNumber}/status")
    public Report changeStatus(@PathVariable String referenceNumber, @Valid @RequestBody StatusChangeRequest body) {
        return service.changeStatus(referenceNumber, body.getToStatus(), body.getReason(), body.isFalseReport(),
                body.isPublish());
    }

    @Operation(summary = "Status changes of a report, oldest first (coordinator)")
    @ApiResponse(responseCode = "200", description = "The status changes")
    @ApiResponse(responseCode = "403", description = "Not a coordinator")
    @ApiResponse(responseCode = "404", description = "No report with that reference number")
    @GetMapping("/{referenceNumber}/history")
    public List<StatusChange> history(@PathVariable String referenceNumber) {
        return service.history(referenceNumber);
    }

    @Operation(summary = "Forward a report to another recipient (coordinator)")
    @ApiResponse(responseCode = "200", description = "The forwarded report")
    @ApiResponse(responseCode = "400", description = "Unknown kind, or name or reason missing")
    @ApiResponse(responseCode = "403", description = "Not a coordinator")
    @ApiResponse(responseCode = "409", description = "Closed, or already with this recipient")
    @PostMapping("/{referenceNumber}/forward")
    public Report forward(@PathVariable String referenceNumber, @Valid @RequestBody ForwardRequest body) {
        return service.forward(referenceNumber, body.getKind(), body.getName(), body.getReason());
    }

    @Operation(summary = "Where a report was forwarded, oldest first")
    @GetMapping("/{referenceNumber}/forwardings")
    public List<Forwarding> forwardings(@PathVariable String referenceNumber) {
        return service.forwardings(referenceNumber);
    }

    @Operation(summary = "Live call to the X-Road test environment (coordinator)")
    @GetMapping("/xroad-status")
    public XRoadCheck.Status xroadStatus() {
        return xroadCheck.check();
    }

    @Operation(summary = "Municipality, nearest address and recipient of a map point")
    @ApiResponse(responseCode = "200", description = "The place")
    @ApiResponse(responseCode = "404", description = "No municipality at this point")
    @ApiResponse(responseCode = "502", description = "The address register is not available")
    @GetMapping("/place")
    public Place place(@RequestParam BigDecimal latitude, @RequestParam BigDecimal longitude,
            @RequestParam(required = false) String categoryCode) {
        return service.place(latitude, longitude, categoryCode);
    }

    @Operation(summary = "Category reference list with responsible units")
    @GetMapping("/categories")
    public List<Category> categories() {
        return service.categories();
    }
}
