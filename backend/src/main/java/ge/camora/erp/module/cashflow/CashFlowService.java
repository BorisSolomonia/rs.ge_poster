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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final int DRILLDOWN_LIMIT = 2000;

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

    // Monotonic stamp bumped on every mutation of rules / overrides / categories.
    // It keys the resolution + matrix caches so a config change makes stale entries
    // unreachable (no explicit invalidation walk needed). Shared with ForecastService.
    private final AtomicLong version = new AtomicLong();

    // Per-transaction resolution (category + SHA-256 fingerprint) is pure given a
    // transaction and a config version, so it is cached across requests. Keyed by
    // (source, transaction, version); a config bump rotates the version and orphans
    // the old entries, which age out by size. Fingerprints are small, so the ceiling
    // is generous. This removes the per-request re-hash + re-resolve of every row.
    private final Cache<ResolutionKey, Resolution> resolutionCache = Caffeine.newBuilder()
        .maximumSize(200_000)
        .build();

    // Fully-built matrix DTO, keyed (from, to, version). The 60s TTL mirrors
    // sourceCache so freshness relative to bank data is unchanged; refresh()
    // invalidates it so a forced re-sync is never shadowed by a cached matrix.
    private final Cache<MatrixKey, CashFlowMatrixDto> matrixCache = Caffeine.newBuilder()
        .maximumSize(256)
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .build();

    private volatile LocalDateTime lastRefreshAt;
    private volatile String lastRefreshError = "";

    /** Current config version; ForecastService keys its own result cache on this. */
    long currentVersion() {
        return version.get();
    }

    /** Bumped by every rules/overrides/categories mutation to invalidate the caches. */
    void bumpVersion() {
        version.incrementAndGet();
    }

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
        MatrixKey key = new MatrixKey(range.dateFrom(), range.dateTo(), version.get());
        CashFlowMatrixDto built = matrixCache.get(key, ignored -> buildMatrix(range));
        // Restamp generatedAt on every call so a cache hit still reports request time,
        // exactly as the uncached build did (the heavy aggregation is what we cached).
        return new CashFlowMatrixDto(built.from(), built.to(), built.months(), built.sections(), LocalDateTime.now());
    }

    private CashFlowMatrixDto buildMatrix(RangeKey range) {
        List<SourcedTransaction> transactions = sourced(range, false);
        List<String> months = monthColumns(range);
        ResolutionContext context = loadContext();
        Map<String, CashFlowCategory> categories = context.categoriesById();

        // categoryId -> (monthKey -> signed total) and categoryId -> count
        Map<String, Map<String, BigDecimal>> byCategory = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SourcedTransaction sourced : transactions) {
            Resolution resolution = resolve(sourced, context);
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
                List<CashFlowCategory> topLevel = context.categories().stream()
                    .filter(category -> category.getSection() == section && category.getDirection() == direction)
                    .filter(category -> category.getParentId() == null)
                    .toList();
                for (CashFlowCategory parent : topLevel) {
                    // Sub-category rows with activity, then the parent = own + children roll-up.
                    List<CashFlowMatrixCategoryDto> children = new ArrayList<>();
                    for (CashFlowCategory child : context.categories()) {
                        if (!parent.getId().equals(child.getParentId())) {
                            continue;
                        }
                        Map<String, BigDecimal> childMonthly = byCategory.get(child.getId());
                        if (childMonthly == null || childMonthly.isEmpty()) {
                            continue;
                        }
                        children.add(new CashFlowMatrixCategoryDto(
                            child.getId(), child.getNameKa(), totalOf(childMonthly), fillMonths(months, childMonthly),
                            counts.getOrDefault(child.getId(), 0), List.of()));
                    }
                    Map<String, BigDecimal> ownMonthly = byCategory.get(parent.getId());
                    boolean hasOwn = ownMonthly != null && !ownMonthly.isEmpty();
                    if (!hasOwn && children.isEmpty()) {
                        continue; // no activity in this parent or any of its children
                    }
                    Map<String, BigDecimal> rollup = new LinkedHashMap<>();
                    if (hasOwn) {
                        ownMonthly.forEach((month, value) -> rollup.merge(month, value, BigDecimal::add));
                    }
                    for (CashFlowMatrixCategoryDto child : children) {
                        child.monthly().forEach((month, value) -> rollup.merge(month, value, BigDecimal::add));
                    }
                    int rollupCount = counts.getOrDefault(parent.getId(), 0)
                        + children.stream().mapToInt(CashFlowMatrixCategoryDto::transactionCount).sum();
                    categoryRows.add(new CashFlowMatrixCategoryDto(
                        parent.getId(), parent.getNameKa(), totalOf(rollup), fillMonths(months, rollup), rollupCount, children));
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
        ResolutionContext context = loadContext();
        Map<String, CashFlowCategory> categories = context.categoriesById();
        List<SourcedTransaction> transactions = sourced(range, false);

        // Drilling a parent includes its sub-categories so the list matches the
        // parent's matrix total; drilling a leaf/sub-category matches just itself.
        Set<String> targetIds = new java.util.HashSet<>();
        targetIds.add(categoryId);
        context.categories().stream()
            .filter(category -> categoryId.equals(category.getParentId()))
            .forEach(category -> targetIds.add(category.getId()));

        List<CashFlowTransactionDto> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SourcedTransaction sourced : transactions) {
            Resolution resolution = resolve(sourced, context);
            if (!targetIds.contains(resolution.categoryId())) {
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
        // total stays the true sum of ALL matching transactions; only the returned
        // list is capped (most recent first) so a large all-uncategorized bucket over
        // a wide range can't produce a multi-MB response that stalls/aborts the write.
        List<CashFlowTransactionDto> limited = rows.size() > DRILLDOWN_LIMIT ? rows.subList(0, DRILLDOWN_LIMIT) : rows;
        CashFlowCategory category = categories.get(categoryId);
        return new CashFlowDrilldownDto(
            categoryId,
            category == null ? categoryId : category.getNameKa(),
            month,
            range.dateFrom(),
            range.dateTo(),
            MoneyUtil.round(total),
            List.copyOf(limited)
        );
    }

    // ── Categorize (single vs cascade) ────────────────────────────────────────

    public void categorizeSingle(String fingerprint, String categoryId) {
        requireCategory(categoryId);
        overrideStore.put(fingerprint, categoryId);
        bumpVersion();
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
        bumpVersion();
    }

    // ── Category tree CRUD (thin pass-through with DTO mapping) ───────────────

    public List<CashFlowCategoryDto> categories() {
        List<CashFlowCategory> all = categoryStore.list();
        Set<String> parentIds = all.stream()
            .map(CashFlowCategory::getParentId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        return all.stream().map(category -> toCategoryDto(category, parentIds.contains(category.getId()))).toList();
    }

    public CashFlowCategoryDto createCategory(CashFlowSection section, CashFlowDirection direction, String nameKa,
                                              String parentId, Integer order) {
        CashFlowCategoryDto created = toCategoryDto(categoryStore.create(section, direction, nameKa, parentId, order), false);
        bumpVersion();
        return created;
    }

    public CashFlowCategoryDto updateCategory(String id, CashFlowSection section, CashFlowDirection direction, String nameKa, Integer order) {
        CashFlowCategory updated = categoryStore.update(id, section, direction, nameKa, order);
        boolean hasChildren = categoryStore.list().stream().anyMatch(category -> id.equals(category.getParentId()));
        bumpVersion();
        return toCategoryDto(updated, hasChildren);
    }

    public void deleteCategory(String id) {
        categoryStore.delete(id);
        bumpVersion();
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
        bumpVersion();
        return toRuleDto(rule, categoriesById());
    }

    public void deleteRule(String id) {
        ruleStore.delete(id);
        bumpVersion();
    }

    // ── Sync / status ─────────────────────────────────────────────────────────

    public CashFlowStatusDto refresh(LocalDate dateFrom, LocalDate dateTo) {
        RangeKey range = normalizeRange(dateFrom, dateTo);
        sourceCache.invalidate(range);
        // A forced re-sync brings fresh bank data; drop cached matrices so the next
        // read rebuilds against it rather than serving a pre-refresh snapshot.
        matrixCache.invalidateAll();
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

    /**
     * Per-bank ("BOG"/"TBC") map of per-day categorized operating income: the sum of
     * CREDIT transactions that resolve (via the cash-flow rules/overrides) to a real
     * OPERATING · INFLOW category, excluding the UNCATEGORIZED_INFLOW sentinel. This
     * lets other features (e.g. sales-analysis) get bank income without re-parsing
     * uploaded statements — it reuses the same sync (sourced) and categorization
     * (resolve) as the matrix. Uncategorized/non-operating inflows (loans, transfers)
     * are excluded, so income reads 0 until the user categorizes it in cash flow.
     */
    public Map<String, Map<LocalDate, BigDecimal>> operatingIncomeByBankAndDate(LocalDate dateFrom, LocalDate dateTo) {
        RangeKey range = normalizeRange(dateFrom, dateTo);
        ResolutionContext context = loadContext();
        Map<String, Map<LocalDate, BigDecimal>> byBank = new LinkedHashMap<>();
        for (SourcedTransaction sourced : sourced(range, false)) {
            Resolution resolution = resolve(sourced, context);
            CashFlowCategory category = context.categoriesById().get(resolution.categoryId());
            if (category == null
                || category.getSection() != CashFlowSection.OPERATING
                || category.getDirection() != CashFlowDirection.INFLOW
                || CashFlowCategoryDefaults.UNCATEGORIZED_INFLOW.equals(category.getId())) {
                continue;
            }
            byBank.computeIfAbsent(sourced.source(), ignored -> new LinkedHashMap<>())
                .merge(sourced.transaction().date(), MoneyUtil.round(sourced.transaction().amount()), BigDecimal::add);
        }
        return byBank;
    }

    /**
     * Per-category, per-day signed totals of categorized bank activity over a range,
     * built with the SAME sync (sourced) and categorization (resolve) as the matrix so
     * the forecasting engine never re-implements or re-reads anything. The two
     * UNCATEGORIZED sentinels are kept in the map (the caller decides whether to drop
     * them); every other key is a real category id. Only aggregated numbers are
     * returned — the raw transactions are discarded as we fold, so a wide (multi-year)
     * forecast window does not hold tens of thousands of rows in the heap.
     */
    public Map<String, Map<LocalDate, BigDecimal>> categorizedDailySeries(LocalDate dateFrom, LocalDate dateTo) {
        RangeKey range = normalizeRange(dateFrom, dateTo);
        ResolutionContext context = loadContext();
        Map<String, Map<LocalDate, BigDecimal>> byCategory = new LinkedHashMap<>();
        for (SourcedTransaction sourced : sourced(range, false)) {
            Resolution resolution = resolve(sourced, context);
            byCategory.computeIfAbsent(resolution.categoryId(), ignored -> new LinkedHashMap<>())
                .merge(sourced.transaction().date(), MoneyUtil.round(sourced.transaction().amount()), BigDecimal::add);
        }
        return byCategory;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private List<SourcedTransaction> sourced(RangeKey range, boolean forceRefresh) {
        if (forceRefresh) {
            sourceCache.invalidate(range);
        }
        try {
            // Atomic load: concurrent requests for the same range share ONE fetch so
            // the (large) ledgers are not loaded into memory several times at once.
            return sourceCache.get(range, this::loadSources);
        } catch (RuntimeException exception) {
            lastRefreshError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            throw exception;
        }
    }

    private List<SourcedTransaction> loadSources(RangeKey range) {
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
        lastRefreshAt = LocalDateTime.now();
        lastRefreshError = "";
        return dated;
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

    // Rules, overrides and categories are loaded ONCE per request into the context
    // and reused for every transaction. Reading those files per transaction (there
    // can be tens of thousands in a wide range) was an allocation storm that
    // exhausted the heap.
    private ResolutionContext loadContext() {
        // Read the version BEFORE the stores: a mutation bumps the version only after
        // it has written, so any config we then read is stamped at that version or a
        // newer one — never a newer config under an older stamp (which would let a
        // stale cache entry win).
        long stamp = version.get();
        List<CashFlowCategory> categories = categoryStore.list();
        Map<String, CashFlowCategory> byId = categories.stream()
            .collect(Collectors.toMap(CashFlowCategory::getId, category -> category, (left, ignored) -> left, LinkedHashMap::new));
        List<TransactionCategoryRule> rules = ruleStore.list();
        return new ResolutionContext(byId, categories, rules, overrideStore.categoryByFingerprint(),
            RuleIndex.build(rules), stamp);
    }

    Resolution resolve(SourcedTransaction sourced, ResolutionContext context) {
        ResolutionKey key = new ResolutionKey(sourced.source(), sourced.transaction(), context.version());
        return resolutionCache.get(key, ignored -> resolveUncached(sourced, context));
    }

    private Resolution resolveUncached(SourcedTransaction sourced, ResolutionContext context) {
        BankTransaction txn = sourced.transaction();
        CashFlowDirection direction = directionOf(txn);
        String fingerprint = CashFlowFingerprint.of(sourced.source(), txn);
        Map<String, CashFlowCategory> categories = context.categoriesById();

        // A credit (inflow) must land in an INFLOW category and a debit (outflow) in
        // an OUTFLOW one. So an override/rule only applies when its target category's
        // direction matches this transaction — otherwise the same counterparty's
        // occasional opposite-direction transaction (e.g. a supplier refund) would be
        // filed under the wrong side. Mismatches fall through to the correct-direction
        // UNCATEGORIZED bucket.
        String overrideCategory = context.overridesByFingerprint().get(fingerprint);
        if (overrideCategory != null && matchesDirection(overrideCategory, direction, categories)) {
            return new Resolution(overrideCategory, RESOLVED_OVERRIDE, fingerprint, direction);
        }

        for (RuleCandidate candidate : ruleCandidates(txn)) {
            Optional<TransactionCategoryRule> match = context.ruleIndex().find(candidate.matchType(), candidate.value(), direction);
            if (match.isPresent() && matchesDirection(match.get().getCategoryId(), direction, categories)) {
                return new Resolution(match.get().getCategoryId(), "RULE_" + candidate.matchType().name(), fingerprint, direction);
            }
        }

        return new Resolution(CashFlowCategoryDefaults.uncategorizedId(direction), RESOLVED_UNCATEGORIZED, fingerprint, direction);
    }

    private boolean matchesDirection(String categoryId, CashFlowDirection direction, Map<String, CashFlowCategory> categories) {
        CashFlowCategory category = categories.get(categoryId);
        return category != null && category.getDirection() == direction;
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

    /**
     * O(1) replacement for the former linear rule scan. Built once per request from
     * the rule list; preserves the exact precedence a scan gave: a direction-specific
     * rule wins over a direction-agnostic one for the same (matchType, normalizedValue),
     * and the first rule seen for a slot wins (rule ids are unique per slot, so at most
     * one exists anyway).
     */
    static final class RuleIndex {
        private final Map<String, DirectionBucket> byKey;

        private RuleIndex(Map<String, DirectionBucket> byKey) {
            this.byKey = byKey;
        }

        static RuleIndex build(List<TransactionCategoryRule> rules) {
            Map<String, DirectionBucket> byKey = new HashMap<>();
            for (TransactionCategoryRule rule : rules) {
                DirectionBucket bucket = byKey.computeIfAbsent(
                    keyOf(rule.getMatchType(), rule.getMatchValue()), ignored -> new DirectionBucket());
                if (rule.getDirection() == null) {
                    if (bucket.agnostic == null) {
                        bucket.agnostic = rule;
                    }
                } else {
                    bucket.byDirection.putIfAbsent(rule.getDirection(), rule);
                }
            }
            return new RuleIndex(byKey);
        }

        Optional<TransactionCategoryRule> find(CashFlowMatchType matchType, String normalizedValue,
                                               CashFlowDirection direction) {
            DirectionBucket bucket = byKey.get(keyOf(matchType, normalizedValue));
            if (bucket == null) {
                return Optional.empty();
            }
            TransactionCategoryRule specific = bucket.byDirection.get(direction);
            return specific != null ? Optional.of(specific) : Optional.ofNullable(bucket.agnostic);
        }

        private static String keyOf(CashFlowMatchType matchType, String normalizedValue) {
            return matchType.name() + ' ' + normalizedValue;
        }

        private static final class DirectionBucket {
            private final Map<CashFlowDirection, TransactionCategoryRule> byDirection =
                new EnumMap<>(CashFlowDirection.class);
            private TransactionCategoryRule agnostic;
        }
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

    private static CashFlowCategoryDto toCategoryDto(CashFlowCategory category, boolean hasChildren) {
        return new CashFlowCategoryDto(
            category.getId(),
            category.getCode(),
            category.getSection().name(),
            category.getSection().nameKa(),
            category.getDirection().name(),
            category.getDirection().nameKa(),
            category.getNameKa(),
            category.getParentId(),
            hasChildren,
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

    record ResolutionContext(
        Map<String, CashFlowCategory> categoriesById,
        List<CashFlowCategory> categories,
        List<TransactionCategoryRule> rules,
        Map<String, String> overridesByFingerprint,
        RuleIndex ruleIndex,
        long version
    ) {
    }

    private record RangeKey(LocalDate dateFrom, LocalDate dateTo) {
    }

    private record RuleCandidate(CashFlowMatchType matchType, String value) {
    }

    // Identity under which a Resolution is memoized: same source + transaction +
    // config version ⇒ identical category and fingerprint. BankTransaction is a
    // record, so its value equality drives the cache lookup.
    private record ResolutionKey(String source, BankTransaction transaction, long version) {
    }

    private record MatrixKey(LocalDate from, LocalDate to, long version) {
    }
}
