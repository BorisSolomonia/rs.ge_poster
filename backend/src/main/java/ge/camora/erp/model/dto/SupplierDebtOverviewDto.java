package ge.camora.erp.model.dto;

import ge.camora.erp.model.config.SupplierPaymentMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SupplierDebtOverviewDto(
    LocalDate dateFrom,
    LocalDate dateTo,
    BigDecimal purchaseTotal,
    BigDecimal bogPaidTotal,
    BigDecimal tbcPaidTotal,
    BigDecimal cashPaidTotal,
    BigDecimal bankPaidTotal,
    BigDecimal paidTotal,
    BigDecimal debtTotal,
    int supplierCount,
    BigDecimal unmatchedPaymentTotal,
    int unmatchedPaymentCount,
    List<SupplierDebtRowDto> suppliers,
    List<SupplierDebtPaymentDto> unmatchedPayments,
    List<SupplierPaymentMapping> mappings,
    List<SupplierDebtSourceStatusDto> sourceStatuses,
    List<SupplierDebtUnmatchedGroupDto> unmatchedPaymentGroups,
    LocalDateTime snapshotGeneratedAt,
    boolean refreshInProgress,
    LocalDateTime lastRefreshStartedAt,
    LocalDateTime lastRefreshCompletedAt,
    String lastRefreshError
) {
}
