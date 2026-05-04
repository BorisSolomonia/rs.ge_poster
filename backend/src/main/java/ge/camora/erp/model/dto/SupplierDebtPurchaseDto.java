package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierDebtPurchaseDto(
    String waybillNumber,
    LocalDate date,
    BigDecimal amount,
    String supplierTin,
    String supplierName
) {
}
