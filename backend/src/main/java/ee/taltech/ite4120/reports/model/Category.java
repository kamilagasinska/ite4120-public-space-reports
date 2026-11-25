package ee.taltech.ite4120.reports.model;

/** A report category and the municipal unit responsible for it. */
public class Category {

    private Boolean onMap;
    private String code;
    private String name;
    private String responsibleUnit;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getResponsibleUnit() { return responsibleUnit; }
    public void setResponsibleUnit(String responsibleUnit) { this.responsibleUnit = responsibleUnit; }
    public Boolean getOnMap() { return onMap; }
    public void setOnMap(Boolean onMap) { this.onMap = onMap; }
}
