package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CashFlowDrilldownDto(
    String categoryId,
    String categoryNameKa,
    String month,
    LocalDate from,
    LocalDate to,
    BigDecimal total,
    List<CashFlowTransactionDto> transactions
) {
}
