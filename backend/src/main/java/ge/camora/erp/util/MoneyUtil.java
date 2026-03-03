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
        String cleaned = raw.replace(",", ".").replaceAll("[^\\d.]", "");
        if (cleaned.isBlank()) return ZERO;
        return round(new BigDecimal(cleaned));
    }

    public static boolean isMatch(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(MATCH_THRESHOLD) < 0;
    }

    public static boolean isMatch(BigDecimal a, BigDecimal b, BigDecimal threshold) {
        return a.subtract(b).abs().compareTo(threshold) < 0;
    }

    private MoneyUtil() {}
}
