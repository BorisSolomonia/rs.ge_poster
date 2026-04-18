package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowOverviewDto(
    String dateFrom,
    String dateTo,
    List<String> availableMonths,
    List<CashFlowMonthDto> months,
    BigDecimal unmappedTotal,
    List<CashFlowUnmappedCategoryDto> unmappedCategories,
    CashFlowAnalysisDto analysis
) {
}
