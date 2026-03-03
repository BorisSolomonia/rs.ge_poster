package ge.camora.erp.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ReconciliationResult(
    String runId,
    LocalDate dateFrom,
    LocalDate dateTo,
    LocalDateTime generatedAt,
    LocalDateTime expiresAt,
    ReconciliationSummary summary,
    List<ReconciliationLineResult> lines,
    NewSuppliersDiscovered newSuppliersDiscovered
) {}
