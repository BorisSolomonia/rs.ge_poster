package ge.camora.erp.module.cashflow;

/**
 * Whether a category and the transactions in it are money coming in (credit)
 * or going out (debit). The Georgian label is the display name in the matrix.
 */
public enum CashFlowDirection {
    INFLOW("შემოსავლები"),
    OUTFLOW("გასავლები");

    private final String nameKa;

    CashFlowDirection(String nameKa) {
        this.nameKa = nameKa;
    }

    public String nameKa() {
        return nameKa;
    }
}
