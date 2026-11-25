package ee.taltech.ite4120.reports.dto;

import org.helex.commons.model.QueryParams;

/** Filters of the report list, on top of limit, offset and sort. */
public class ReportQueryParams extends QueryParams {

    private String municipalityCode;
    private String channel;
    private String categoryCode;
    private String status;

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMunicipalityCode() { return municipalityCode; }
    public void setMunicipalityCode(String municipalityCode) { this.municipalityCode = municipalityCode; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
}
