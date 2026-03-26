package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record CashFlowCategoryDebugMonthDto(
    String month,
    BigDecimal amount,
    int rowCount
) {
}
