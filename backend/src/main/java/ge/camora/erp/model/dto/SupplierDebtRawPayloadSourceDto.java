package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record SupplierDebtRawPayloadSourceDto(
    String source,
    boolean cached,
    String status,
    String message,
    String technicalDetails,
    int recordCount,
    BigDecimal total,
    List<SupplierDebtRawPayloadItemDto> payloads
) {
}
