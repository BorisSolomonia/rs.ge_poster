package ge.camora.erp.model.dto;

public record CashFlowCategoryRequest(
    String section,
    String direction,
    String nameKa,
    String parentId,
    Integer order
) {
}
