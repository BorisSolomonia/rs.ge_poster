package ge.camora.erp.model.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateSupplierMappingRequest {
    @NotBlank
    private String posterAlias;
    @NotBlank
    private String rsgeRawValue;

    public String getPosterAlias() { return posterAlias; }
    public void setPosterAlias(String posterAlias) { this.posterAlias = posterAlias; }
    public String getRsgeRawValue() { return rsgeRawValue; }
    public void setRsgeRawValue(String rsgeRawValue) { this.rsgeRawValue = rsgeRawValue; }
}
