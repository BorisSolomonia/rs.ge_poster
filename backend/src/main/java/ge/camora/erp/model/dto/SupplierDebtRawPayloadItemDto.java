package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierDebtRawPayloadItemDto(
    int index,
    LocalDate date,
    String direction,
    BigDecimal amount,
    String counterparty,
    String counterpartyInn,
    String counterpartyAccount,
    String reference,
    String rawPayload
) {
}
