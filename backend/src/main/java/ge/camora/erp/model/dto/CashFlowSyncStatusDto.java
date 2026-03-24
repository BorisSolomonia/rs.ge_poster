package ge.camora.erp.model.dto;

public record CashFlowSyncStatusDto(
    String status,
    String lastSyncStartedAt,
    String lastSyncCompletedAt,
    String lastSuccessAt,
    String lastError,
    int rowCount,
    boolean refreshInProgress
) {
}
