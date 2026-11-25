package ee.taltech.ite4120.reports.api;

import ee.taltech.ite4120.reports.model.MapDetail;
import ee.taltech.ite4120.reports.model.MapPoint;
import ee.taltech.ite4120.reports.model.Message;
import ee.taltech.ite4120.reports.model.PhotoInfo;
import ee.taltech.ite4120.reports.model.Report;
import ee.taltech.ite4120.reports.model.Standing;
import ee.taltech.ite4120.reports.service.DialogueService;
import ee.taltech.ite4120.reports.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Photos, updates, reminders, points and the public map. X-Submission-Key proves the sender. */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Public space reports, specification REPORTS.01")
public class DialogueController {

    private static final String KEY = "X-Submission-Key";

    private final DialogueService service;
    private final ReportService reports;

    public DialogueController(DialogueService service, ReportService reports) {
        this.service = service;
        this.reports = reports;
    }

    @Operation(summary = "Add a photo, of the problem (sender) or of the result (coordinator)")
    @ApiResponse(responseCode = "201", description = "Stored without the data of the file")
    @ApiResponse(responseCode = "400", description = "Not a picture, too large, or the sixth")
    @ApiResponse(responseCode = "404", description = "Not a report of this sender")
    @PostMapping(path = "/{referenceNumber}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoInfo addPhoto(@PathVariable String referenceNumber,
            @RequestHeader(name = KEY, required = false) String key, @RequestParam MultipartFile file) throws IOException {
        return service.addPhoto(referenceNumber, key, file.getBytes());
    }

    @Operation(summary = "Photos of a report (coordinator or sender)")
    @GetMapping("/{referenceNumber}/photos")
    public List<PhotoInfo> photos(@PathVariable String referenceNumber,
            @RequestHeader(name = KEY, required = false) String key) {
        return service.photos(referenceNumber, key);
    }

    @Operation(summary = "One photo (coordinator or sender)")
    @GetMapping(path = "/{referenceNumber}/photos/{id}", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] photo(@PathVariable String referenceNumber, @PathVariable Long id,
            @RequestHeader(name = KEY, required = false) String key) {
        return service.photo(referenceNumber, key, id);
    }

    @Operation(summary = "News for the resident (coordinator)")
    @ApiResponse(responseCode = "201", description = "Stored")
    @ApiResponse(responseCode = "403", description = "Not a coordinator")
    @PostMapping("/{referenceNumber}/updates")
    @ResponseStatus(HttpStatus.CREATED)
    public void update(@PathVariable String referenceNumber, @Valid @RequestBody UpdateRequest body) {
        service.update(referenceNumber, body.text(), body.expectedOn());
    }

    @Operation(summary = "Updates, reminders and reopenings, oldest first")
    @GetMapping("/{referenceNumber}/messages")
    public List<Message> messages(@PathVariable String referenceNumber) {
        return service.messages(referenceNumber);
    }

    @Operation(summary = "Remind of a report (sender), once in 3 days")
    @ApiResponse(responseCode = "201", description = "Reminded")
    @ApiResponse(responseCode = "409", description = "Too early, or the report is closed")
    @PostMapping("/{referenceNumber}/reminders")
    @ResponseStatus(HttpStatus.CREATED)
    public void remind(@PathVariable String referenceNumber, @RequestHeader(name = KEY, required = false) String key) {
        service.remind(referenceNumber, key);
    }

    @Operation(summary = "The problem remains, reopen a resolved report (sender)")
    @ApiResponse(responseCode = "200", description = "Reopened as Accepted")
    @ApiResponse(responseCode = "409", description = "Not resolved, or resolved more than 30 days ago")
    @PostMapping("/{referenceNumber}/remains")
    public Report remains(@PathVariable String referenceNumber, @RequestHeader(name = KEY, required = false) String key,
            @Valid @RequestBody RemainsRequest body) {
        return service.remains(referenceNumber, key, body.text());
    }

    @Operation(summary = "Points and trust level of a device")
    @GetMapping("/me")
    public Standing me(@RequestHeader(name = "X-Device-Id", required = false) String deviceId) {
        return reports.standing(deviceId);
    }

    @Operation(summary = "Public map, at most 500 points")
    @GetMapping("/map")
    public List<MapPoint> map(@RequestParam BigDecimal west, @RequestParam BigDecimal south,
            @RequestParam BigDecimal east, @RequestParam BigDecimal north,
            @RequestParam(required = false) String categoryCode) {
        return service.map(west, south, east, north, categoryCode);
    }

    @Operation(summary = "One report of the public map, with text and photos once published")
    @GetMapping("/map/{id}")
    public MapDetail mapDetail(@PathVariable Long id) {
        return service.mapDetail(id);
    }

    @Operation(summary = "I see it too, counted once per device")
    @PostMapping("/map/{id}/sightings")
    public MapPoint sight(@PathVariable Long id, @RequestHeader(name = "X-Device-Id", required = false) String deviceId) {
        return service.sight(id, reports.deviceHash(deviceId));
    }

    @Operation(summary = "A published photo")
    @GetMapping(path = "/map/{id}/photos/{photoId}", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] mapPhoto(@PathVariable Long id, @PathVariable Long photoId) {
        return service.mapPhoto(id, photoId);
    }
}
