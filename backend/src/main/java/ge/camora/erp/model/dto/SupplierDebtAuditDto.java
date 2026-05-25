package ge.camora.erp.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SupplierDebtAuditDto(
    LocalDate dateFrom,
    LocalDate dateTo,
    LocalDateTime auditedAt,
    boolean passed,
    int sampledSupplierCount,
    int failedSupplierCount,
    List<SupplierDebtAuditSupplierDto> suppliers
) {
}
