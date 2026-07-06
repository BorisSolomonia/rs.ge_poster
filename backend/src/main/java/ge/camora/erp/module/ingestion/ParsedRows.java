package ge.camora.erp.module.ingestion;

import java.util.List;

/**
 * Parser output carrying the rows that could not be parsed alongside the
 * parsed records, so silent row loss is visible to API callers.
 */
public record ParsedRows<T>(List<T> records, int skippedRows) {}
