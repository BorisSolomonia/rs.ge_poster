package ge.camora.erp.model.config;

import java.time.LocalDateTime;

public class SupplierPaymentMapping {
    private String id;
    private String provider;
    private String matchText;
    private String normalizedMatchText;
    private String supplierKey;
    private String supplierTin;
    private String supplierName;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SupplierPaymentMapping() {
    }

    public SupplierPaymentMapping(
        String id,
        String provider,
        String matchText,
        String normalizedMatchText,
        String supplierKey,
        String supplierTin,
        String supplierName,
        String source,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        this.id = id;
        this.provider = provider;
        this.matchText = matchText;
        this.normalizedMatchText = normalizedMatchText;
        this.supplierKey = supplierKey;
        this.supplierTin = supplierTin;
        this.supplierName = supplierName;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getMatchText() { return matchText; }
    public void setMatchText(String matchText) { this.matchText = matchText; }
    public String getNormalizedMatchText() { return normalizedMatchText; }
    public void setNormalizedMatchText(String normalizedMatchText) { this.normalizedMatchText = normalizedMatchText; }
    public String getSupplierKey() { return supplierKey; }
    public void setSupplierKey(String supplierKey) { this.supplierKey = supplierKey; }
    public String getSupplierTin() { return supplierTin; }
    public void setSupplierTin(String supplierTin) { this.supplierTin = supplierTin; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
