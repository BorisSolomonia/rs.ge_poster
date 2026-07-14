package ge.camora.erp.module.cashflow;

import java.time.LocalDateTime;

/**
 * A leaf cash-flow category in the section -> direction -> category tree.
 * {@code builtin} categories are seeded defaults that cannot be deleted (the
 * two UNCATEGORIZED sentinels rely on this). For builtin categories the
 * {@code id} equals the {@code code} so rules referencing them survive restarts
 * and re-seeds; user-created categories get a random UUID id.
 */
public class CashFlowCategory {
    private String id;
    private String code;
    private CashFlowSection section;
    private CashFlowDirection direction;
    private String nameKa;
    /** Null for a top-level category; otherwise the id of the parent it is a sub-category of. */
    private String parentId;
    private int order;
    private boolean builtin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CashFlowCategory() {
    }

    public CashFlowCategory(String id, String code, CashFlowSection section, CashFlowDirection direction,
                            String nameKa, String parentId, int order, boolean builtin,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.code = code;
        this.section = section;
        this.direction = direction;
        this.nameKa = nameKa;
        this.parentId = parentId;
        this.order = order;
        this.builtin = builtin;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public CashFlowSection getSection() {
        return section;
    }

    public void setSection(CashFlowSection section) {
        this.section = section;
    }

    public CashFlowDirection getDirection() {
        return direction;
    }

    public void setDirection(CashFlowDirection direction) {
        this.direction = direction;
    }

    public String getNameKa() {
        return nameKa;
    }

    public void setNameKa(String nameKa) {
        this.nameKa = nameKa;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public void setBuiltin(boolean builtin) {
        this.builtin = builtin;
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
