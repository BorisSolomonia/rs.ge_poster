package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single bank transaction shown in a drill-down. {@code fingerprint} is the
 * stable id the UI echoes back when categorizing. {@code resolvedBy} explains how
 * the category was assigned (OVERRIDE | RULE_TAX_ID | RULE_IBAN | RULE_NAME |
 * UNCATEGORIZED). {@code amount} is the positive magnitude for display.
 */
public record CashFlowTransactionDto(
    String fingerprint,
    String source,
    LocalDate date,
    String monthKey,
    String direction,
    BigDecimal amount,
    String currency,
    String counterparty,
    String counterpartyInn,
    String counterpartyAccount,
    String description,
    String reference,
    String categoryId,
    String categoryNameKa,
    String resolvedBy
) {
}
