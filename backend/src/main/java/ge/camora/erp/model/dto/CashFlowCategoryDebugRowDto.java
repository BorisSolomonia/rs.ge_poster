package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowCategoryDebugRowDto(
    int sourceRow,
    String date,
    String month,
    String sourceCategory,
    String normalizedSourceCategory,
    String effectiveCategory,
    String normalizedEffectiveCategory,
    String group,
    String classificationReason,
    boolean countedAsIncome,
    BigDecimal incomeAmount,
    String rawCashInflow,
    String rawBogInflow,
    String rawTbcInflow,
    String rawCashBalance,
    String rawBogBalance,
    String rawTbcBalance,
    BigDecimal cashInflow,
    BigDecimal bogInflow,
    BigDecimal tbcInflow,
    List<String> issues
) {
}
