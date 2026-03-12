package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record SalesAnalysisPeriodRow(
    String key,
    String dateFrom,
    String dateTo,
    BigDecimal sales,
    BigDecimal tbcIncome,
    BigDecimal bogIncome,
    BigDecimal bankIncome,
    BigDecimal variance,
    BigDecimal variancePercent,
    BigDecimal captureRatio,
    BigDecimal bankMixTbc,
    BigDecimal bankMixBog,
    SalesAnalysisStatus status
) {
}
