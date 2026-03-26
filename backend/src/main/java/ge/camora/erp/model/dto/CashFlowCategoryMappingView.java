package ge.camora.erp.model.dto;

public record CashFlowCategoryMappingView(
    String sourceCategory,
    String targetCategory,
    String source
) {
}
