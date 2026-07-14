package ge.camora.erp.model.dto;

import java.math.BigDecimal;

/** Set a manual override for one forecast cell. */
public record BudgetOverrideRequest(
    String periodType,   // WEEK | MONTH
    String periodKey,
    String categoryId,
    BigDecimal amount
) {
}
