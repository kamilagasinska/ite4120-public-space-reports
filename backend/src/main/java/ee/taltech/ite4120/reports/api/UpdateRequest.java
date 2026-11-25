package ee.taltech.ite4120.reports.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** News for the resident, with the day to expect something if it is known. */
public record UpdateRequest(@NotBlank @Size(max = 500) String text, LocalDate expectedOn) {}
