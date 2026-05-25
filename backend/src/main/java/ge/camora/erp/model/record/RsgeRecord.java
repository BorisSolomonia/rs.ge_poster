package ge.camora.erp.model.record;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RsgeRecord(
    String waybillNumber,
    String supplierRaw,
    String productName,
    String unitOfMeasure,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal totalPrice,
    LocalDateTime recordDate,
    String supplierTin,
    String supplierName
) {
    public RsgeRecord(
        String waybillNumber,
        String supplierRaw,
        String productName,
        String unitOfMeasure,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        LocalDateTime recordDate
    ) {
        this(
            waybillNumber,
            supplierRaw,
            productName,
            unitOfMeasure,
            quantity,
            unitPrice,
            totalPrice,
            recordDate,
            "",
            ""
        );
    }
}
