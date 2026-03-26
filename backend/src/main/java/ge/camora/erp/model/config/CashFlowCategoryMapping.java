package ge.camora.erp.model.config;

import java.time.LocalDateTime;

public class CashFlowCategoryMapping {
    private String sourceCategory;
    private String normalizedSourceCategory;
    private String targetCategory;
    private String normalizedTargetCategory;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CashFlowCategoryMapping() {
    }

    public CashFlowCategoryMapping(
        String sourceCategory,
        String normalizedSourceCategory,
        String targetCategory,
        String normalizedTargetCategory,
        String source,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        this.sourceCategory = sourceCategory;
        this.normalizedSourceCategory = normalizedSourceCategory;
        this.targetCategory = targetCategory;
        this.normalizedTargetCategory = normalizedTargetCategory;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getSourceCategory() {
        return sourceCategory;
    }

    public void setSourceCategory(String sourceCategory) {
        this.sourceCategory = sourceCategory;
    }

    public String getNormalizedSourceCategory() {
        return normalizedSourceCategory;
    }

    public void setNormalizedSourceCategory(String normalizedSourceCategory) {
        this.normalizedSourceCategory = normalizedSourceCategory;
    }

    public String getTargetCategory() {
        return targetCategory;
    }

    public void setTargetCategory(String targetCategory) {
        this.targetCategory = targetCategory;
    }

    public String getNormalizedTargetCategory() {
        return normalizedTargetCategory;
    }

    public void setNormalizedTargetCategory(String normalizedTargetCategory) {
        this.normalizedTargetCategory = normalizedTargetCategory;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
