package ge.camora.erp.module.cashflow;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.dto.CashFlowCategoryDto;
import ge.camora.erp.model.dto.CashFlowCategoryDebugDto;
import ge.camora.erp.model.dto.CashFlowCategoryDebugMonthDto;
import ge.camora.erp.model.dto.CashFlowCategoryDebugRowDto;
import ge.camora.erp.model.dto.CashFlowCategoryMappingView;
import ge.camora.erp.model.dto.CashFlowGroupDto;
import ge.camora.erp.model.dto.CashFlowMappingsViewDto;
import ge.camora.erp.model.dto.CashFlowMonthDto;
import ge.camora.erp.model.dto.CashFlowOverviewDto;
import ge.camora.erp.model.dto.CashFlowSyncStatusDto;
import ge.camora.erp.model.dto.CashFlowTransactionDto;
import ge.camora.erp.model.dto.CashFlowTransactionsResponseDto;
import ge.camora.erp.model.dto.CashFlowUnmappedCategoryDto;
import ge.camora.erp.model.dto.CashFlowWarningDto;
import ge.camora.erp.model.dto.CashFlowWarningsResponseDto;
import ge.camora.erp.store.ConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CashFlowService {

    private static final Logger log = LoggerFactory.getLogger(CashFlowService.class);
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
    private final ConfigStore configStore;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private volatile CashFlowSnapshot snapshot = new CashFlowSnapshot(
        new CashFlowOverviewDto(null, null, List.of(), List.of(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), List.of()),
        List.of(),
        List.of(),
        null,
        0,
        null,
        null,
        null
    );

    public CashFlowService(GoogleSheetsCashFlowClient sheetsClient, CamoraProperties properties, ConfigStore configStore) {
        this.sheetsClient = sheetsClient;
        this.properties = properties;
        this.configStore = configStore;
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
            logRefreshDiagnostics(parseResult.rows(), overview);
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
        LocalDate fromDate = parseOverviewBoundary(from, true);
        LocalDate toDate = parseOverviewBoundary(to, false);
        List<CashFlowLedgerRow> filteredRows = snapshot.rows().stream()
            .filter(row -> row.date() != null)
            .filter(row -> fromDate == null || !row.date().isBefore(fromDate))
            .filter(row -> toDate == null || !row.date().isAfter(toDate))
            .toList();
        List<CashFlowWarningDto> filteredWarnings = snapshot.warnings().stream()
            .filter(warning -> filteredRows.stream().anyMatch(row -> row.sourceRow() == warning.sourceRow()))
            .toList();
        return buildOverview(filteredRows, filteredWarnings);
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

    public CashFlowMappingsViewDto getMappingsView(String from, String to) {
        CashFlowOverviewDto overview = getOverview(from, to);
        List<String> canonicalCategories = collectCanonicalCategories();
        List<CashFlowCategoryMappingView> mappings = configStore.getCashFlowCategoryMappings().stream()
            .map(mapping -> new CashFlowCategoryMappingView(mapping.getSourceCategory(), mapping.getTargetCategory(), mapping.getSource()))
            .toList();
        return new CashFlowMappingsViewDto(canonicalCategories, mappings, overview.unmappedCategories());
    }

    public CashFlowCategoryMappingView upsertMapping(String sourceCategory, String targetCategory) {
        var mapping = configStore.upsertCashFlowCategoryMapping(sourceCategory, targetCategory, "manual");
        refreshSnapshot();
        return new CashFlowCategoryMappingView(mapping.getSourceCategory(), mapping.getTargetCategory(), mapping.getSource());
    }

    public void deleteMapping(String sourceCategory) {
        configStore.deleteCashFlowCategoryMapping(sourceCategory);
        refreshSnapshot();
    }

    public CashFlowCategoryDebugDto getCategoryDebug(String category, String from, String to) {
        String normalizedCategory = normalizeCategory(category);
        LocalDate fromDate = parseOverviewBoundary(from, true);
        LocalDate toDate = parseOverviewBoundary(to, false);
        List<CashFlowLedgerRow> rows = snapshot.rows().stream()
            .filter(row -> row.date() != null)
            .filter(row -> fromDate == null || !row.date().isBefore(fromDate))
            .filter(row -> toDate == null || !row.date().isAfter(toDate))
            .filter(row ->
                normalizedCategory.equals(row.normalizedSourceCategory())
                    || normalizedCategory.equals(row.normalizedCategory())
            )
            .sorted(Comparator.comparing(CashFlowLedgerRow::date).thenComparing(CashFlowLedgerRow::sourceRow))
            .toList();

        List<CashFlowCategoryDebugMonthDto> months = rows.stream()
            .filter(CashFlowLedgerRow::countedAsIncome)
            .collect(Collectors.groupingBy(CashFlowLedgerRow::monthKey, LinkedHashMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> new CashFlowCategoryDebugMonthDto(
                entry.getKey(),
                round(sum(entry.getValue(), CashFlowLedgerRow::incomeAmount)),
                entry.getValue().size()
            ))
            .toList();

        List<CashFlowCategoryDebugRowDto> debugRows = rows.stream()
            .map(row -> new CashFlowCategoryDebugRowDto(
                row.sourceRow(),
                row.date() == null ? null : row.date().toString(),
                row.monthKey(),
                row.sourceCategory(),
                row.normalizedSourceCategory(),
                row.category(),
                row.normalizedCategory(),
                row.group().name(),
                row.classificationReason(),
                row.countedAsIncome(),
                round(row.incomeAmount()),
                row.cashInflow(),
                row.bogInflow(),
                row.tbcInflow(),
                row.issues()
            ))
            .toList();

        BigDecimal totalAmount = debugRows.stream()
            .filter(CashFlowCategoryDebugRowDto::countedAsIncome)
            .map(CashFlowCategoryDebugRowDto::incomeAmount)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);

        return new CashFlowCategoryDebugDto(
            category,
            normalizedCategory,
            fromDate == null ? null : fromDate.toString(),
            toDate == null ? null : toDate.toString(),
            round(totalAmount),
            (int) debugRows.stream().filter(CashFlowCategoryDebugRowDto::countedAsIncome).count(),
            (int) debugRows.stream().filter(row -> !row.countedAsIncome()).count(),
            months,
            debugRows
        );
    }

    private ParseResult parseRows(List<List<Object>> rawRows) {
        List<CashFlowLedgerRow> rows = new ArrayList<>();
        List<CashFlowWarningDto> warnings = new ArrayList<>();
        int sourceRow = properties.getCashFlow().getSourceStartRow();
        for (List<Object> raw : rawRows) {
            List<String> row = raw.stream().map(String::valueOf).toList();
            if (isBlankRow(row)) {
                sourceRow++;
                continue;
            }
            List<String> issues = new ArrayList<>();
            LocalDate date = parseDate(row, issues);
            String monthKey = date == null ? "unknown" : YearMonth.from(date).toString();
            String sourceCategory = read(row, properties.getCashFlow().getColumns().getCategory());
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
            if ((sourceCategory == null || sourceCategory.isBlank()) && hasMovement(materialValue, serviceValue, cashInflow, cashOutflow, bogInflow, bogOutflow, tbcInflow, tbcOutflow)) {
                issues.add("blank category with movement");
            }
            if (validationFlag != null && !validationFlag.isBlank()) {
                issues.add("validation flag: " + validationFlag);
            }
            BigDecimal threshold = properties.getCashFlow().getWarningNegativeBalanceThreshold();
            if (cashBalance.compareTo(threshold) < 0 || bogBalance.compareTo(threshold) < 0 || tbcBalance.compareTo(threshold) < 0) {
                issues.add("negative balance");
            }

            String normalizedSourceCategory = normalizeCategory(sourceCategory);
            String effectiveCategory = resolveEffectiveCategory(sourceCategory);
            String normalizedEffectiveCategory = normalizeCategory(effectiveCategory);
            ClassificationResult classification = resolveGroup(
                normalizedEffectiveCategory,
                cashInflow,
                bogInflow,
                tbcInflow,
                materialValue,
                serviceValue,
                cashOutflow,
                bogOutflow,
                tbcOutflow
            );
            if (shouldSkipNonDataRow(date, sourceCategory, counterparty, comment, validationFlag, materialValue, serviceValue, cashInflow, cashOutflow, cashBalance, bogInflow, bogOutflow, bogBalance, tbcInflow, tbcOutflow, tbcBalance)) {
                sourceRow++;
                continue;
            }
            CashFlowLedgerRow rowModel = new CashFlowLedgerRow(
                sourceRow,
                date,
                monthKey,
                sourceCategory == null || sourceCategory.isBlank() ? "Uncategorized" : sourceCategory.trim(),
                normalizedSourceCategory,
                effectiveCategory,
                normalizedEffectiveCategory,
                classification.group(),
                classification.reason(),
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
            int warningSourceRow = sourceRow;
            warnings.addAll(issues.stream()
                .map(issue -> new CashFlowWarningDto(monthKey, warningSourceRow, severityFor(issue), codeFor(issue), issue))
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
        List<CashFlowUnmappedCategoryDto> unmappedCategories = buildUnmappedCategories(rows);
        BigDecimal unmappedTotal = unmappedCategories.stream()
            .map(CashFlowUnmappedCategoryDto::amount)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        return new CashFlowOverviewDto(dateFrom, dateTo, monthsAvailable, months, round(unmappedTotal), unmappedCategories);
    }

    private CashFlowMonthDto buildMonth(String month, List<CashFlowLedgerRow> rows, List<CashFlowWarningDto> warnings) {
        BigDecimal totalInflow = sumGroupAmount(rows, CashFlowGroup.INCOME);
        BigDecimal totalOutflow = sumGroupAmount(rows, CashFlowGroup.EXPENSE);
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
            .filter(row -> row.group() != CashFlowGroup.UNCATEGORIZED)
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
            row.sourceCategory(),
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
        String normalized = normalizeCategory(value);
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

    private ClassificationResult resolveGroup(
        String normalizedCategory,
        BigDecimal cashInflow,
        BigDecimal bogInflow,
        BigDecimal tbcInflow,
        BigDecimal materialValue,
        BigDecimal serviceValue,
        BigDecimal cashOutflow,
        BigDecimal bogOutflow,
        BigDecimal tbcOutflow
    ) {
        if (matches(normalizedCategory, properties.getCashFlow().getDividendKeywords())) {
            return new ClassificationResult(CashFlowGroup.DIVIDEND, "matched dividend keywords");
        }
        if (matches(normalizedCategory, properties.getCashFlow().getSafeKeywords())) {
            return new ClassificationResult(CashFlowGroup.SAFE, "matched safe keywords");
        }
        if (matchesExact(normalizedCategory, properties.getCashFlow().getIncomeKeywords())) {
            return new ClassificationResult(CashFlowGroup.INCOME, "matched income keywords");
        }
        if (matches(normalizedCategory, properties.getCashFlow().getExpenseKeywords())) {
            return new ClassificationResult(CashFlowGroup.EXPENSE, "matched expense keywords");
        }
        if (materialValue.signum() > 0 || serviceValue.signum() > 0 || cashOutflow.signum() > 0 || bogOutflow.signum() > 0 || tbcOutflow.signum() > 0) {
            return new ClassificationResult(CashFlowGroup.EXPENSE, "derived expense from outflow or cost columns");
        }
        if (cashInflow.signum() > 0 || bogInflow.signum() > 0 || tbcInflow.signum() > 0) {
            return new ClassificationResult(CashFlowGroup.UNCATEGORIZED, "inflow present but category not mapped to income");
        }
        return new ClassificationResult(CashFlowGroup.UNCATEGORIZED, "no classification rule matched");
    }

    private String resolveEffectiveCategory(String sourceCategory) {
        if (sourceCategory == null || sourceCategory.isBlank()) {
            return "Uncategorized";
        }
        return configStore.findCashFlowCategoryMapping(sourceCategory)
            .map(mapping -> mapping.getTargetCategory().trim())
            .orElse(sourceCategory.trim());
    }

    private String normalizeCategory(String value) {
        return ConfigStore.normalizeSalesKey(value);
    }

    private boolean matches(String normalizedValue, Collection<String> keywords) {
        return keywords.stream()
            .filter(Objects::nonNull)
            .map(this::normalizeCategory)
            .anyMatch(keyword -> !keyword.isBlank() && normalizedValue.contains(keyword));
    }

    private boolean matchesExact(String normalizedValue, Collection<String> keywords) {
        return keywords.stream()
            .filter(Objects::nonNull)
            .map(this::normalizeCategory)
            .anyMatch(keyword -> !keyword.isBlank() && normalizedValue.equals(keyword));
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

    private BigDecimal sumGroupAmount(List<CashFlowLedgerRow> rows, CashFlowGroup group) {
        return rows.stream()
            .filter(row -> row.group() == group)
            .map(CashFlowLedgerRow::groupAmount)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    }

    private String severityFor(String issue) {
        if (issue.contains("negative balance") || issue.contains("missing or invalid date")) {
            return "ERROR";
        }
        return "WARN";
    }

    private String codeFor(String issue) {
        return normalizeCategory(issue).replace(' ', '_');
    }

    private boolean isBlankRow(List<String> row) {
        return row.stream().allMatch(value -> value == null || value.isBlank());
    }

    private boolean shouldSkipNonDataRow(
        LocalDate date,
        String category,
        String counterparty,
        String comment,
        String validationFlag,
        BigDecimal materialValue,
        BigDecimal serviceValue,
        BigDecimal cashInflow,
        BigDecimal cashOutflow,
        BigDecimal cashBalance,
        BigDecimal bogInflow,
        BigDecimal bogOutflow,
        BigDecimal bogBalance,
        BigDecimal tbcInflow,
        BigDecimal tbcOutflow,
        BigDecimal tbcBalance
    ) {
        if (date != null) {
            return false;
        }
        if ((validationFlag != null && !validationFlag.isBlank())
            || (counterparty != null && !counterparty.isBlank())
            || (comment != null && !comment.isBlank())) {
            return false;
        }
        if (hasAnyNonZero(materialValue, serviceValue, cashInflow, cashOutflow, cashBalance, bogInflow, bogOutflow, bogBalance, tbcInflow, tbcOutflow, tbcBalance)) {
            return false;
        }
        return category == null || !category.isBlank();
    }

    private boolean hasAnyNonZero(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value.signum() != 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate parseOverviewBoundary(String raw, boolean startBoundary) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.matches("\\d{4}-\\d{2}$")) {
            YearMonth yearMonth = YearMonth.parse(value);
            return startBoundary ? yearMonth.atDay(1) : yearMonth.atEndOfMonth();
        }
        return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private List<CashFlowUnmappedCategoryDto> buildUnmappedCategories(List<CashFlowLedgerRow> rows) {
        return rows.stream()
            .filter(row -> row.group() == CashFlowGroup.UNCATEGORIZED)
            .filter(row -> row.groupAmount().signum() > 0)
            .collect(Collectors.groupingBy(CashFlowLedgerRow::sourceCategory, LinkedHashMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> new CashFlowUnmappedCategoryDto(
                entry.getKey(),
                round(sum(entry.getValue(), CashFlowLedgerRow::groupAmount)),
                entry.getValue().size()
            ))
            .sorted(Comparator.comparing(CashFlowUnmappedCategoryDto::amount).reversed())
            .toList();
    }

    private List<String> collectCanonicalCategories() {
        Set<String> categories = new LinkedHashSet<>();
        snapshot.rows().stream()
            .map(CashFlowLedgerRow::sourceCategory)
            .filter(category -> category != null && !category.isBlank() && !"Uncategorized".equalsIgnoreCase(category))
            .forEach(categories::add);
        configStore.getCashFlowCategoryMappings().stream()
            .map(mapping -> mapping.getTargetCategory())
            .filter(category -> category != null && !category.isBlank())
            .forEach(categories::add);
        properties.getCashFlow().getIncomeKeywords().stream()
            .filter(keyword -> keyword != null && !keyword.isBlank())
            .forEach(categories::add);
        properties.getCashFlow().getExpenseKeywords().stream()
            .filter(keyword -> keyword != null && !keyword.isBlank() && !keyword.contains(" "))
            .forEach(categories::add);
        properties.getCashFlow().getSafeKeywords().stream()
            .filter(keyword -> keyword != null && !keyword.isBlank() && !keyword.contains(" "))
            .forEach(categories::add);
        properties.getCashFlow().getDividendKeywords().stream()
            .filter(keyword -> keyword != null && !keyword.isBlank())
            .forEach(categories::add);
        return categories.stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private void logRefreshDiagnostics(List<CashFlowLedgerRow> rows, CashFlowOverviewDto overview) {
        if (!properties.getCashFlow().isDebug()) {
            return;
        }
        String minDate = rows.stream()
            .map(CashFlowLedgerRow::date)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo)
            .map(LocalDate::toString)
            .orElse("n/a");
        String maxDate = rows.stream()
            .map(CashFlowLedgerRow::date)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .map(LocalDate::toString)
            .orElse("n/a");
        log.info(
            "Cash flow refresh debug: sheetId={}, range={}, startRow={}, rows={}, minDate={}, maxDate={}",
            properties.getCashFlow().getSheetId(),
            properties.getCashFlow().getRange(),
            properties.getCashFlow().getSourceStartRow(),
            rows.size(),
            minDate,
            maxDate
        );
        rows.stream()
            .filter(row -> matchesExact(row.normalizedCategory(), properties.getCashFlow().getIncomeKeywords()))
            .collect(Collectors.groupingBy(CashFlowLedgerRow::monthKey, LinkedHashMap::new, Collectors.toList()))
            .forEach((month, monthRows) -> log.info(
                "Cash flow income debug: month={}, category={}, amount={}, rows={}",
                month,
                "რეალიზაცია",
                round(sum(monthRows, CashFlowLedgerRow::incomeAmount)),
                monthRows.size()
            ));
        long inflowUncategorized = rows.stream()
            .filter(row -> row.group() == CashFlowGroup.UNCATEGORIZED)
            .filter(row -> row.totalInflow().signum() > 0)
            .count();
        log.info("Cash flow income debug: inflow rows still uncategorized={}", inflowUncategorized);
        overview.unmappedCategories().stream()
            .limit(10)
            .forEach(category -> log.info(
                "Cash flow unmapped debug: sourceCategory={}, amount={}, transactions={}",
                category.sourceCategory(),
                category.amount(),
                category.transactionCount()
            ));
        rows.stream()
            .filter(row -> row.totalInflow().signum() > 0)
            .filter(row -> row.group() != CashFlowGroup.INCOME)
            .filter(row -> isCloseToIncomeKeyword(row.normalizedSourceCategory()))
            .limit(20)
            .forEach(row -> log.info(
                "Cash flow income mismatch: row={}, sourceCategory='{}', normalized='{}', effectiveCategory='{}', group={}, reason={}, inflow={}",
                row.sourceRow(),
                row.sourceCategory(),
                row.normalizedSourceCategory(),
                row.category(),
                row.group(),
                row.classificationReason(),
                row.totalInflow()
            ));
    }

    private boolean isCloseToIncomeKeyword(String normalizedCategory) {
        return properties.getCashFlow().getIncomeKeywords().stream()
            .map(this::normalizeCategory)
            .filter(keyword -> !keyword.isBlank())
            .anyMatch(keyword ->
                normalizedCategory.contains(keyword)
                    || keyword.contains(normalizedCategory)
                    || levenshtein(normalizedCategory, keyword) <= 2
            );
    }

    private int levenshtein(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return Integer.MAX_VALUE;
        }
        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[left.length()][right.length()];
    }

    private record ParseResult(List<CashFlowLedgerRow> rows, List<CashFlowWarningDto> warnings) {
    }

    private record ClassificationResult(CashFlowGroup group, String reason) {
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
