package ge.camora.erp.model.dto;

import java.math.BigDecimal;

/**
 * One forecast cell (category × period). {@code baseline} is what the algorithm
 * produced; {@code amount} is what to display/plan with — the manual override when
 * present, otherwise the baseline. {@code basis} explains how the baseline was
 * derived (SEASONAL_GROWTH / TREND / AVERAGE / NONE) so management can judge and
 * correct it.
 */
public record BudgetForecastCellDto(
    String periodKey,
    BigDecimal baseline,
    BigDecimal amount,
    boolean overridden,
    String basis
) {
}
