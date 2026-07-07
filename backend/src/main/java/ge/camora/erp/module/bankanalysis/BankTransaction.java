package ge.camora.erp.module.bankanalysis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

// rawPayload holds the full raw bank payload per transaction. It is useful only
// at fetch time (debug views force-refresh to see it) and must NOT be persisted:
// the SourceLedgerStore ledgers accumulate a full date range across both banks,
// so writing rawPayload per row bloats the JSON until loading it OOMs the heap.
// @JsonIgnoreProperties keeps it out of serialization AND skips it on read, so
// existing over-sized ledger files also load without materializing those strings.
@JsonIgnoreProperties({"rawPayload"})
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
    // @JsonIgnore keeps these derived values out of the persisted ledger JSON.
    @JsonIgnore
    public boolean isExpense() {
        return hasDirection(DEBIT) && amount != null && amount.signum() != 0;
    }

    @JsonIgnore
    public boolean isIncome() {
        return hasDirection(CREDIT) && amount != null && amount.signum() != 0;
    }

    private boolean hasDirection(String expected) {
        return direction != null && expected.equalsIgnoreCase(direction.trim());
    }
}
