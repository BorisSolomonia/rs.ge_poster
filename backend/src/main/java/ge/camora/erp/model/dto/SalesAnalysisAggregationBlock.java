package ge.camora.erp.model.dto;

import java.util.List;

public record SalesAnalysisAggregationBlock(
    SalesAggregation aggregation,
    SalesAnalysisSummary summary,
    List<SalesAnalysisPeriodRow> periods,
    List<SalesAnalysisProductOption> availableProducts,
    List<SalesAnalysisProductSeries> productSeries
) {
}
