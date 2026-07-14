package ge.camora.erp.module.cashflow;

/**
 * Top level of the Agicap-style cash-flow statement (activity classification).
 * The Georgian label is the display name used in the monthly matrix.
 */
public enum CashFlowSection {
    OPERATING("საოპერაციო საქმიანობა", 1),
    INVESTING("საინვესტიციო საქმიანობა", 2),
    FINANCING("საფინანსო საქმიანობა", 3);

    private final String nameKa;
    private final int order;

    CashFlowSection(String nameKa, int order) {
        this.nameKa = nameKa;
        this.order = order;
    }

    public String nameKa() {
        return nameKa;
    }

    public int order() {
        return order;
    }
}
