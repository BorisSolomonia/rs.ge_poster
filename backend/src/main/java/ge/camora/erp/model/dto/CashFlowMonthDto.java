package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowMonthDto(
    String month,
    BigDecimal totalInflow,
    BigDecimal totalOutflow,
    BigDecimal cashInflow,
    BigDecimal cashOutflow,
    BigDecimal bogInflow,
    BigDecimal bogOutflow,
    BigDecimal tbcInflow,
    BigDecimal tbcOutflow,
    BigDecimal endingCash,
    BigDecimal endingBog,
    BigDecimal endingTbc,
    BigDecimal totalBankBalance,
    BigDecimal totalEndingBalance,
    BigDecimal netMovement,
    int warningCount,
    int flaggedRowCount,
    List<CashFlowGroupDto> groups
) {
}
