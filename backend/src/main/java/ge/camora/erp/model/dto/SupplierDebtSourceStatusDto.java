package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record SupplierDebtSourceStatusDto(
    String source,
    String status,
    String message,
    int recordCount,
    BigDecimal total
) {
}
