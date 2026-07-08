package ge.camora.erp.model.dto;

import java.math.BigDecimal;

/** Per-period roll-up: total income, total expense and net cash (baseline and final). */
public record BudgetForecastTotalDto(
    String periodKey,
    BigDecimal inflowBaseline,
    BigDecimal inflowAmount,
    BigDecimal outflowBaseline,
    BigDecimal outflowAmount,
    BigDecimal netBaseline,
    BigDecimal netAmount
) {
}
