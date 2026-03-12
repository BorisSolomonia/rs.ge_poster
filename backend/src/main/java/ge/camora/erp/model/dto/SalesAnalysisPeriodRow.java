package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;

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
    List<String> events,
    SalesAnalysisStatus status
) {
}
