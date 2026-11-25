package ee.taltech.ite4120.reports.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/** One report of the public map. Address, description and photos only after publication. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapDetail(@JsonUnwrapped MapPoint point, String recipientName, String address, String description, List<PhotoInfo> photos) {}
