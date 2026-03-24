package ge.camora.erp.model.dto;

import java.util.List;

public record CashFlowTransactionsResponseDto(
    String month,
    String group,
    String category,
    List<CashFlowTransactionDto> transactions
) {
}
