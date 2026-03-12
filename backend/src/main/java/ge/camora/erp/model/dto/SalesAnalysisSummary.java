package ge.camora.erp.model.dto;

public record SalesAnalysisSummary(
    int periodCount,
    SalesAnalysisMetric totalSales,
    SalesAnalysisMetric totalBankIncome,
    SalesAnalysisMetric totalTbcIncome,
    SalesAnalysisMetric totalBogIncome,
    SalesAnalysisMetric variance,
    SalesAnalysisMetric captureRatio,
    SalesAnalysisMetric averageSales,
    SalesAnalysisMetric averageBankIncome
) {
}
