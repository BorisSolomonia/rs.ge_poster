package ge.camora.erp.module.cashflow;

/**
 * Identifier a categorization rule matches on. Precedence is TAX_ID before
 * IBAN before NAME (most-to-least unique) when resolving a transaction.
 */
public enum CashFlowMatchType {
    TAX_ID,
    IBAN,
    NAME
}
