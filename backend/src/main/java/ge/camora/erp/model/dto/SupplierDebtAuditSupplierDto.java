package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record SupplierDebtAuditSupplierDto(
    String supplierKey,
    String supplierTin,
    String supplierName,
    boolean passed,
    BigDecimal snapshotPurchaseTotal,
    BigDecimal freshPurchaseTotal,
    BigDecimal snapshotBogPaidTotal,
    BigDecimal freshBogPaidTotal,
    BigDecimal snapshotTbcPaidTotal,
    BigDecimal freshTbcPaidTotal,
    BigDecimal snapshotCashPaidTotal,
    BigDecimal freshCashPaidTotal,
    BigDecimal snapshotDebtLeft,
    BigDecimal freshDebtLeft,
    BigDecimal debtDifference
) {
}
