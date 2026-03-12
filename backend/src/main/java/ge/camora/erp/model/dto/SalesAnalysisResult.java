package ge.camora.erp.model.dto;

public record SalesAnalysisResult(
    String dateFrom,
    String dateTo,
    String generatedAt,
    SalesAnalysisAggregationBlock day,
    SalesAnalysisAggregationBlock week,
    SalesAnalysisAggregationBlock month
) {
}
