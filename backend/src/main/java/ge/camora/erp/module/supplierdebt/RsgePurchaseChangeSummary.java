package ge.camora.erp.module.supplierdebt;

import java.time.LocalDateTime;
import java.util.List;

public record RsgePurchaseChangeSummary(
    int newCount,
    int changedCount,
    int missingCount,
    int restoredCount,
    int unchangedCount,
    LocalDateTime checkedAt,
    List<String> examples
) {
    public static RsgePurchaseChangeSummary empty() {
        return new RsgePurchaseChangeSummary(0, 0, 0, 0, 0, null, List.of());
    }

    public boolean hasRisk() {
        return changedCount > 0 || missingCount > 0 || restoredCount > 0;
    }

    public boolean hasAnyChange() {
        return newCount > 0 || hasRisk();
    }

    public String shortMessage() {
        if (!hasAnyChange()) {
            return "RS.ge source integrity check: no changed rows";
        }
        return "RS.ge source integrity check: "
            + newCount + " new, "
            + changedCount + " changed, "
            + missingCount + " missing, "
            + restoredCount + " restored";
    }

    public String technicalDetails() {
        StringBuilder builder = new StringBuilder(shortMessage());
        if (checkedAt != null) {
            builder.append(System.lineSeparator()).append("Checked at: ").append(checkedAt);
        }
        if (hasRisk()) {
            builder.append(System.lineSeparator())
                .append("Review warning: RS.ge previously returned different rows for this period. ")
                .append("Balances use the latest RS.ge response, but changed/missing rows are kept in the local ledger for audit.");
        }
        if (examples != null && !examples.isEmpty()) {
            builder.append(System.lineSeparator()).append("Examples:");
            examples.forEach(example -> builder.append(System.lineSeparator()).append("- ").append(example));
        }
        return builder.toString();
    }
}
