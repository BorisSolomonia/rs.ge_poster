package ge.camora.erp.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

public class GeorgianDateParser {

    private static final Map<String, String> GEO_MONTHS = Map.ofEntries(
        Map.entry("იან", "01"),
        Map.entry("თებ", "02"),
        Map.entry("მარ", "03"),
        Map.entry("აპრ", "04"),
        Map.entry("მაი", "05"),
        Map.entry("ივნ", "06"),
        Map.entry("ივლ", "07"),
        Map.entry("აგვ", "08"),
        Map.entry("სექ", "09"),
        Map.entry("ოქტ", "10"),
        Map.entry("ნოე", "11"),
        Map.entry("დეკ", "12")
    );

    /**
     * Parses a Georgian date string like "01-ოქტ-2025 17:05:13".
     * Splits on "-" (3 parts): ["01", "ოქტ", "2025 17:05:13"]
     * Replaces Georgian month with numeric: "01-10-2025 17:05:13"
     */
    public static LocalDateTime parse(String raw, String dateFormat) throws DateTimeParseException {
        if (raw == null || raw.isBlank()) {
            throw new DateTimeParseException("Empty date string", raw == null ? "" : raw, 0);
        }
        // Split only on the first two dashes: "01-ოქტ-2025 17:05:13"
        String[] parts = raw.split("-", 3);
        if (parts.length != 3) {
            throw new DateTimeParseException("Unexpected date format: " + raw, raw, 0);
        }
        String day = parts[0].trim();
        String geoMonth = parts[1].trim();
        String rest = parts[2].trim(); // "2025 17:05:13"

        String numericMonth = GEO_MONTHS.get(geoMonth);
        if (numericMonth == null) {
            throw new DateTimeParseException("Unknown Georgian month: " + geoMonth, raw, 0);
        }

        String normalized = day + "-" + numericMonth + "-" + rest;
        return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(dateFormat));
    }

    public static LocalDateTime parse(String raw) throws DateTimeParseException {
        return parse(raw, "dd-MM-yyyy HH:mm:ss");
    }

    private GeorgianDateParser() {}
}
