package ge.camora.erp.module.bankanalysis;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankTransaction(
    LocalDate date,
    String direction,
    BigDecimal amount,
    String currency,
    String accountNumber,
    String counterparty,
    String description,
    String reference,
    String rawPayload
) {
}
