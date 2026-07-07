package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.Map;

public record CashFlowMatrixCategoryDto(
    String categoryId,
    String nameKa,
    BigDecimal total,
    Map<String, BigDecimal> monthly,
    int transactionCount
) {
}
