package ge.camora.erp.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MoneyUtil {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    public static final BigDecimal MATCH_THRESHOLD = new BigDecimal("0.01");

    public static BigDecimal round(BigDecimal value) {
        if (value == null) return ZERO;
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) return ZERO;
        String normalizedInput = raw
            .trim()
            .replace('\u2212', '-')
            .replace('\u2012', '-')
            .replace('\u2013', '-')
            .replace('\u2014', '-');
        boolean negative = normalizedInput.contains("-")
            || (normalizedInput.startsWith("(") && normalizedInput.endsWith(")"));
        String cleaned = normalizedInput.replaceAll("[^\\d,.]", "");
        if (cleaned.isBlank()) return ZERO;

        int lastComma = cleaned.lastIndexOf(',');
        int lastDot = cleaned.lastIndexOf('.');
        char decimalSeparator = decimalSeparator(cleaned, lastComma, lastDot);

        String normalized;
        if (decimalSeparator == 0) {
            normalized = cleaned.replaceAll("[^\\d]", "");
        } else {
            int separatorIndex = cleaned.lastIndexOf(decimalSeparator);
            String integerPart = cleaned.substring(0, separatorIndex).replaceAll("[^\\d]", "");
            String fractionPart = cleaned.substring(separatorIndex + 1).replaceAll("[^\\d]", "");
            normalized = integerPart + "." + fractionPart;
        }

        if (normalized.isBlank() || ".".equals(normalized)) return ZERO;
        BigDecimal parsed = new BigDecimal((negative ? "-" : "") + normalized);
        return round(parsed);
    }

    private static char decimalSeparator(String cleaned, int lastComma, int lastDot) {
        if (lastComma >= 0 && lastDot >= 0) {
            return lastComma > lastDot ? ',' : '.';
        }

        int separatorIndex = Math.max(lastComma, lastDot);
        if (separatorIndex < 0) {
            return 0;
        }

        int digitsBeforeSeparator = cleaned.substring(0, separatorIndex).replaceAll("[^\\d]", "").length();
        int digitsAfterSeparator = cleaned.substring(separatorIndex + 1).replaceAll("[^\\d]", "").length();
        if (digitsAfterSeparator == 0) {
            return 0;
        }
        if (digitsAfterSeparator <= 2) {
            return cleaned.charAt(separatorIndex);
        }
        if (digitsAfterSeparator == 3 && digitsBeforeSeparator >= 2 && digitsBeforeSeparator <= 3) {
            return 0;
        }
        return cleaned.charAt(separatorIndex);
    }

    public static boolean isMatch(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(MATCH_THRESHOLD) < 0;
    }

    public static boolean isMatch(BigDecimal a, BigDecimal b, BigDecimal threshold) {
        return a.subtract(b).abs().compareTo(threshold) < 0;
    }

    private MoneyUtil() {}
}
