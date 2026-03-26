package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowCategoryDebugDto(
    String category,
    String normalizedCategory,
    String dateFrom,
    String dateTo,
    BigDecimal totalAmount,
    int includedRowCount,
    int excludedRowCount,
    List<CashFlowCategoryDebugMonthDto> months,
    List<CashFlowCategoryDebugRowDto> rows
) {
}
