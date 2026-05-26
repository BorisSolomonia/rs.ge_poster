package ge.camora.erp.module.supplierdebt;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RsgePurchaseFingerprint(
    String rowKey,
    String contentHash,
    String waybillNumber,
    String supplierTin,
    String supplierName,
    LocalDate date,
    BigDecimal amount
) {
}
