package ge.camora.erp.util;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Shared product-pattern matching semantics used by both the reconciliation
 * engine and the /product-mappings/test-match endpoint, so previews always
 * behave exactly like the engine.
 */
public final class PatternMatcher {

    public static Pattern compile(String pattern) {
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public static boolean matches(String pattern, String value, boolean regex) {
        if (pattern == null || pattern.isBlank() || value == null) {
            return false;
        }
        if (regex) {
            try {
                return compile(pattern).matcher(value).find();
            } catch (PatternSyntaxException e) {
                return false;
            }
        }
        return value.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT));
    }

    private PatternMatcher() {}
}
