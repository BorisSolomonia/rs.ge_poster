package ge.camora.erp.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReconciliationResultSummary(
    String runId,
    LocalDate dateFrom,
    LocalDate dateTo,
    LocalDateTime generatedAt,
    LocalDateTime expiresAt,
    ReconciliationSummary summary
) {}
