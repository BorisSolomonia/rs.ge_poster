package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record BankUnmappedGroupDto(
    String direction,
    String matchText,
    String counterparty,
    String description,
    BigDecimal amount,
    int transactionCount,
    BigDecimal largestTransaction
) {
}
