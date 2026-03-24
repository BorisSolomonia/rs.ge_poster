package ge.camora.erp.model.dto;

public record CashFlowWarningDto(
    String month,
    int sourceRow,
    String severity,
    String code,
    String message
) {
}
