package ge.camora.erp.model.dto;

public record ReconciliationSummary(
    int totalLines,
    int matched,
    int discrepancy,
    int missingPoster,
    int missingRsge
) {}
