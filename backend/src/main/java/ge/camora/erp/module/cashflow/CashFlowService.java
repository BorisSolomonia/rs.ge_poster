package ge.camora.erp.module.cashflow;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.dto.CashFlowCategoryDto;
import ge.camora.erp.model.dto.CashFlowDrilldownDto;
import ge.camora.erp.model.dto.CashFlowMatrixCategoryDto;
import ge.camora.erp.model.dto.CashFlowMatrixDirectionDto;
import ge.camora.erp.model.dto.CashFlowMatrixDto;
import ge.camora.erp.model.dto.CashFlowMatrixSectionDto;
import ge.camora.erp.model.dto.CashFlowRuleDto;
import ge.camora.erp.model.dto.CashFlowStatusDto;
import ge.camora.erp.model.dto.CashFlowTransactionDto;
import ge.camora.erp.module.bankanalysis.BankTransaction;
import ge.camora.erp.module.bankanalysis.BogBusinessOnlineClient;
import ge.camora.erp.module.bankanalysis.TbcDbiClient;
import ge.camora.erp.module.supplierdebt.SourceLedgerStore;
import ge.camora.erp.store.ConfigStore;
import ge.camora.erp.util.MoneyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Builds the Agicap-style monthly cash-flow matrix from BOG + TBC bank data.
 *
 * <p>Each transaction is resolved to a leaf category with the precedence
 * override(fingerprint) &gt; rule(TAX_ID) &gt; rule(IBAN) &gt; rule(NAME) &gt;
 * UNCATEGORIZED_&lt;direction&gt;. Amounts stay signed so a reversal nets down its
 * category; because inflow categories collect (positive) credits and outflow
 * categories collect (positive) debits, the summed magnitude is already positive.
 */
@Service
public class CashFlowService {

    private static final Logger log = LoggerFactory.getLogger(CashFlowService.class);
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String BOG = "BOG";
    private static final String TBC = "TBC";

    public static final String RESOLVED_OVERRIDE = "OVERRIDE";
    public static final String RESOLVED_UNCATEGORIZED = "UNCATEGORIZED";

    private final TbcDbiClient tbcDbiClient;
    private final BogBusinessOnlineClient bogBusinessOnlineClient;
    private final SourceLedgerStore sourceLedgerStore;
    private final CashFlowCategoryStore categoryStore;
    private final TransactionCategoryRuleStore ruleStore;
    private final TransactionCategoryOverrideStore overrideStore;
    private final CamoraProperties properties;

    private final Cache<RangeKey, List<SourcedTransaction>> sourceCache = Caffeine.newBuilder()
        .maximumSize(16)
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .build();

    private volatile LocalDateTime lastRefreshAt;
    private volatile String lastRefreshError = "";

    public CashFlowService(TbcDbiClient tbcDbiClient,
                           BogBusinessOnlineClient bogBusinessOnlineClient,
                           SourceLedgerStore sourceLedgerStore,
                           CashFlowCategoryStore categoryStore,
                           TransactionCategoryRuleStore ruleStore,
                           TransactionCategoryOverrideStore overrideStore,
                           CamoraProperties properties) {
        this.tbcDbiClient = tbcDbiClient;
        this.bogBusinessOnlineClient = bogBusinessOnlineClient;
        this.sourceLedgerStore = sourceLedgerStore;
        this.categoryStore = categoryStore;
        this.ruleStore = ruleStore;
        this.overrideStore = overrideStore;
        this.properties = properties;
    }

    // ── Matrix ────────────────────────────────────────────────────────────────

    public CashFlowMatrixDto matrix(LocalDate dateFrom, LocalDate dateTo) {
        RangeKey range = normalizeRange(dateFrom, dateTo);
        List<SourcedTransaction> transactions = sourced(range, false);
        List<String> months = monthColumns(range);
        Map<String, CashFlowCategory> categories = categoriesById();

        // categoryId -> (monthKey -> signed total) and categoryId -> count
        Map<String, Map<String, BigDecimal>> byCategory = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SourcedTransaction sourced : transactions) {
            Resolution resolution = resolve(sourced, categories);
            String monthKey = sourced.transaction().date().format(MONTH_KEY);
            byCategory.computeIfAbsent(resolution.categoryId(), ignored -> new LinkedHashMap<>())
                .merge(monthKey, MoneyUtil.round(sourced.transaction().amount()), BigDecimal::add);
            counts.merge(resolution.categoryId(), 1, Integer::sum);
        }

        List<CashFlowMatrixSectionDto> sections = new ArrayList<>();
        for (CashFlowSection section : sortedSections()) {
            List<CashFlowMatrixDirectionDto> directions = new ArrayList<>();
            for (CashFlowDirection direction : List.of(CashFlowDirection.INFLOW, CashFlowDirection.OUTFLOW)) {
                List<CashFlowMatrixCategoryDto> categoryRows = new ArrayList<>();
                List<CashFlowCategory> leaves = categoryStore.list().stream()
                    .filter(category -> category.getSection() == section && category.getDirection() == direction)
                    .toList();
                for (CashFlowCategory leaf : leaves) {
                    Map<String, BigDecimal> monthly = byCategory.get(leaf.getId());
                    if (monthly == null || monthly.isEmpty()) {
                        continue; // only show categories with activity in range
                    }
                    categoryRows.add(new CashFlowMatrixCategoryDto(
                        leaf.getId(),
                        leaf.getNameKa(),
                        totalOf(monthly),
                        fillMonths(months, monthly),
                        counts.getOrDefault(leaf.getId(), 0)
                    ));
                }
                if (categoryRows.isEmpty()) {
                    continue;
                }
                BigDecimal directionTotal = categoryRows.stream()
                    .map(CashFlowMatrixCategoryDto::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                directions.add(new CashFlowMatrixDirectionDto(
                    direction.name(),
                    direction.nameKa(),
                    MoneyUtil.round(directionTotal),
                    monthlyRollup(months, categoryRows),
                    categoryRows
                ));
            }
            if (directions.isEmpty()) {
                continue;
            }
            sections.add(new CashFlowMatrixSectionDto(
                section.name(),
                section.nameKa(),
                directions.stream().map(CashFlowMatrixDirectionDto::total).reduce(BigDecimal.ZERO, BigDecimal::add),
                monthlyRollupDirections(months, directions),
                directions
            ));
        }

        return new CashFlowMatrixDto(range.dateFrom(), range.dateTo(), months, sections, LocalDateTime.now());
    }

    // ── Drill-down ──────────────────────────────────────────────────────────

    public CashFlowDrilldownDto drilldown(String categoryId, String month, LocalDate dateFrom, LocalDate dateTo) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("categoryId is required");
        }
        RangeKey range = normalizeRange(dateFrom, dateTo);
        Map<String, CashFlowCategory> categories = categoriesById();
        List<SourcedTransaction> transactions = sourced(range, false);

        List<CashFlowTransactionDto> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SourcedTransaction sourced : transactions) {
            Resolution resolution = resolve(sourced, categories);
            if (!resolution.categoryId().equals(categoryId)) {
                continue;
            }
            String monthKey = sourced.transaction().date().format(MONTH_KEY);
            if (month != null && !month.isBlank() && !month.equals(monthKey)) {
                continue;
            }
            total = total.add(MoneyUtil.round(sourced.transaction().amount()));
            rows.add(toTransactionDto(sourced, resolution, categories));
        }
        rows.sort(Comparator.comparing(CashFlowTransactionDto::date, Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed());
        CashFlowCategory category = categories.get(categoryId);
        return new CashFlowDrilldownDto(
            categoryId,
            category == null ? categoryId : category.getNameKa(),
            month,
            range.dateFrom(),
            range.dateTo(),
            MoneyUtil.round(total),
            rows
        );
    }

    // ── Categorize (single vs cascade) ────────────────────────────────────────

    public void categorizeSingle(String fingerprint, String categoryId) {
        requireCategory(categoryId);
        overrideStore.put(fingerprint, categoryId);
    }

    /**
     * Cascade: build/refresh a global rule from the transaction's best identifier
     * (TIN, else IBAN, else name) and clear any single-transaction override so the
     * now-global intent is not shadowed.
     */
    public void categorizeCascade(String fingerprint, String categoryId,
                                  String counterpartyInn, String counterpartyAccount, String counterparty) {
        requireCategory(categoryId);
        CashFlowMatchType matchType;
        String rawValue;
        String tin = CashFlowFingerprint.normalizeTin(counterpartyInn);
        if (!tin.isBlank()) {
            matchType = CashFlowMatchType.TAX_ID;
            rawValue = counterpartyInn;
        } else if (counterpartyAccount != null && !counterpartyAccount.isBlank()) {
            matchType = CashFlowMatchType.IBAN;
            rawValue = counterpartyAccount;
        } else if (counterparty != null && !counterparty.isBlank()) {
            matchType = CashFlowMatchType.NAME;
            rawValue = counterparty;
        } else {
            throw new IllegalArgumentException("Cannot cascade: transaction has no tax id, account, or name");
        }
        ruleStore.upsert(matchType, rawValue, null, categoryId, "user");
        if (fingerprint != null && !fingerprint.isBlank()) {
            overrideStore.remove(fingerprint);
        }
    }

    // ── Category tree CRUD (thin pass-through with DTO mapping) ───────────────

    public List<CashFlowCategoryDto> categories() {
        return categoryStore.list().stream().map(CashFlowService::toCategoryDto).toList();
    }

    public CashFlowCategoryDto createCategory(CashFlowSection section, CashFlowDirection direction, String nameKa, Integer order) {
        return toCategoryDto(categoryStore.create(section, direction, nameKa, order));
    }

    public CashFlowCategoryDto updateCategory(String id, CashFlowSection section, CashFlowDirection direction, String nameKa, Integer order) {
        return toCategoryDto(categoryStore.update(id, section, direction, nameKa, order));
    }

    public void deleteCategory(String id) {
        categoryStore.delete(id);
    }

    // ── Rule CRUD (mapping sheet) ─────────────────────────────────────────────

    public List<CashFlowRuleDto> rules() {
        Map<String, CashFlowCategory> categories = categoriesById();
        return ruleStore.list().stream().map(rule -> toRuleDto(rule, categories)).toList();
    }

    public CashFlowRuleDto upsertRule(CashFlowMatchType matchType, String matchValue,
                                      CashFlowDirection direction, String categoryId) {
        requireCategory(categoryId);
        TransactionCategoryRule rule = ruleStore.upsert(matchType, matchValue, direction, categoryId, "user");
        return toRuleDto(rule, categoriesById());
    }

    public void deleteRule(String id) {
        ruleStore.delete(id);
    }

    // ── Sync / status ─────────────────────────────────────────────────────────

    public CashFlowStatusDto refresh(LocalDate dateFrom, LocalDate dateTo) {
        RangeKey range = normalizeRange(dateFrom, dateTo);
        sourceCache.invalidate(range);
        sourced(range, true);
        return status();
    }

    public CashFlowStatusDto status() {
        return new CashFlowStatusDto(
            !safe(properties.getBogApi().getAccountNumber()).isBlank(),
            !safe(properties.getTbcDbi().getAccountNumber()).isBlank(),
            lastRefreshAt,
            lastRefreshError == null ? "" : lastRefreshError
        );
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private List<SourcedTransaction> sourced(RangeKey range, boolean forceRefresh) {
        if (!forceRefresh) {
            List<SourcedTransaction> cached = sourceCache.getIfPresent(range);
            if (cached != null) {
                return cached;
            }
        }
        try {
            List<SourcedTransaction> combined = new ArrayList<>();
            List<BankTransaction> bog = sourceLedgerStore.syncBank(BOG, range.dateFrom(), range.dateTo(),
                this::fetchBogStatementsInWindows);
            bog.forEach(txn -> combined.add(new SourcedTransaction(BOG, txn)));
            List<BankTransaction> tbc = sourceLedgerStore.syncBank(TBC, range.dateFrom(), range.dateTo(),
                tbcDbiClient::getAccountMovements);
            tbc.forEach(txn -> combined.add(new SourcedTransaction(TBC, txn)));
            List<SourcedTransaction> dated = combined.stream()
                .filter(sourced -> sourced.transaction().date() != null)
                .toList();
            sourceCache.put(range, dated);
            lastRefreshAt = LocalDateTime.now();
            lastRefreshError = "";
            return dated;
        } catch (RuntimeException exception) {
            lastRefreshError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            throw exception;
        }
    }

    /** Wraps BOG statement calls in windows to respect its pagination limits (mirrors SupplierDebtService). */
    private List<BankTransaction> fetchBogStatementsInWindows(LocalDate dateFrom, LocalDate dateTo) {
        int windowDays = properties.getSupplierDebt().getBogStatementWindowDays();
        if (windowDays <= 0) {
            windowDays = properties.getBogApi().getStatementChunkDays();
        }
        if (windowDays <= 0 || !dateTo.isAfter(dateFrom.plusDays(windowDays - 1L))) {
            return bogBusinessOnlineClient.getStatement(dateFrom, dateTo);
        }
        List<BankTransaction> transactions = new ArrayList<>();
        LocalDate windowStart = dateFrom;
        while (!windowStart.isAfter(dateTo)) {
            LocalDate windowEnd = minDate(
                dateTo,
                windowStart.plusDays(windowDays - 1L),
                windowStart.with(TemporalAdjusters.lastDayOfMonth()));
            transactions.addAll(bogBusinessOnlineClient.getStatement(windowStart, windowEnd));
            windowStart = windowEnd.plusDays(1);
        }
        return transactions;
    }

    Resolution resolve(SourcedTransaction sourced, Map<String, CashFlowCategory> categories) {
        BankTransaction txn = sourced.transaction();
        CashFlowDirection direction = directionOf(txn);
        String fingerprint = CashFlowFingerprint.of(sourced.source(), txn);

        Optional<String> override = overrideStore.categoryFor(fingerprint);
        if (override.isPresent() && categories.containsKey(override.get())) {
            return new Resolution(override.get(), RESOLVED_OVERRIDE, fingerprint, direction);
        }

        List<TransactionCategoryRule> rules = ruleStore.list();
        for (RuleCandidate candidate : ruleCandidates(txn)) {
            Optional<TransactionCategoryRule> match = findRule(rules, candidate.matchType(), candidate.value(), direction);
            if (match.isPresent() && categories.containsKey(match.get().getCategoryId())) {
                return new Resolution(match.get().getCategoryId(), "RULE_" + candidate.matchType().name(), fingerprint, direction);
            }
        }

        return new Resolution(CashFlowCategoryDefaults.uncategorizedId(direction), RESOLVED_UNCATEGORIZED, fingerprint, direction);
    }

    private List<RuleCandidate> ruleCandidates(BankTransaction txn) {
        List<RuleCandidate> candidates = new ArrayList<>();
        String tin = CashFlowFingerprint.normalizeTin(txn.counterpartyInn());
        if (!tin.isBlank()) {
            candidates.add(new RuleCandidate(CashFlowMatchType.TAX_ID, tin));
        }
        String iban = ConfigStore.normalizeSalesKey(txn.counterpartyAccount());
        if (!iban.isBlank()) {
            candidates.add(new RuleCandidate(CashFlowMatchType.IBAN, iban));
        }
        String name = ConfigStore.normalizeSalesKey(txn.counterparty());
        if (!name.isBlank()) {
            candidates.add(new RuleCandidate(CashFlowMatchType.NAME, name));
        }
        return candidates;
    }

    private Optional<TransactionCategoryRule> findRule(List<TransactionCategoryRule> rules,
                                                       CashFlowMatchType matchType, String normalizedValue,
                                                       CashFlowDirection direction) {
        TransactionCategoryRule agnostic = null;
        for (TransactionCategoryRule rule : rules) {
            if (rule.getMatchType() != matchType || !normalizedValue.equals(rule.getMatchValue())) {
                continue;
            }
            if (rule.getDirection() == direction) {
                return Optional.of(rule); // direction-specific wins
            }
            if (rule.getDirection() == null) {
                agnostic = rule;
            }
        }
        return Optional.ofNullable(agnostic);
    }

    private CashFlowTransactionDto toTransactionDto(SourcedTransaction sourced, Resolution resolution,
                                                    Map<String, CashFlowCategory> categories) {
        BankTransaction txn = sourced.transaction();
        CashFlowCategory category = categories.get(resolution.categoryId());
        return new CashFlowTransactionDto(
            resolution.fingerprint(),
            sourced.source(),
            txn.date(),
            txn.date().format(MONTH_KEY),
            txn.direction(),
            MoneyUtil.round(txn.amount()),
            txn.currency(),
            txn.counterparty(),
            txn.counterpartyInn(),
            txn.counterpartyAccount(),
            txn.description(),
            txn.reference(),
            resolution.categoryId(),
            category == null ? resolution.categoryId() : category.getNameKa(),
            resolution.resolvedBy()
        );
    }

    private void requireCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("categoryId is required");
        }
        if (categoryStore.findById(categoryId).isEmpty()) {
            throw new IllegalArgumentException("Unknown cash-flow category: " + categoryId);
        }
    }

    private Map<String, CashFlowCategory> categoriesById() {
        return categoryStore.list().stream()
            .collect(Collectors.toMap(CashFlowCategory::getId, category -> category, (left, ignored) -> left, LinkedHashMap::new));
    }

    private List<CashFlowSection> sortedSections() {
        return java.util.Arrays.stream(CashFlowSection.values())
            .sorted(Comparator.comparingInt(CashFlowSection::order))
            .toList();
    }

    private List<String> monthColumns(RangeKey range) {
        List<String> months = new ArrayList<>();
        YearMonth cursor = YearMonth.from(range.dateFrom());
        YearMonth end = YearMonth.from(range.dateTo());
        while (!cursor.isAfter(end)) {
            months.add(cursor.format(MONTH_KEY));
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private Map<String, BigDecimal> fillMonths(List<String> months, Map<String, BigDecimal> source) {
        Map<String, BigDecimal> filled = new LinkedHashMap<>();
        for (String month : months) {
            filled.put(month, MoneyUtil.round(source.getOrDefault(month, BigDecimal.ZERO)));
        }
        return filled;
    }

    private BigDecimal totalOf(Map<String, BigDecimal> monthly) {
        return MoneyUtil.round(monthly.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private Map<String, BigDecimal> monthlyRollup(List<String> months, List<CashFlowMatrixCategoryDto> rows) {
        Map<String, BigDecimal> rollup = new LinkedHashMap<>();
        for (String month : months) {
            BigDecimal sum = rows.stream()
                .map(row -> row.monthly().getOrDefault(month, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            rollup.put(month, MoneyUtil.round(sum));
        }
        return rollup;
    }

    private Map<String, BigDecimal> monthlyRollupDirections(List<String> months, List<CashFlowMatrixDirectionDto> directions) {
        Map<String, BigDecimal> rollup = new LinkedHashMap<>();
        for (String month : months) {
            BigDecimal sum = directions.stream()
                .map(direction -> direction.monthly().getOrDefault(month, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            rollup.put(month, MoneyUtil.round(sum));
        }
        return rollup;
    }

    private CashFlowDirection directionOf(BankTransaction txn) {
        return BankTransaction.CREDIT.equalsIgnoreCase(safe(txn.direction()).trim())
            ? CashFlowDirection.INFLOW
            : CashFlowDirection.OUTFLOW;
    }

    private RangeKey normalizeRange(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate effectiveFrom = dateFrom == null ? LocalDate.now().withDayOfMonth(1) : dateFrom;
        LocalDate effectiveTo = dateTo == null ? LocalDate.now() : dateTo;
        if (effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("dateTo must be on or after dateFrom");
        }
        return new RangeKey(effectiveFrom, effectiveTo);
    }

    private LocalDate minDate(LocalDate... dates) {
        LocalDate min = null;
        for (LocalDate date : dates) {
            if (date != null && (min == null || date.isBefore(min))) {
                min = date;
            }
        }
        return min;
    }

    private static CashFlowCategoryDto toCategoryDto(CashFlowCategory category) {
        return new CashFlowCategoryDto(
            category.getId(),
            category.getCode(),
            category.getSection().name(),
            category.getSection().nameKa(),
            category.getDirection().name(),
            category.getDirection().nameKa(),
            category.getNameKa(),
            category.getOrder(),
            category.isBuiltin()
        );
    }

    private static CashFlowRuleDto toRuleDto(TransactionCategoryRule rule, Map<String, CashFlowCategory> categories) {
        CashFlowCategory category = categories.get(rule.getCategoryId());
        return new CashFlowRuleDto(
            rule.getId(),
            rule.getMatchType().name(),
            rule.getMatchValue(),
            rule.getDirection() == null ? null : rule.getDirection().name(),
            rule.getCategoryId(),
            category == null ? rule.getCategoryId() : category.getNameKa(),
            rule.getSource(),
            rule.getUpdatedAt()
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    record SourcedTransaction(String source, BankTransaction transaction) {
    }

    record Resolution(String categoryId, String resolvedBy, String fingerprint, CashFlowDirection direction) {
    }

    private record RangeKey(LocalDate dateFrom, LocalDate dateTo) {
    }

    private record RuleCandidate(CashFlowMatchType matchType, String value) {
    }
}
