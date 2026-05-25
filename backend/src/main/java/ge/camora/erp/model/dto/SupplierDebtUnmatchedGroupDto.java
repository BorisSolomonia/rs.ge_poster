package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record SupplierDebtUnmatchedGroupDto(
    String groupKey,
    String provider,
    String matchText,
    String matchType,
    String counterparty,
    String counterpartyInn,
    String counterpartyAccount,
    String description,
    BigDecimal amount,
    int transactionCount,
    BigDecimal largestTransaction,
    List<SupplierDebtPaymentDto> examples
) {
}
