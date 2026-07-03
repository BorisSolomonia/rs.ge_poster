package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SupplierCreditorRowDto(
    String supplierKey,
    String supplierTin,
    String supplierName,
    boolean active,
    boolean synced,
    LocalDateTime lastSyncedAt,
    String lastSyncError,
    BigDecimal purchaseTotal,
    int purchaseCount,
    BigDecimal bogPaidTotal,
    int bogPaymentCount,
    BigDecimal tbcPaidTotal,
    int tbcPaymentCount,
    BigDecimal cashPaidTotal,
    int cashPaymentCount,
    BigDecimal paidTotal,
    int paymentCount,
    BigDecimal debtLeft,
    LocalDate lastPurchaseDate,
    LocalDate lastPaymentDate,
    List<SupplierDebtPurchaseDto> purchases,
    List<SupplierDebtPaymentDto> payments
) {
}