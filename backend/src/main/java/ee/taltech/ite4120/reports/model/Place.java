package ee.taltech.ite4120.reports.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** What is known about a map point. The recipient only when a category was given. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Place(String fullAddress, String municipalityCode, String municipalityName,
        Recipient.Kind recipientKind, String recipientName) {}
