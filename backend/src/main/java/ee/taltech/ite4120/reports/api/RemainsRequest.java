package ee.taltech.ite4120.reports.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why the problem is not solved. */
public record RemainsRequest(@NotBlank @Size(max = 500) String text) {}
