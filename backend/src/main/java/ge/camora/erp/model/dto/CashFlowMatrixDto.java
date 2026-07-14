package ge.camora.erp.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pivoted monthly cash-flow statement. {@code months} is the ordered list of
 * YYYY-MM column keys; every row exposes a per-month map plus a total, so the
 * frontend renders "Total | month | month | ...".
 */
public record CashFlowMatrixDto(
    LocalDate from,
    LocalDate to,
    List<String> months,
    List<CashFlowMatrixSectionDto> sections,
    LocalDateTime generatedAt
) {
}
