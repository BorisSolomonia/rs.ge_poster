package ge.camora.erp.module.cashflow;

import ge.camora.erp.model.dto.CashFlowOverviewDto;
import ge.camora.erp.model.dto.CashFlowSyncStatusDto;
import ge.camora.erp.model.dto.CashFlowWarningDto;

import java.time.LocalDateTime;
import java.util.List;

public record CashFlowSnapshot(
    CashFlowOverviewDto overview,
    List<CashFlowLedgerRow> rows,
    List<CashFlowWarningDto> warnings,
    String lastError,
    int rowCount,
    LocalDateTime syncStartedAt,
    LocalDateTime syncCompletedAt,
    LocalDateTime successAt
) {
    public CashFlowSyncStatusDto toStatus(boolean refreshInProgress) {
        String status = lastError == null
            ? (successAt == null ? "IDLE" : "READY")
            : "ERROR";
        return new CashFlowSyncStatusDto(
            status,
            syncStartedAt == null ? null : syncStartedAt.toString(),
            syncCompletedAt == null ? null : syncCompletedAt.toString(),
            successAt == null ? null : successAt.toString(),
            lastError,
            rowCount,
            refreshInProgress
        );
    }
}
