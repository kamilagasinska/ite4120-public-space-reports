package ee.taltech.ite4120.reports.api;

import ee.taltech.ite4120.reports.dto.ReportQueryParams;
import ee.taltech.ite4120.reports.model.Category;
import ee.taltech.ite4120.reports.model.Report;
import ee.taltech.ite4120.reports.model.StatusChange;
import ee.taltech.ite4120.reports.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.helex.commons.model.QueryResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The public space reports API. HTTP only, the rules are in ReportService. */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Public space reports, specification REPORTS.01")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
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
    @PostMapping
    public ResponseEntity<Report> submit(@Valid @RequestBody ReportRequest body) {
        ReportService.Submission result = service.submit(body.toModel());
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
        return service.changeStatus(referenceNumber, body.getToStatus(), body.getReason());
    }

    @Operation(summary = "Status changes of a report, oldest first (coordinator)")
    @ApiResponse(responseCode = "200", description = "The status changes")
    @ApiResponse(responseCode = "403", description = "Not a coordinator")
    @ApiResponse(responseCode = "404", description = "No report with that reference number")
    @GetMapping("/{referenceNumber}/history")
    public List<StatusChange> history(@PathVariable String referenceNumber) {
        return service.history(referenceNumber);
    }

    @Operation(summary = "Category reference list with responsible units")
    @GetMapping("/categories")
    public List<Category> categories() {
        return service.categories();
    }
}
