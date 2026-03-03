package ge.camora.erp.model.dto;

public class UpdateSupplierMappingRequest {
    private String posterAlias;
    private String rsgeRawValue;
    private Boolean posterExcluded;
    private Boolean rsgeExcluded;

    public String getPosterAlias() { return posterAlias; }
    public void setPosterAlias(String posterAlias) { this.posterAlias = posterAlias; }
    public String getRsgeRawValue() { return rsgeRawValue; }
    public void setRsgeRawValue(String rsgeRawValue) { this.rsgeRawValue = rsgeRawValue; }
    public Boolean getPosterExcluded() { return posterExcluded; }
    public void setPosterExcluded(Boolean posterExcluded) { this.posterExcluded = posterExcluded; }
    public Boolean getRsgeExcluded() { return rsgeExcluded; }
    public void setRsgeExcluded(Boolean rsgeExcluded) { this.rsgeExcluded = rsgeExcluded; }
}
