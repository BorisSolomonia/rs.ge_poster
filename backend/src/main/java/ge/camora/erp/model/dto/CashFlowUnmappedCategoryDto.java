package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record CashFlowUnmappedCategoryDto(
    String sourceCategory,
    BigDecimal amount,
    int transactionCount
) {
}
