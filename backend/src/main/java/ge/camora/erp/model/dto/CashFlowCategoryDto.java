package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record CashFlowCategoryDto(
    String category,
    String group,
    BigDecimal amount,
    int transactionCount
) {
}
