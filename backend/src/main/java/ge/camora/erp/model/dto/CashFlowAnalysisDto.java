package ge.camora.erp.model.dto;

public record CashFlowAnalysisDto(
    CashFlowAnalysisPeriodDto currentPeriod,
    CashFlowAnalysisPeriodDto previousMonthPeriod,
    CashFlowAnalysisPeriodDto previousYearPeriod,
    CashFlowAnalysisMetricDto totalInflow,
    CashFlowAnalysisMetricDto totalOutflow,
    CashFlowAnalysisMetricDto netMovement,
    CashFlowAnalysisMetricDto totalEndingBalance
) {
}
