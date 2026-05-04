package ge.camora.erp.model.config;

import java.time.LocalDateTime;

public class BankTransactionMapping {
    private String id;
    private String direction;
    private String matchText;
    private String normalizedMatchText;
    private String category;
    private String normalizedCategory;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BankTransactionMapping() {
    }

    public BankTransactionMapping(
        String id,
        String direction,
        String matchText,
        String normalizedMatchText,
        String category,
        String normalizedCategory,
        String source,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        this.id = id;
        this.direction = direction;
        this.matchText = matchText;
        this.normalizedMatchText = normalizedMatchText;
        this.category = category;
        this.normalizedCategory = normalizedCategory;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getMatchText() {
        return matchText;
    }

    public void setMatchText(String matchText) {
        this.matchText = matchText;
    }

    public String getNormalizedMatchText() {
        return normalizedMatchText;
    }

    public void setNormalizedMatchText(String normalizedMatchText) {
        this.normalizedMatchText = normalizedMatchText;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getNormalizedCategory() {
        return normalizedCategory;
    }

    public void setNormalizedCategory(String normalizedCategory) {
        this.normalizedCategory = normalizedCategory;
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
