package ge.camora.erp.module.cashflow;

import java.time.LocalDateTime;

/**
 * A global rule mapping a bank counterparty identifier to a cash-flow category.
 * The {@code id} is deterministic ({@code matchType:matchValue[:direction]}) so a
 * cascade ("Apply to All") upsert is idempotent and never creates duplicates.
 * {@code direction} is optional: a counterparty that both sends and receives can
 * be split by direction, otherwise a direction-agnostic rule applies to both.
 */
public class TransactionCategoryRule {
    private String id;
    private CashFlowMatchType matchType;
    private String matchValue;
    private CashFlowDirection direction;
    private String categoryId;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TransactionCategoryRule() {
    }

    public TransactionCategoryRule(String id, CashFlowMatchType matchType, String matchValue,
                                   CashFlowDirection direction, String categoryId, String source,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.matchType = matchType;
        this.matchValue = matchValue;
        this.direction = direction;
        this.categoryId = categoryId;
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

    public CashFlowMatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(CashFlowMatchType matchType) {
        this.matchType = matchType;
    }

    public String getMatchValue() {
        return matchValue;
    }

    public void setMatchValue(String matchValue) {
        this.matchValue = matchValue;
    }

    public CashFlowDirection getDirection() {
        return direction;
    }

    public void setDirection(CashFlowDirection direction) {
        this.direction = direction;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
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
