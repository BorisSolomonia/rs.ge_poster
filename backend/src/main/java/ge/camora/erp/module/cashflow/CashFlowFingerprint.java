package ge.camora.erp.module.cashflow;

import ge.camora.erp.module.bankanalysis.BankTransaction;
import ge.camora.erp.store.ConfigStore;
import ge.camora.erp.util.MoneyUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable per-transaction identity used to attach a one-off ("Apply to This Only")
 * category override to a single bank transaction.
 *
 * <p>The hash intentionally excludes {@code description} and {@code rawPayload}
 * because those are volatile across re-fetches (whitespace/casing churn, running
 * balances). {@code direction} is included so BOG's paired CREDIT/DEBIT rows on
 * one record stay distinct. If a bank corrects an amount/reference on re-fetch the
 * hash changes and the override harmlessly falls back to the rule engine.
 */
public final class CashFlowFingerprint {

    public static String of(String source, BankTransaction transaction) {
        String payload = String.join("|",
            safe(source),
            transaction.date() == null ? "" : transaction.date().toString(),
            safe(transaction.direction()),
            MoneyUtil.round(transaction.amount()).toPlainString(),
            normalizeTin(transaction.counterpartyInn()),
            ConfigStore.normalizeSalesKey(transaction.counterpartyAccount()),
            ConfigStore.normalizeSalesKey(transaction.counterparty()),
            safe(transaction.reference()).trim()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static String normalizeTin(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^\\d]", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private CashFlowFingerprint() {
    }
}
