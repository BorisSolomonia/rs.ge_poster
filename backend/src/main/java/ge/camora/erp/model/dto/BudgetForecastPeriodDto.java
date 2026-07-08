package ge.camora.erp.model.dto;

import java.time.LocalDate;

/** One forecast column: a future week or month. */
public record BudgetForecastPeriodDto(
    String type,   // "WEEK" | "MONTH"
    String key,    // "2026-08" for a month, weekStart ISO date "2026-08-03" for a week
    String labelKa,
    LocalDate start,
    LocalDate end
) {
}
