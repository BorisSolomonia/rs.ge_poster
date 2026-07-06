package ge.camora.erp.module.bankanalysis;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankTransaction(
    LocalDate date,
    String direction,
    BigDecimal amount,
    String currency,
    String accountNumber,
    String counterparty,
    String counterpartyInn,
    String counterpartyAccount,
    String description,
    String reference,
    String rawPayload
) {
    public static final String CREDIT = "CREDIT";
    public static final String DEBIT = "DEBIT";

    // Amounts are signed: a negative debit is a payment reversal and must be
    // included so it nets against the original payment in downstream sums.
    public boolean isExpense() {
        return hasDirection(DEBIT) && amount != null && amount.signum() != 0;
    }

    public boolean isIncome() {
        return hasDirection(CREDIT) && amount != null && amount.signum() != 0;
    }

    private boolean hasDirection(String expected) {
        return direction != null && expected.equalsIgnoreCase(direction.trim());
    }
}
