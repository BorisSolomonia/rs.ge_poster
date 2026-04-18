package ge.camora.erp.model.dto;

public record CashFlowAnalysisPeriodDto(
    String dateFrom,
    String dateTo,
    boolean available
) {
}
