package ge.camora.erp.module.cashflow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CashFlowLedgerRow(
    int sourceRow,
    LocalDate date,
    String monthKey,
    String sourceCategory,
    String category,
    CashFlowGroup group,
    String counterparty,
    String comment,
    BigDecimal materialValue,
    BigDecimal serviceValue,
    BigDecimal cashInflow,
    BigDecimal cashOutflow,
    BigDecimal cashBalance,
    BigDecimal bogInflow,
    BigDecimal bogOutflow,
    BigDecimal bogBalance,
    BigDecimal tbcInflow,
    BigDecimal tbcOutflow,
    BigDecimal tbcBalance,
    String validationFlag,
    List<String> issues
) {
    public BigDecimal totalInflow() {
        return cashInflow.add(bogInflow).add(tbcInflow);
    }

    public BigDecimal totalOutflow() {
        return cashOutflow.add(bogOutflow).add(tbcOutflow);
    }

    public BigDecimal expenseAmount() {
        return materialValue.add(serviceValue).add(totalOutflow());
    }

    public BigDecimal groupAmount() {
        return switch (group) {
            case INCOME -> totalInflow();
            case EXPENSE -> expenseAmount();
            case SAFE, DIVIDEND -> totalInflow().add(totalOutflow());
            case UNCATEGORIZED -> totalInflow().add(totalOutflow()).add(materialValue).add(serviceValue);
        };
    }
}
