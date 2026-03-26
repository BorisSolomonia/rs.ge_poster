package ge.camora.erp.model.dto;

public class CashFlowCategoryMappingRequest {
    private String sourceCategory;
    private String targetCategory;

    public String getSourceCategory() {
        return sourceCategory;
    }

    public void setSourceCategory(String sourceCategory) {
        this.sourceCategory = sourceCategory;
    }

    public String getTargetCategory() {
        return targetCategory;
    }

    public void setTargetCategory(String targetCategory) {
        this.targetCategory = targetCategory;
    }
}
