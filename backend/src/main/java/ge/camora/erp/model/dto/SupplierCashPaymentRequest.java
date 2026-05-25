package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierCashPaymentRequest(
    String supplierKey,
    String supplierTin,
    String supplierName,
    LocalDate date,
    BigDecimal amount,
    String note
) {
}
