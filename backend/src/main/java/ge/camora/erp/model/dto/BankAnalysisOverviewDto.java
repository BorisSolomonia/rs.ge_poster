package ge.camora.erp.model.dto;

import ge.camora.erp.model.config.BankTransactionMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BankAnalysisOverviewDto(
    LocalDate dateFrom,
    LocalDate dateTo,
    String provider,
    String accountNumber,
    String currency,
    BigDecimal totalCredits,
    BigDecimal totalDebits,
    BigDecimal netMovement,
    int transactionCount,
    List<BankCategoryTotalDto> categoryTotals,
    List<BankUnmappedGroupDto> largeUnmappedCredits,
    List<BankUnmappedGroupDto> unmappedDebitReceivers,
    List<BankTransactionMapping> mappings,
    List<BankTransactionDto> transactions
) {
}
