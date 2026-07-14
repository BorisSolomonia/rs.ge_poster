package ge.camora.erp.model.dto;

/**
 * Request to categorize a drilled-down transaction. {@code scope} is SINGLE
 * ("Apply to This Only" — a per-transaction override) or CASCADE ("Apply to All"
 * — a global rule from the transaction's best identifier). The counterparty fields
 * are used only for CASCADE.
 */
public record CashFlowCategorizeRequest(
    String fingerprint,
    String categoryId,
    String scope,
    String counterpartyInn,
    String counterpartyAccount,
    String counterparty
) {
}
