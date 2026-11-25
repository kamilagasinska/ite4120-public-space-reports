package ee.taltech.ite4120.reports.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A report on the public map, without any text. */
public record MapPoint(Long id, BigDecimal latitude, BigDecimal longitude, String categoryCode, String status,
        String municipalityName, LocalDate submittedOn) {}
