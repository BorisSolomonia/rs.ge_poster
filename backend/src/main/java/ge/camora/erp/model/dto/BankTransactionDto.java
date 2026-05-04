package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankTransactionDto(
    LocalDate date,
    String direction,
    BigDecimal amount,
    String currency,
    String accountNumber,
    String counterparty,
    String description,
    String reference,
    String category,
    boolean mapped,
    String mappingMatchText
) {
}
