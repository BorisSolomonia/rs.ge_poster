package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowGroupDto(
    String group,
    BigDecimal amount,
    int transactionCount,
    List<CashFlowCategoryDto> categories
) {
}
