package ge.camora.erp.model.dto;

public record SalesAnalysisResult(
    String dateFrom,
    String dateTo,
    String generatedAt,
    java.util.List<String> availableEvents,
    SalesAnalysisAggregationBlock day,
    SalesAnalysisAggregationBlock week,
    SalesAnalysisAggregationBlock month
) {
}
