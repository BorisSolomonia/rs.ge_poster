package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record BankCategoryTotalDto(
    String direction,
    String category,
    BigDecimal amount,
    int transactionCount
) {
}
