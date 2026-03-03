package ge.camora.erp.model.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateProductMappingRequest {
    @NotBlank
    private String supplierMappingId;
    @NotBlank
    private String rsgeProductPattern;
    @NotBlank
    private String posterProductPattern;
    private boolean isRegex;
    private boolean isExcluded;
    private int priority;

    public String getSupplierMappingId() { return supplierMappingId; }
    public void setSupplierMappingId(String supplierMappingId) { this.supplierMappingId = supplierMappingId; }
    public String getRsgeProductPattern() { return rsgeProductPattern; }
    public void setRsgeProductPattern(String rsgeProductPattern) { this.rsgeProductPattern = rsgeProductPattern; }
    public String getPosterProductPattern() { return posterProductPattern; }
    public void setPosterProductPattern(String posterProductPattern) { this.posterProductPattern = posterProductPattern; }
    public boolean isRegex() { return isRegex; }
    public void setRegex(boolean regex) { isRegex = regex; }
    public boolean isExcluded() { return isExcluded; }
    public void setExcluded(boolean excluded) { isExcluded = excluded; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
