package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierDebtPaymentDto(
    LocalDate date,
    BigDecimal amount,
    String provider,
    String counterparty,
    String counterpartyInn,
    String counterpartyAccount,
    String description,
    String reference,
    String matchReason
) {
}
