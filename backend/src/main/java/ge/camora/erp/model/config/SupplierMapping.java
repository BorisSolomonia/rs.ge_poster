package ge.camora.erp.model.config;

import java.time.LocalDateTime;

public class SupplierMapping {
    private String id;
    private String posterAlias;
    private String rsgeRawValue;
    private String rsgeTaxId;
    private String rsgeOfficialName;
    private boolean posterExcluded;
    private boolean rsgeExcluded;
    private LocalDateTime createdAt;

    public SupplierMapping() {}

    public SupplierMapping(String id, String posterAlias, String rsgeRawValue,
                           String rsgeTaxId, String rsgeOfficialName,
                           boolean posterExcluded, boolean rsgeExcluded, LocalDateTime createdAt) {
        this.id = id;
        this.posterAlias = posterAlias;
        this.rsgeRawValue = rsgeRawValue;
        this.rsgeTaxId = rsgeTaxId;
        this.rsgeOfficialName = rsgeOfficialName;
        this.posterExcluded = posterExcluded;
        this.rsgeExcluded = rsgeExcluded;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPosterAlias() { return posterAlias; }
    public void setPosterAlias(String posterAlias) { this.posterAlias = posterAlias; }
    public String getRsgeRawValue() { return rsgeRawValue; }
    public void setRsgeRawValue(String rsgeRawValue) { this.rsgeRawValue = rsgeRawValue; }
    public String getRsgeTaxId() { return rsgeTaxId; }
    public void setRsgeTaxId(String rsgeTaxId) { this.rsgeTaxId = rsgeTaxId; }
    public String getRsgeOfficialName() { return rsgeOfficialName; }
    public void setRsgeOfficialName(String rsgeOfficialName) { this.rsgeOfficialName = rsgeOfficialName; }
    public boolean isPosterExcluded() { return posterExcluded; }
    public void setPosterExcluded(boolean posterExcluded) { this.posterExcluded = posterExcluded; }
    public boolean isRsgeExcluded() { return rsgeExcluded; }
    public void setRsgeExcluded(boolean rsgeExcluded) { this.rsgeExcluded = rsgeExcluded; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
