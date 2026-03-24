package ge.camora.erp.module.cashflow;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.dto.CashFlowCategoryDto;
import ge.camora.erp.model.dto.CashFlowGroupDto;
import ge.camora.erp.model.dto.CashFlowMonthDto;
import ge.camora.erp.model.dto.CashFlowOverviewDto;
import ge.camora.erp.model.dto.CashFlowSyncStatusDto;
import ge.camora.erp.model.dto.CashFlowTransactionDto;
import ge.camora.erp.model.dto.CashFlowTransactionsResponseDto;
import ge.camora.erp.model.dto.CashFlowWarningDto;
import ge.camora.erp.model.dto.CashFlowWarningsResponseDto;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CashFlowService {

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("[^0-9,.-]");
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("d-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private final GoogleSheetsCashFlowClient sheetsClient;
    private final CamoraProperties properties;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private volatile CashFlowSnapshot snapshot = new CashFlowSnapshot(
        new CashFlowOverviewDto(null, null, List.of(), List.of()),
        List.of(),
        List.of(),
        null,
        0,
        null,
        null,
        null
    );

    public CashFlowService(GoogleSheetsCashFlowClient sheetsClient, CamoraProperties properties) {
        this.sheetsClient = sheetsClient;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (properties.getCashFlow().isEnabled()) {
            refreshSnapshot();
        }
    }

    @Scheduled(fixedDelayString = "${camora.cash-flow.sync-fixed-delay:14400000}")
    public void scheduledRefresh() {
        if (properties.getCashFlow().isEnabled()) {
            refreshSnapshot();
        }
    }

    public CashFlowSyncStatusDto getStatus() {
        return snapshot.toStatus(refreshInProgress.get());
    }

    public CashFlowSyncStatusDto refreshSnapshot() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            return snapshot.toStatus(true);
        }

        LocalDateTime startedAt = LocalDateTime.now();
        try {
            List<List<Object>> rawRows = sheetsClient.fetchLedgerRows();
            ParseResult parseResult = parseRows(rawRows);
            CashFlowOverviewDto overview = buildOverview(parseResult.rows(), parseResult.warnings());
            snapshot = new CashFlowSnapshot(
                overview,
                parseResult.rows(),
                parseResult.warnings(),
                null,
                parseResult.rows().size(),
                startedAt,
                LocalDateTime.now(),
                LocalDateTime.now()
            );
        } catch (Exception e) {
            snapshot = new CashFlowSnapshot(
                snapshot.overview(),
                snapshot.rows(),
                snapshot.warnings(),
                e.getMessage(),
                snapshot.rowCount(),
                startedAt,
                LocalDateTime.now(),
                snapshot.successAt()
            );
        } finally {
            refreshInProgress.set(false);
        }
        return snapshot.toStatus(false);
    }

    public CashFlowOverviewDto getOverview(String from, String to) {
        if (snapshot.overview().months().isEmpty()) {
            refreshSnapshot();
        }
        if ((from == null || from.isBlank()) && (to == null || to.isBlank())) {
            return snapshot.overview();
        }
        List<CashFlowMonthDto> months = snapshot.overview().months().stream()
            .filter(month -> (from == null || from.isBlank() || month.month().compareTo(from) >= 0)
                && (to == null || to.isBlank() || month.month().compareTo(to) <= 0))
            .toList();
        String dateFrom = months.isEmpty() ? null : months.get(0).month();
        String dateTo = months.isEmpty() ? null : months.get(months.size() - 1).month();
        return new CashFlowOverviewDto(dateFrom, dateTo, snapshot.overview().availableMonths(), months);
    }

    public List<CashFlowGroupDto> getCategories(String month) {
        CashFlowMonthDto monthDto = snapshot.overview().months().stream()
            .filter(item -> item.month().equals(month))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Not found: " + month));
        return monthDto.groups();
    }

    public CashFlowTransactionsResponseDto getTransactions(String month, String group, String category) {
        List<CashFlowTransactionDto> transactions = snapshot.rows().stream()
            .filter(row -> row.monthKey().equals(month))
            .filter(row -> group == null || group.isBlank() || row.group().name().equalsIgnoreCase(group))
            .filter(row -> category == null || category.isBlank() || row.category().equalsIgnoreCase(category))
            .sorted(Comparator.comparing(CashFlowLedgerRow::date).thenComparing(CashFlowLedgerRow::sourceRow))
            .map(this::toTransactionDto)
            .toList();
        return new CashFlowTransactionsResponseDto(month, group, category, transactions);
    }

    public CashFlowWarningsResponseDto getWarnings(String month) {
        List<CashFlowWarningDto> warnings = snapshot.warnings().stream()
            .filter(warning -> month == null || month.isBlank() || month.equals(warning.month()))
            .toList();
        return new CashFlowWarningsResponseDto(month, warnings.size(), warnings);
    }

    private ParseResult parseRows(List<List<Object>> rawRows) {
        List<CashFlowLedgerRow> rows = new ArrayList<>();
        List<CashFlowWarningDto> warnings = new ArrayList<>();
        int sourceRow = properties.getCashFlow().getSourceStartRow();
        for (List<Object> raw : rawRows) {
            List<String> row = raw.stream().map(String::valueOf).toList();
            List<String> issues = new ArrayList<>();
            LocalDate date = parseDate(row, issues);
            String monthKey = date == null ? "unknown" : YearMonth.from(date).toString();
            String category = read(row, properties.getCashFlow().getColumns().getCategory());
            String counterparty = read(row, properties.getCashFlow().getColumns().getCounterparty());
            String comment = read(row, properties.getCashFlow().getColumns().getComment());
            String validationFlag = read(row, properties.getCashFlow().getColumns().getValidationFlag());

            BigDecimal materialValue = parseAmount(read(row, properties.getCashFlow().getColumns().getMaterialValue()), issues, "material_value");
            BigDecimal serviceValue = parseAmount(read(row, properties.getCashFlow().getColumns().getServiceValue()), issues, "service_value");
            BigDecimal cashInflow = parseAmount(read(row, properties.getCashFlow().getColumns().getCashInflow()), issues, "cash_inflow");
            BigDecimal cashOutflow = parseAmount(read(row, properties.getCashFlow().getColumns().getCashOutflow()), issues, "cash_outflow");
            BigDecimal cashBalance = parseAmount(read(row, properties.getCashFlow().getColumns().getCashBalance()), issues, "cash_balance");
            BigDecimal bogInflow = parseAmount(read(row, properties.getCashFlow().getColumns().getBogInflow()), issues, "bog_inflow");
            BigDecimal bogOutflow = parseAmount(read(row, properties.getCashFlow().getColumns().getBogOutflow()), issues, "bog_outflow");
            BigDecimal bogBalance = parseAmount(read(row, properties.getCashFlow().getColumns().getBogBalance()), issues, "bog_balance");
            BigDecimal tbcInflow = parseAmount(read(row, properties.getCashFlow().getColumns().getTbcInflow()), issues, "tbc_inflow");
            BigDecimal tbcOutflow = parseAmount(read(row, properties.getCashFlow().getColumns().getTbcOutflow()), issues, "tbc_outflow");
            BigDecimal tbcBalance = parseAmount(read(row, properties.getCashFlow().getColumns().getTbcBalance()), issues, "tbc_balance");

            int movementColumns = countPositive(cashInflow, cashOutflow, bogInflow, bogOutflow, tbcInflow, tbcOutflow);
            if (movementColumns > 2) {
                issues.add("multiple movement columns populated");
            }
            if ((category == null || category.isBlank()) && hasMovement(materialValue, serviceValue, cashInflow, cashOutflow, bogInflow, bogOutflow, tbcInflow, tbcOutflow)) {
                issues.add("blank category with movement");
            }
            if (validationFlag != null && !validationFlag.isBlank()) {
                issues.add("validation flag: " + validationFlag);
            }
            BigDecimal threshold = properties.getCashFlow().getWarningNegativeBalanceThreshold();
            if (cashBalance.compareTo(threshold) < 0 || bogBalance.compareTo(threshold) < 0 || tbcBalance.compareTo(threshold) < 0) {
                issues.add("negative balance");
            }

            CashFlowGroup group = resolveGroup(category, cashInflow, bogInflow, tbcInflow, materialValue, serviceValue, cashOutflow, bogOutflow, tbcOutflow);
            CashFlowLedgerRow rowModel = new CashFlowLedgerRow(
                sourceRow,
                date,
                monthKey,
                category == null || category.isBlank() ? "Uncategorized" : category.trim(),
                group,
                counterparty,
                comment,
                materialValue,
                serviceValue,
                cashInflow,
                cashOutflow,
                cashBalance,
                bogInflow,
                bogOutflow,
                bogBalance,
                tbcInflow,
                tbcOutflow,
                tbcBalance,
                validationFlag,
                List.copyOf(issues)
            );
            rows.add(rowModel);
            warnings.addAll(issues.stream()
                .map(issue -> new CashFlowWarningDto(monthKey, sourceRow, severityFor(issue), codeFor(issue), issue))
                .toList());
            sourceRow++;
        }
        return new ParseResult(rows, warnings);
    }

    private CashFlowOverviewDto buildOverview(List<CashFlowLedgerRow> rows, List<CashFlowWarningDto> warnings) {
        Map<String, List<CashFlowLedgerRow>> byMonth = rows.stream()
            .filter(row -> !"unknown".equals(row.monthKey()))
            .collect(Collectors.groupingBy(CashFlowLedgerRow::monthKey, LinkedHashMap::new, Collectors.toList()));
        List<String> monthsAvailable = byMonth.keySet().stream().sorted().toList();
        List<CashFlowMonthDto> months = monthsAvailable.stream()
            .map(month -> buildMonth(month, byMonth.get(month), warnings))
            .toList();
        String dateFrom = months.isEmpty() ? null : months.get(0).month();
        String dateTo = months.isEmpty() ? null : months.get(months.size() - 1).month();
        return new CashFlowOverviewDto(dateFrom, dateTo, monthsAvailable, months);
    }

    private CashFlowMonthDto buildMonth(String month, List<CashFlowLedgerRow> rows, List<CashFlowWarningDto> warnings) {
        BigDecimal totalInflow = sum(rows, CashFlowLedgerRow::totalInflow);
        BigDecimal totalOutflow = sum(rows, CashFlowLedgerRow::totalOutflow);
        BigDecimal cashInflow = sum(rows, CashFlowLedgerRow::cashInflow);
        BigDecimal cashOutflow = sum(rows, CashFlowLedgerRow::cashOutflow);
        BigDecimal bogInflow = sum(rows, CashFlowLedgerRow::bogInflow);
        BigDecimal bogOutflow = sum(rows, CashFlowLedgerRow::bogOutflow);
        BigDecimal tbcInflow = sum(rows, CashFlowLedgerRow::tbcInflow);
        BigDecimal tbcOutflow = sum(rows, CashFlowLedgerRow::tbcOutflow);
        BigDecimal endingCash = lastBalance(rows, CashFlowLedgerRow::cashBalance);
        BigDecimal endingBog = lastBalance(rows, CashFlowLedgerRow::bogBalance);
        BigDecimal endingTbc = lastBalance(rows, CashFlowLedgerRow::tbcBalance);
        BigDecimal totalBankBalance = endingBog.add(endingTbc);
        BigDecimal totalEndingBalance = endingCash.add(totalBankBalance);
        BigDecimal netMovement = totalInflow.subtract(totalOutflow).setScale(2, RoundingMode.HALF_UP);
        List<CashFlowGroupDto> groups = buildGroups(rows);
        int warningCount = (int) warnings.stream().filter(warning -> month.equals(warning.month())).count();
        int flaggedCount = (int) rows.stream().filter(row -> row.validationFlag() != null && !row.validationFlag().isBlank()).count();
        return new CashFlowMonthDto(
            month,
            round(totalInflow),
            round(totalOutflow),
            round(cashInflow),
            round(cashOutflow),
            round(bogInflow),
            round(bogOutflow),
            round(tbcInflow),
            round(tbcOutflow),
            round(endingCash),
            round(endingBog),
            round(endingTbc),
            round(totalBankBalance),
            round(totalEndingBalance),
            round(netMovement),
            warningCount,
            flaggedCount,
            groups
        );
    }

    private List<CashFlowGroupDto> buildGroups(List<CashFlowLedgerRow> rows) {
        return rows.stream()
            .collect(Collectors.groupingBy(CashFlowLedgerRow::group, LinkedHashMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                List<CashFlowCategoryDto> categories = entry.getValue().stream()
                    .collect(Collectors.groupingBy(CashFlowLedgerRow::category, LinkedHashMap::new, Collectors.toList()))
                    .entrySet()
                    .stream()
                    .map(categoryEntry -> new CashFlowCategoryDto(
                        categoryEntry.getKey(),
                        entry.getKey().name(),
                        round(sum(categoryEntry.getValue(), CashFlowLedgerRow::groupAmount)),
                        categoryEntry.getValue().size()
                    ))
                    .sorted(Comparator.comparing(CashFlowCategoryDto::amount).reversed())
                    .toList();
                return new CashFlowGroupDto(
                    entry.getKey().name(),
                    round(sum(entry.getValue(), CashFlowLedgerRow::groupAmount)),
                    entry.getValue().size(),
                    categories
                );
            })
            .toList();
    }

    private CashFlowTransactionDto toTransactionDto(CashFlowLedgerRow row) {
        return new CashFlowTransactionDto(
            row.sourceRow(),
            row.date() == null ? null : row.date().toString(),
            row.monthKey(),
            row.category(),
            row.group().name(),
            row.counterparty(),
            row.comment(),
            row.materialValue(),
            row.serviceValue(),
            row.cashInflow(),
            row.cashOutflow(),
            row.cashBalance(),
            row.bogInflow(),
            row.bogOutflow(),
            row.bogBalance(),
            row.tbcInflow(),
            row.tbcOutflow(),
            row.tbcBalance(),
            row.validationFlag(),
            row.issues()
        );
    }

    private LocalDate parseDate(List<String> row, List<String> issues) {
        String fullDate = read(row, properties.getCashFlow().getColumns().getFullDate());
        if (fullDate != null && !fullDate.isBlank()) {
            LocalDate parsed = tryParseDate(fullDate);
            if (parsed != null) {
                return parsed;
            }
        }
        String day = read(row, properties.getCashFlow().getColumns().getDay());
        String month = read(row, properties.getCashFlow().getColumns().getMonth());
        String year = read(row, properties.getCashFlow().getColumns().getYear());
        if (day == null || day.isBlank() || month == null || month.isBlank() || year == null || year.isBlank()) {
            issues.add("missing or invalid date");
            return null;
        }
        try {
            int parsedDay = Integer.parseInt(day.trim());
            int parsedYear = Integer.parseInt(year.trim());
            int parsedMonth = parseMonth(month);
            return LocalDate.of(parsedYear, parsedMonth, parsedDay);
        } catch (Exception e) {
            issues.add("missing or invalid date");
            return null;
        }
    }

    private LocalDate tryParseDate(String raw) {
        String value = raw.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                TemporalAccessor accessor = formatter.parse(value);
                return LocalDate.from(accessor);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private int parseMonth(String raw) {
        String value = raw.trim();
        if (value.matches("\\d+")) {
            return Integer.parseInt(value);
        }
        String normalized = normalize(value);
        Map<String, Integer> months = Map.ofEntries(
            Map.entry("january", 1), Map.entry("იანვარი", 1),
            Map.entry("february", 2), Map.entry("თებერვალი", 2),
            Map.entry("march", 3), Map.entry("მარტი", 3),
            Map.entry("april", 4), Map.entry("აპრილი", 4),
            Map.entry("may", 5), Map.entry("მაისი", 5),
            Map.entry("june", 6), Map.entry("ივნისი", 6),
            Map.entry("july", 7), Map.entry("ივლისი", 7),
            Map.entry("august", 8), Map.entry("აგვისტო", 8),
            Map.entry("september", 9), Map.entry("სექტემბერი", 9),
            Map.entry("october", 10), Map.entry("ოქტომბერი", 10),
            Map.entry("november", 11), Map.entry("ნოემბერი", 11),
            Map.entry("december", 12), Map.entry("დეკემბერი", 12)
        );
        Integer resolved = months.get(normalized);
        if (resolved == null) {
            resolved = Month.valueOf(value.toUpperCase(Locale.ROOT)).getValue();
        }
        return resolved;
    }

    private CashFlowGroup resolveGroup(
        String category,
        BigDecimal cashInflow,
        BigDecimal bogInflow,
        BigDecimal tbcInflow,
        BigDecimal materialValue,
        BigDecimal serviceValue,
        BigDecimal cashOutflow,
        BigDecimal bogOutflow,
        BigDecimal tbcOutflow
    ) {
        String normalized = normalize(category);
        if (matches(normalized, properties.getCashFlow().getDividendKeywords())) {
            return CashFlowGroup.DIVIDEND;
        }
        if (matches(normalized, properties.getCashFlow().getSafeKeywords())) {
            return CashFlowGroup.SAFE;
        }
        if (matches(normalized, properties.getCashFlow().getIncomeKeywords())) {
            return CashFlowGroup.INCOME;
        }
        if (matches(normalized, properties.getCashFlow().getExpenseKeywords())) {
            return CashFlowGroup.EXPENSE;
        }
        if (cashInflow.signum() > 0 || bogInflow.signum() > 0 || tbcInflow.signum() > 0) {
            return CashFlowGroup.INCOME;
        }
        if (materialValue.signum() > 0 || serviceValue.signum() > 0 || cashOutflow.signum() > 0 || bogOutflow.signum() > 0 || tbcOutflow.signum() > 0) {
            return CashFlowGroup.EXPENSE;
        }
        return CashFlowGroup.UNCATEGORIZED;
    }

    private boolean matches(String normalizedValue, Collection<String> keywords) {
        return keywords.stream()
            .filter(Objects::nonNull)
            .map(this::normalize)
            .anyMatch(keyword -> !keyword.isBlank() && normalizedValue.contains(keyword));
    }

    private String read(List<String> row, int index) {
        if (index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index) == null ? "" : row.get(index).trim();
    }

    private BigDecimal parseAmount(String raw, List<String> issues, String code) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            String normalized = NUMERIC_PATTERN.matcher(raw).replaceAll("").replace(",", ".");
            if (normalized.isBlank() || normalized.equals("-")) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            issues.add("invalid " + code);
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private int countPositive(BigDecimal... values) {
        int count = 0;
        for (BigDecimal value : values) {
            if (value.signum() > 0) {
                count++;
            }
        }
        return count;
    }

    private boolean hasMovement(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value.signum() > 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal lastBalance(List<CashFlowLedgerRow> rows, BalanceExtractor extractor) {
        return rows.stream()
            .sorted(Comparator.comparing(CashFlowLedgerRow::date, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CashFlowLedgerRow::sourceRow))
            .map(extractor::extract)
            .reduce((left, right) -> right)
            .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal sum(List<CashFlowLedgerRow> rows, ValueExtractor extractor) {
        return rows.stream()
            .map(extractor::extract)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    }

    private String severityFor(String issue) {
        if (issue.contains("negative balance") || issue.contains("missing or invalid date")) {
            return "ERROR";
        }
        return "WARN";
    }

    private String codeFor(String issue) {
        return normalize(issue).replace(' ', '_');
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record ParseResult(List<CashFlowLedgerRow> rows, List<CashFlowWarningDto> warnings) {
    }

    @FunctionalInterface
    private interface ValueExtractor {
        BigDecimal extract(CashFlowLedgerRow row);
    }

    @FunctionalInterface
    private interface BalanceExtractor {
        BigDecimal extract(CashFlowLedgerRow row);
    }
}
