package ge.camora.erp.model.dto;

public record CashFlowRuleRequest(
    String matchType,
    String matchValue,
    String direction,
    String categoryId
) {
}
