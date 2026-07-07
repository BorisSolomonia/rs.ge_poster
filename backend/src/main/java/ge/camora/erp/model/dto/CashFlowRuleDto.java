package ge.camora.erp.model.dto;

import java.time.LocalDateTime;

/** A global identifier -> category rule shown/edited in the mapping sheet. */
public record CashFlowRuleDto(
    String id,
    String matchType,
    String matchValue,
    String direction,
    String categoryId,
    String categoryNameKa,
    String source,
    LocalDateTime updatedAt
) {
}
