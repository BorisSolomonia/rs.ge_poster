package ge.camora.erp.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SupplierDebtRawPayloadDto(
    LocalDate dateFrom,
    LocalDate dateTo,
    LocalDateTime generatedAt,
    List<SupplierDebtRawPayloadSourceDto> sources
) {
}
