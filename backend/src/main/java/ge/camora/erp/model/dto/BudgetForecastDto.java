package ge.camora.erp.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Full budget forecast: rolling week + month columns, per-category rows and roll-up totals. */
public record BudgetForecastDto(
    LocalDate asOf,
    List<BudgetForecastPeriodDto> periods,
    List<BudgetForecastRowDto> rows,
    List<BudgetForecastTotalDto> totals,
    boolean historyUncategorized,
    LocalDateTime generatedAt
) {
}
