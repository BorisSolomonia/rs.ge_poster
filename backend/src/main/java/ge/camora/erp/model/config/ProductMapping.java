package ge.camora.erp.model.config;

import java.time.LocalDateTime;

public class ProductMapping {
    private String id;
    private String supplierMappingId;
    private String rsgeProductPattern;
    private String posterProductPattern;
    private boolean isRegex;
    private boolean isExcluded;
    private int priority;
    private LocalDateTime createdAt;

    public ProductMapping() {}

    public ProductMapping(String id, String supplierMappingId, String rsgeProductPattern,
                          String posterProductPattern, boolean isRegex, boolean isExcluded,
                          int priority, LocalDateTime createdAt) {
        this.id = id;
        this.supplierMappingId = supplierMappingId;
        this.rsgeProductPattern = rsgeProductPattern;
        this.posterProductPattern = posterProductPattern;
        this.isRegex = isRegex;
        this.isExcluded = isExcluded;
        this.priority = priority;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
