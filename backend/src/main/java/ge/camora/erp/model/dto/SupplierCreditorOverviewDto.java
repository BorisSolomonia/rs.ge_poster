package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SupplierCreditorOverviewDto(
    LocalDate dateFrom,
    LocalDate dateTo,
    BigDecimal approximateDebtTotal,
    int syncedSupplierCount,
    int totalSupplierCount,
    LocalDateTime generatedAt,
    List<SupplierCreditorRowDto> suppliers
) {
}