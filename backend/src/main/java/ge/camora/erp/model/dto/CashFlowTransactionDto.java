package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowTransactionDto(
    int sourceRow,
    String date,
    String month,
    String category,
    String group,
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
}
