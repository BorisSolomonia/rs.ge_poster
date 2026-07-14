package ge.camora.erp.model.dto;

import java.util.List;

/** One category's forecast row: cells aligned to the response's period order. */
public record BudgetForecastRowDto(
    String categoryId,
    String categoryName,
    String section,     // OPERATING | INVESTING | FINANCING
    String direction,   // INFLOW | OUTFLOW
    String parentId,    // null for top-level; else the parent category id (for indentation)
    List<BudgetForecastCellDto> cells
) {
}
