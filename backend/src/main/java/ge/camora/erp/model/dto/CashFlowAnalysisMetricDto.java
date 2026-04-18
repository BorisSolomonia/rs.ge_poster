package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record CashFlowAnalysisMetricDto(
    BigDecimal currentValue,
    BigDecimal previousMonthValue,
    CashFlowAnalysisDeltaDto previousMonthDelta,
    BigDecimal previousYearValue,
    CashFlowAnalysisDeltaDto previousYearDelta
) {
}
