package ge.camora.erp.module.supplierdebt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.SupplierCashPayment;
import ge.camora.erp.model.config.SupplierPaymentMapping;
import ge.camora.erp.model.dto.SupplierDebtAuditDto;
import ge.camora.erp.model.dto.SupplierDebtAuditSupplierDto;
import ge.camora.erp.model.dto.SupplierDebtOverviewDto;
import ge.camora.erp.model.dto.SupplierDebtPaymentDto;
import ge.camora.erp.model.dto.SupplierDebtPurchaseDto;
import ge.camora.erp.model.dto.SupplierDebtRowDto;
import ge.camora.erp.model.dto.SupplierDebtSourceStatusDto;
import ge.camora.erp.model.dto.SupplierDebtUnmatchedGroupDto;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.module.bankanalysis.BankTransaction;
import ge.camora.erp.module.bankanalysis.BogBusinessOnlineClient;
import ge.camora.erp.module.bankanalysis.TbcDbiClient;
import ge.camora.erp.module.rsge.RsgePurchaseWaybillService;
import ge.camora.erp.store.ConfigStore;
import ge.camora.erp.util.MoneyUtil;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SupplierDebtService {

    private static final Pattern RAW_SUPPLIER_PATTERN = Pattern.compile("^\\((\\d+)\\)\\s*(.+)$");
    private static final String RSGE = "RSGE";
    private static final String BOG = "BOG";
    private static final String TBC = "TBC";
    private static final String CASH = "CASH";
    private static final int UNMATCHED_GROUP_EXAMPLE_LIMIT = 3;

    private final RsgePurchaseWaybillService rsgePurchaseWaybillService;
    private final BogBusinessOnlineClient bogBusinessOnlineClient;
    private final TbcDbiClient tbcDbiClient;
    private final ConfigStore configStore;
    private final CamoraProperties properties;
    private final SupplierDebtSnapshotStore snapshotStore;
    private final RsgePurchaseLedgerStore rsgePurchaseLedgerStore;
    private final Cache<RangeKey, List<RsgeRecord>> purchaseCache;
    private final Cache<RangeKey, List<BankTransaction>> bogTransactionCache;
    private final Cache<RangeKey, List<BankTransaction>> tbcTransactionCache;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    private volatile LocalDateTime lastRefreshStartedAt;
    private volatile LocalDateTime lastRefreshCompletedAt;
    private volatile String lastRefreshError = "";

    public SupplierDebtService(
        RsgePurchaseWaybillService rsgePurchaseWaybillService,
        BogBusinessOnlineClient bogBusinessOnlineClient,
        TbcDbiClient tbcDbiClient,
        ConfigStore configStore,
        CamoraProperties properties,
        SupplierDebtSnapshotStore snapshotStore,
        RsgePurchaseLedgerStore rsgePurchaseLedgerStore
    ) {
        this.rsgePurchaseWaybillService = rsgePurchaseWaybillService;
        this.bogBusinessOnlineClient = bogBusinessOnlineClient;
        this.tbcDbiClient = tbcDbiClient;
        this.configStore = configStore;
        this.properties = properties;
        this.snapshotStore = snapshotStore;
        this.rsgePurchaseLedgerStore = rsgePurchaseLedgerStore;
        long cacheTtlMinutes = Math.max(1, properties.getSupplierDebt().getSourceCacheTtlMinutes());
        long maximumCacheSize = Math.max(1, properties.getSupplierDebt().getMaximumSourceCacheSize());
        this.purchaseCache = sourceCache(cacheTtlMinutes, maximumCacheSize);
        this.bogTransactionCache = sourceCache(cacheTtlMinutes, maximumCacheSize);
        this.tbcTransactionCache = sourceCache(cacheTtlMinutes, maximumCacheSize);
    }

    public LocalDate defaultDateFrom() {
        String configured = properties.getOrganizationOpeningDate();
        if (configured == null || configured.isBlank()) {
            return LocalDate.of(2025, 1, 1);
        }
        return LocalDate.parse(configured);
    }

    public SupplierDebtOverviewDto overview(LocalDate dateFrom, LocalDate dateTo, boolean forceRefresh) {
        RangeKey range = normalizeRange(dateFrom, dateTo);
        if (forceRefresh) {
            return refreshSnapshot(range, true);
        }

        Optional<SupplierDebtOverviewDto> saved = snapshotStore.load();
        if (saved.isPresent() && canServeSavedSnapshot(saved.get(), range)) {
            if (shouldStartBackgroundRefresh(saved.get(), range)) {
                startBackgroundRefresh(range);
            }
            return withRefreshMetadata(saved.get(), refreshInProgress.get());
        }

        return refreshSnapshot(range, true);
    }

    public SupplierDebtOverviewDto analyze(LocalDate dateFrom, LocalDate dateTo) {
        return analyze(dateFrom, dateTo, false);
    }

    public SupplierDebtOverviewDto analyze(LocalDate dateFrom, LocalDate dateTo, boolean refreshSources) {
        return calculateOverview(normalizeRange(dateFrom, dateTo), refreshSources, true);
    }

    public SupplierDebtRowDto supplierTransactions(String supplierKey, LocalDate dateFrom, LocalDate dateTo, boolean forceRefresh) {
        SupplierDebtOverviewDto overview = calculateOverview(normalizeRange(dateFrom, dateTo), forceRefresh, true);
        return overview.suppliers().stream()
            .filter(row -> row.supplierKey().equals(supplierKey))
            .findFirst()
            .orElseGet(() -> emptySupplierRow(supplierKey));
    }

    public SupplierDebtAuditDto auditRandom(LocalDate dateFrom, LocalDate dateTo) {
        RangeKey range = normalizeRange(dateFrom, dateTo);
        SupplierDebtOverviewDto snapshot = snapshotStore.load()
            .filter(saved -> canServeSavedSnapshot(saved, range))
            .orElseGet(() -> refreshSnapshot(range, true));
        SupplierDebtOverviewDto fresh = calculateOverview(range, true, false);

        List<SupplierDebtRowDto> sample = new ArrayList<>(snapshot.suppliers());
        Collections.shuffle(sample);
        int sampleSize = Math.min(10, Math.max(3, (int) Math.ceil(sample.size() * 0.10)));
        if (sample.size() > sampleSize) {
            sample = sample.subList(0, sampleSize);
        }

        Map<String, SupplierDebtRowDto> freshBySupplier = fresh.suppliers().stream()
            .collect(Collectors.toMap(SupplierDebtRowDto::supplierKey, row -> row, (left, ignored) -> left));
        List<SupplierDebtAuditSupplierDto> auditedSuppliers = sample.stream()
            .map(snapshotRow -> auditSupplier(snapshotRow, freshBySupplier.get(snapshotRow.supplierKey())))
            .toList();
        int failedCount = (int) auditedSuppliers.stream().filter(row -> !row.passed()).count();
        SupplierDebtAuditDto audit = new SupplierDebtAuditDto(
            range.dateFrom(),
            range.dateTo(),
            LocalDateTime.now(),
            failedCount == 0,
            auditedSuppliers.size(),
            failedCount,
            auditedSuppliers
        );
        snapshotStore.save(withAudit(snapshot, audit));
        return audit;
    }

    private void startBackgroundRefresh(RangeKey range) {
        if (!refreshInProgress.compareAndSet(false, true)) {
            return;
        }
        LocalDateTime startedAt = LocalDateTime.now();
        lastRefreshStartedAt = startedAt;
        lastRefreshError = "";
        CompletableFuture.runAsync(() -> {
            try {
                SupplierDebtOverviewDto refreshed = calculateOverview(range, true, false);
                LocalDateTime completedAt = LocalDateTime.now();
                lastRefreshCompletedAt = completedAt;
                lastRefreshError = "";
                snapshotStore.save(withSnapshotMetadata(
                    refreshed,
                    LocalDateTime.now(),
                    false,
                    startedAt,
                    completedAt,
                    "",
                    latestAuditFromSnapshot()
                ));
            } catch (RuntimeException exception) {
                lastRefreshCompletedAt = LocalDateTime.now();
                lastRefreshError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                snapshotStore.load()
                    .map(snapshot -> withRefreshError(snapshot, startedAt, lastRefreshCompletedAt, lastRefreshError))
                    .ifPresent(snapshotStore::save);
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    private SupplierDebtOverviewDto refreshSnapshot(RangeKey range, boolean refreshSources) {
        LocalDateTime startedAt = LocalDateTime.now();
        lastRefreshStartedAt = startedAt;
        lastRefreshError = "";
        try {
            SupplierDebtOverviewDto calculated = calculateOverview(range, refreshSources, false);
            LocalDateTime completedAt = LocalDateTime.now();
            lastRefreshCompletedAt = completedAt;
            SupplierDebtOverviewDto snapshot = withSnapshotMetadata(
                calculated,
                completedAt,
                false,
                startedAt,
                completedAt,
                "",
                latestAuditFromSnapshot()
            );
            return snapshotStore.save(snapshot);
        } catch (RuntimeException exception) {
            lastRefreshCompletedAt = LocalDateTime.now();
            lastRefreshError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            throw exception;
        }
    }

    private SupplierDebtOverviewDto calculateOverview(RangeKey range, boolean refreshSources, boolean includeDetails) {
        if (refreshSources) {
            invalidateSourceCaches(range);
        }

        CompletableFuture<SourceFetchResult<RsgeRecord>> purchaseFuture = CompletableFuture.supplyAsync(() ->
            fetchSource(RSGE, purchaseCache, range, () -> rsgePurchaseWaybillService.fetchPurchaseRecords(range.dateFrom(), range.dateTo()))
        );
        CompletableFuture<SourceFetchResult<BankTransaction>> bogFuture = CompletableFuture.supplyAsync(() ->
            fetchSource(BOG, bogTransactionCache, range, () -> bogBusinessOnlineClient.getStatement(range.dateFrom(), range.dateTo()))
        );
        CompletableFuture<SourceFetchResult<BankTransaction>> tbcFuture = CompletableFuture.supplyAsync(() ->
            fetchSource(TBC, tbcTransactionCache, range, () -> tbcDbiClient.getAccountMovements(range.dateFrom(), range.dateTo()))
        );

        SourceFetchResult<RsgeRecord> purchaseSource = purchaseFuture.join();
        SourceFetchResult<BankTransaction> bogSource = bogFuture.join();
        SourceFetchResult<BankTransaction> tbcSource = tbcFuture.join();
        RsgePurchaseChangeSummary purchaseChangeSummary = purchaseSource.failed()
            ? RsgePurchaseChangeSummary.empty()
            : rsgePurchaseLedgerStore.compareAndSave(
                range.dateFrom(),
                range.dateTo(),
                purchaseFingerprints(purchaseSource.records())
            );

        Map<String, SupplierBucket> suppliers = new LinkedHashMap<>();
        List<SupplierDebtSourceStatusDto> statuses = new ArrayList<>();
        BigDecimal purchaseSourceTotal = BigDecimal.ZERO;
        for (RsgeRecord record : purchaseSource.records()) {
            SupplierIdentity identity = supplierIdentity(record);
            SupplierBucket bucket = suppliers.computeIfAbsent(identity.key(), ignored -> new SupplierBucket(identity));
            BigDecimal amount = MoneyUtil.round(record.totalPrice());
            purchaseSourceTotal = purchaseSourceTotal.add(amount);
            bucket.addPurchase(new SupplierDebtPurchaseDto(
                record.waybillNumber(),
                record.recordDate() == null ? null : record.recordDate().toLocalDate(),
                amount,
                identity.tin(),
                identity.name()
            ));
        }
        statuses.add(purchaseSource.failed()
            ? failed(RSGE, purchaseSource.errorMessage(), purchaseSource.errorDetails())
            : rsgeStatus(purchaseSource.records().size(), purchaseSourceTotal, purchaseSource.cached(), purchaseChangeSummary));

        List<SupplierPaymentMapping> mappings = configStore.getSupplierPaymentMappings();
        List<SupplierDebtPaymentDto> unmatchedPayments = new ArrayList<>();
        addBankPayments(bogSource, suppliers, mappings, unmatchedPayments, statuses);
        addBankPayments(tbcSource, suppliers, mappings, unmatchedPayments, statuses);
        addCashPayments(range.dateFrom(), range.dateTo(), suppliers, statuses);

        Optional<SupplierDebtOverviewDto> previousSnapshot = snapshotStore.load()
            .filter(snapshot -> canServeSavedSnapshot(snapshot, range));
        Set<String> previousSupplierKeys = previousSnapshot
            .map(snapshot -> snapshot.suppliers().stream()
                .map(SupplierDebtRowDto::supplierKey)
                .collect(Collectors.toSet()))
            .orElse(Set.of());

        List<SupplierDebtRowDto> rows = suppliers.values().stream()
            .map(bucket -> bucket.toDto(includeDetails))
            .sorted(Comparator.comparing(SupplierDebtRowDto::debtLeft).reversed()
                .thenComparing(SupplierDebtRowDto::supplierName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        rows = markNewRsgeSuppliers(rows, previousSnapshot.isPresent(), previousSupplierKeys);

        BigDecimal purchaseTotal = sum(rows.stream().map(SupplierDebtRowDto::purchaseTotal).toList());
        BigDecimal bogPaidTotal = sum(rows.stream().map(SupplierDebtRowDto::bogPaidTotal).toList());
        BigDecimal tbcPaidTotal = sum(rows.stream().map(SupplierDebtRowDto::tbcPaidTotal).toList());
        BigDecimal cashPaidTotal = sum(rows.stream().map(SupplierDebtRowDto::cashPaidTotal).toList());
        BigDecimal paidTotal = sum(rows.stream().map(SupplierDebtRowDto::paidTotal).toList());
        BigDecimal unmatchedTotal = sum(unmatchedPayments.stream().map(SupplierDebtPaymentDto::amount).toList());

        return new SupplierDebtOverviewDto(
            range.dateFrom(),
            range.dateTo(),
            purchaseTotal,
            bogPaidTotal,
            tbcPaidTotal,
            cashPaidTotal,
            MoneyUtil.round(bogPaidTotal.add(tbcPaidTotal)),
            paidTotal,
            MoneyUtil.round(purchaseTotal.subtract(paidTotal)),
            rows.size(),
            unmatchedTotal,
            unmatchedPayments.size(),
            rows,
            unmatchedPayments.stream()
                .sorted(Comparator.comparing(SupplierDebtPaymentDto::amount).reversed())
                .toList(),
            mappings,
            statuses,
            groupUnmatchedPayments(unmatchedPayments),
            null,
            false,
            null,
            null,
            "",
            null
        );
    }

    private void addBankPayments(
        SourceFetchResult<BankTransaction> source,
        Map<String, SupplierBucket> suppliers,
        List<SupplierPaymentMapping> mappings,
        List<SupplierDebtPaymentDto> unmatchedPayments,
        List<SupplierDebtSourceStatusDto> statuses
    ) {
        if (source.failed()) {
            statuses.add(failed(source.source(), source.errorMessage(), source.errorDetails()));
            return;
        }

        int debitCount = 0;
        BigDecimal debitTotal = BigDecimal.ZERO;
        for (BankTransaction transaction : source.records()) {
            if (!"DEBIT".equals(transaction.direction()) || transaction.amount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            debitCount++;
            debitTotal = debitTotal.add(MoneyUtil.round(transaction.amount()));
            PaymentMatch match = matchPayment(source.source(), transaction, suppliers, mappings);
            SupplierDebtPaymentDto payment = toPaymentDto(source.source(), transaction, match.reason());
            if (match.bucket() == null) {
                unmatchedPayments.add(payment);
            } else {
                match.bucket().addPayment(payment);
            }
        }
        statuses.add(success(source.source(), debitCount, debitTotal, source.cached()));
    }

    private void addCashPayments(
        LocalDate dateFrom,
        LocalDate dateTo,
        Map<String, SupplierBucket> suppliers,
        List<SupplierDebtSourceStatusDto> statuses
    ) {
        List<SupplierCashPayment> cashPayments = configStore.getSupplierCashPayments(dateFrom, dateTo);
        BigDecimal cashTotal = BigDecimal.ZERO;
        for (SupplierCashPayment cashPayment : cashPayments) {
            SupplierIdentity identity = new SupplierIdentity(
                cashPayment.getSupplierKey(),
                normalizeTin(cashPayment.getSupplierTin()),
                blankTo(cashPayment.getSupplierName(), cashPayment.getSupplierKey())
            );
            SupplierBucket bucket = suppliers.computeIfAbsent(identity.key(), ignored -> new SupplierBucket(identity));
            BigDecimal amount = MoneyUtil.round(cashPayment.getAmount());
            cashTotal = cashTotal.add(amount);
            bucket.addPayment(new SupplierDebtPaymentDto(
                cashPayment.getId(),
                cashPayment.getDate(),
                amount,
                CASH,
                cashPayment.getSupplierName(),
                normalizeTin(cashPayment.getSupplierTin()),
                "",
                cashPayment.getNote(),
                cashPayment.getId(),
                "manual cash payment"
            ));
        }
        statuses.add(new SupplierDebtSourceStatusDto(CASH, "OK", "local manual ledger", "", cashPayments.size(), MoneyUtil.round(cashTotal)));
    }

    private List<SupplierDebtUnmatchedGroupDto> groupUnmatchedPayments(List<SupplierDebtPaymentDto> unmatchedPayments) {
        Map<String, UnmatchedGroupBucket> buckets = new LinkedHashMap<>();
        for (SupplierDebtPaymentDto payment : unmatchedPayments) {
            MatchIdentifier identifier = bestIdentifier(payment);
            String groupKey = payment.provider() + "|" + identifier.type() + "|" + ConfigStore.normalizeSalesKey(identifier.value());
            buckets.computeIfAbsent(groupKey, ignored -> new UnmatchedGroupBucket(groupKey, identifier, payment))
                .add(payment);
        }
        return buckets.values().stream()
            .map(UnmatchedGroupBucket::toDto)
            .sorted(Comparator.comparing(SupplierDebtUnmatchedGroupDto::amount).reversed())
            .toList();
    }

    private MatchIdentifier bestIdentifier(SupplierDebtPaymentDto payment) {
        String tin = normalizeTin(payment.counterpartyInn());
        if (!tin.isBlank()) {
            return new MatchIdentifier("TIN", tin);
        }
        if (!safe(payment.counterpartyAccount()).isBlank()) {
            return new MatchIdentifier("ACCOUNT", payment.counterpartyAccount().trim());
        }
        if (!safe(payment.counterparty()).isBlank()) {
            return new MatchIdentifier("COUNTERPARTY", payment.counterparty().trim());
        }
        if (!safe(payment.description()).isBlank()) {
            return new MatchIdentifier("DESCRIPTION", payment.description().trim());
        }
        return new MatchIdentifier("REFERENCE", safe(payment.reference()).trim());
    }

    private List<RsgePurchaseFingerprint> purchaseFingerprints(List<RsgeRecord> records) {
        Map<String, Integer> occurrenceCounts = new HashMap<>();
        List<RsgePurchaseFingerprint> fingerprints = new ArrayList<>();
        for (RsgeRecord record : records) {
            String baseKey = rsgeBaseRowKey(record);
            int occurrence = occurrenceCounts.merge(baseKey, 1, Integer::sum);
            String rowKey = baseKey + "#" + occurrence;
            SupplierIdentity identity = supplierIdentity(record);
            fingerprints.add(new RsgePurchaseFingerprint(
                rowKey,
                contentHash(record),
                safe(record.waybillNumber()),
                identity.tin(),
                identity.name(),
                record.recordDate() == null ? null : record.recordDate().toLocalDate(),
                MoneyUtil.round(record.totalPrice())
            ));
        }
        return fingerprints;
    }

    private String rsgeBaseRowKey(RsgeRecord record) {
        SupplierIdentity identity = supplierIdentity(record);
        return String.join("|",
            "rsge",
            normalizeKey(record.waybillNumber()),
            normalizeKey(identity.tin()),
            record.recordDate() == null ? "" : record.recordDate().toLocalDate().toString(),
            normalizeKey(record.productName()),
            normalizeKey(record.unitOfMeasure())
        );
    }

    private String contentHash(RsgeRecord record) {
        String payload = String.join("|",
            safe(record.waybillNumber()),
            safe(record.supplierRaw()),
            safe(record.supplierTin()),
            safe(record.supplierName()),
            safe(record.productName()),
            safe(record.unitOfMeasure()),
            decimalText(record.quantity()),
            decimalText(record.unitPrice()),
            decimalText(record.totalPrice()),
            record.recordDate() == null ? "" : record.recordDate().toString()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String decimalText(BigDecimal value) {
        return value == null ? "" : MoneyUtil.round(value).stripTrailingZeros().toPlainString();
    }

    private String normalizeKey(String value) {
        return ConfigStore.normalizeSalesKey(safe(value));
    }

    private <T> Cache<RangeKey, List<T>> sourceCache(long cacheTtlMinutes, long maximumCacheSize) {
        return Caffeine.newBuilder()
            .maximumSize(maximumCacheSize)
            .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
            .build();
    }

    private <T> SourceFetchResult<T> fetchSource(
        String source,
        Cache<RangeKey, List<T>> cache,
        RangeKey range,
        Supplier<List<T>> loader
    ) {
        List<T> cached = cache.getIfPresent(range);
        if (cached != null) {
            return new SourceFetchResult<>(source, cached, true, "", "");
        }
        try {
            List<T> records = List.copyOf(loader.get());
            cache.put(range, records);
            return new SourceFetchResult<>(source, records, false, "", "");
        } catch (RuntimeException exception) {
            return new SourceFetchResult<>(source, List.of(), false, exceptionSummary(exception), stackTrace(exception));
        }
    }

    private void invalidateSourceCaches(RangeKey range) {
        purchaseCache.invalidate(range);
        bogTransactionCache.invalidate(range);
        tbcTransactionCache.invalidate(range);
    }

    private PaymentMatch matchPayment(
        String provider,
        BankTransaction transaction,
        Map<String, SupplierBucket> suppliers,
        List<SupplierPaymentMapping> mappings
    ) {
        String normalizedInn = normalizeTin(transaction.counterpartyInn());
        if (!normalizedInn.isBlank()) {
            SupplierBucket byTin = suppliers.get("tin:" + normalizedInn);
            if (byTin != null) {
                return new PaymentMatch(byTin, "matched supplier TIN");
            }
        }

        String searchText = ConfigStore.normalizeSalesKey(String.join(" ",
            safe(transaction.counterparty()),
            safe(transaction.counterpartyInn()),
            safe(transaction.counterpartyAccount()),
            safe(transaction.description()),
            safe(transaction.reference())
        ));
        for (SupplierPaymentMapping mapping : mappings) {
            if (!provider.equalsIgnoreCase(mapping.getProvider())) {
                continue;
            }
            String mappingTaxCode = normalizeTin(mapping.getMatchText());
            if (!normalizedInn.isBlank() && !mappingTaxCode.isBlank() && !normalizedInn.equals(mappingTaxCode)) {
                continue;
            }
            if (!normalizedInn.isBlank() && !mappingTaxCode.isBlank()) {
                SupplierBucket bucket = suppliers.computeIfAbsent(mapping.getSupplierKey(), ignored ->
                    new SupplierBucket(new SupplierIdentity(
                        mapping.getSupplierKey(),
                        normalizeTin(mapping.getSupplierTin()),
                        blankTo(mapping.getSupplierName(), mapping.getSupplierKey())
                    ))
                );
                return new PaymentMatch(bucket, "matched saved tax-code mapping");
            }
            if (!normalizedInn.isBlank()) {
                continue;
            }
            if (!mapping.getNormalizedMatchText().isBlank()
                && searchText.contains(mapping.getNormalizedMatchText())) {
                SupplierBucket bucket = suppliers.computeIfAbsent(mapping.getSupplierKey(), ignored ->
                    new SupplierBucket(new SupplierIdentity(
                        mapping.getSupplierKey(),
                        normalizeTin(mapping.getSupplierTin()),
                        blankTo(mapping.getSupplierName(), mapping.getSupplierKey())
                    ))
                );
                return new PaymentMatch(bucket, "matched saved mapping");
            }
        }
        return new PaymentMatch(null, "unmatched");
    }

    private SupplierDebtPaymentDto toPaymentDto(String provider, BankTransaction transaction, String matchReason) {
        return new SupplierDebtPaymentDto(
            transaction.reference(),
            transaction.date(),
            MoneyUtil.round(transaction.amount()),
            provider,
            transaction.counterparty(),
            normalizeTin(transaction.counterpartyInn()),
            transaction.counterpartyAccount(),
            transaction.description(),
            transaction.reference(),
            matchReason
        );
    }

    private SupplierIdentity supplierIdentity(RsgeRecord record) {
        SupplierIdentity rawIdentity = supplierIdentity(record.supplierRaw());
        String sellerTin = normalizeTin(record.supplierTin());
        if (sellerTin.isBlank()) {
            return rawIdentity;
        }
        String sellerName = blankTo(record.supplierName(), rawIdentity.name());
        return new SupplierIdentity("tin:" + sellerTin, sellerTin, sellerName);
    }

    private SupplierIdentity supplierIdentity(String rawSupplier) {
        String raw = safe(rawSupplier).trim();
        Matcher matcher = RAW_SUPPLIER_PATTERN.matcher(raw);
        if (matcher.matches()) {
            String tin = normalizeTin(matcher.group(1));
            return new SupplierIdentity("tin:" + tin, tin, matcher.group(2).trim());
        }
        String name = raw.isBlank() ? "Unknown Supplier" : raw;
        return new SupplierIdentity("name:" + ConfigStore.normalizeSalesKey(name), "", name);
    }

    private SupplierDebtAuditSupplierDto auditSupplier(SupplierDebtRowDto snapshotRow, SupplierDebtRowDto freshRow) {
        SupplierDebtRowDto effectiveFresh = freshRow == null ? emptySupplierRow(snapshotRow.supplierKey()) : freshRow;
        BigDecimal debtDifference = MoneyUtil.round(snapshotRow.debtLeft().subtract(effectiveFresh.debtLeft()));
        boolean passed = same(snapshotRow.purchaseTotal(), effectiveFresh.purchaseTotal())
            && same(snapshotRow.bogPaidTotal(), effectiveFresh.bogPaidTotal())
            && same(snapshotRow.tbcPaidTotal(), effectiveFresh.tbcPaidTotal())
            && same(snapshotRow.cashPaidTotal(), effectiveFresh.cashPaidTotal())
            && same(snapshotRow.debtLeft(), effectiveFresh.debtLeft());
        return new SupplierDebtAuditSupplierDto(
            snapshotRow.supplierKey(),
            snapshotRow.supplierTin(),
            snapshotRow.supplierName(),
            passed,
            snapshotRow.purchaseTotal(),
            effectiveFresh.purchaseTotal(),
            snapshotRow.bogPaidTotal(),
            effectiveFresh.bogPaidTotal(),
            snapshotRow.tbcPaidTotal(),
            effectiveFresh.tbcPaidTotal(),
            snapshotRow.cashPaidTotal(),
            effectiveFresh.cashPaidTotal(),
            snapshotRow.debtLeft(),
            effectiveFresh.debtLeft(),
            debtDifference
        );
    }

    private boolean same(BigDecimal left, BigDecimal right) {
        return MoneyUtil.round(left).compareTo(MoneyUtil.round(right)) == 0;
    }

    private SupplierDebtRowDto emptySupplierRow(String supplierKey) {
        return new SupplierDebtRowDto(
            supplierKey,
            "",
            supplierKey,
            BigDecimal.ZERO,
            0,
            BigDecimal.ZERO,
            0,
            BigDecimal.ZERO,
            0,
            BigDecimal.ZERO,
            0,
            BigDecimal.ZERO,
            0,
            BigDecimal.ZERO,
            null,
            null,
            false,
            List.of(),
            List.of()
        );
    }

    private boolean canServeSavedSnapshot(SupplierDebtOverviewDto snapshot, RangeKey range) {
        if (snapshot.dateFrom() == null || snapshot.dateTo() == null) {
            return false;
        }
        if (!range.dateFrom().equals(snapshot.dateFrom())) {
            return false;
        }
        if (range.dateTo().equals(snapshot.dateTo())) {
            return true;
        }
        return range.dateTo().equals(LocalDate.now()) && !snapshot.dateTo().isAfter(range.dateTo());
    }

    private boolean shouldStartBackgroundRefresh(SupplierDebtOverviewDto snapshot, RangeKey range) {
        if (refreshInProgress.get()) {
            return false;
        }
        if (range.dateTo().equals(LocalDate.now()) && snapshot.dateTo().isBefore(range.dateTo())) {
            return true;
        }
        if (snapshot.snapshotGeneratedAt() == null) {
            return true;
        }
        long freshnessMinutes = Math.max(1, properties.getSupplierDebt().getSourceCacheTtlMinutes());
        Duration snapshotAge = Duration.between(snapshot.snapshotGeneratedAt(), LocalDateTime.now());
        return snapshotAge.toMinutes() >= freshnessMinutes;
    }

    private RangeKey normalizeRange(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate effectiveDateFrom = dateFrom == null ? defaultDateFrom() : dateFrom;
        LocalDate effectiveDateTo = dateTo == null ? LocalDate.now() : dateTo;
        if (effectiveDateTo.isBefore(effectiveDateFrom)) {
            throw new IllegalArgumentException("dateTo must be on or after dateFrom");
        }
        return new RangeKey(effectiveDateFrom, effectiveDateTo);
    }

    private SupplierDebtAuditDto latestAuditFromSnapshot() {
        return snapshotStore.load().map(SupplierDebtOverviewDto::latestAudit).orElse(null);
    }

    private List<SupplierDebtRowDto> markNewRsgeSuppliers(
        List<SupplierDebtRowDto> rows,
        boolean hasPreviousSnapshot,
        Set<String> previousSupplierKeys
    ) {
        if (!hasPreviousSnapshot) {
            return rows;
        }
        return rows.stream()
            .map(row -> withNewFromRsge(row, row.purchaseCount() > 0 && !previousSupplierKeys.contains(row.supplierKey())))
            .toList();
    }

    private SupplierDebtRowDto withNewFromRsge(SupplierDebtRowDto row, boolean newFromRsge) {
        return new SupplierDebtRowDto(
            row.supplierKey(),
            row.supplierTin(),
            row.supplierName(),
            row.purchaseTotal(),
            row.purchaseCount(),
            row.bogPaidTotal(),
            row.bogPaymentCount(),
            row.tbcPaidTotal(),
            row.tbcPaymentCount(),
            row.cashPaidTotal(),
            row.cashPaymentCount(),
            row.paidTotal(),
            row.paymentCount(),
            row.debtLeft(),
            row.lastPurchaseDate(),
            row.lastPaymentDate(),
            newFromRsge,
            row.purchases(),
            row.payments()
        );
    }

    private SupplierDebtOverviewDto withRefreshMetadata(SupplierDebtOverviewDto overview, boolean inProgress) {
        return withSnapshotMetadata(
            overview,
            overview.snapshotGeneratedAt(),
            inProgress,
            lastRefreshStartedAt == null ? overview.lastRefreshStartedAt() : lastRefreshStartedAt,
            lastRefreshCompletedAt == null ? overview.lastRefreshCompletedAt() : lastRefreshCompletedAt,
            lastRefreshError == null || lastRefreshError.isBlank() ? overview.lastRefreshError() : lastRefreshError,
            overview.latestAudit()
        );
    }

    private SupplierDebtOverviewDto withRefreshError(
        SupplierDebtOverviewDto overview,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String error
    ) {
        return withSnapshotMetadata(
            overview,
            overview.snapshotGeneratedAt(),
            false,
            startedAt,
            completedAt,
            error,
            overview.latestAudit()
        );
    }

    private SupplierDebtOverviewDto withAudit(SupplierDebtOverviewDto overview, SupplierDebtAuditDto audit) {
        return withSnapshotMetadata(
            overview,
            overview.snapshotGeneratedAt(),
            false,
            overview.lastRefreshStartedAt(),
            overview.lastRefreshCompletedAt(),
            overview.lastRefreshError(),
            audit
        );
    }

    private SupplierDebtOverviewDto withSnapshotMetadata(
        SupplierDebtOverviewDto overview,
        LocalDateTime snapshotGeneratedAt,
        boolean refreshInProgress,
        LocalDateTime lastRefreshStartedAt,
        LocalDateTime lastRefreshCompletedAt,
        String lastRefreshError,
        SupplierDebtAuditDto latestAudit
    ) {
        return new SupplierDebtOverviewDto(
            overview.dateFrom(),
            overview.dateTo(),
            overview.purchaseTotal(),
            overview.bogPaidTotal(),
            overview.tbcPaidTotal(),
            overview.cashPaidTotal(),
            overview.bankPaidTotal(),
            overview.paidTotal(),
            overview.debtTotal(),
            overview.supplierCount(),
            overview.unmatchedPaymentTotal(),
            overview.unmatchedPaymentCount(),
            overview.suppliers(),
            overview.unmatchedPayments(),
            overview.mappings(),
            overview.sourceStatuses(),
            overview.unmatchedPaymentGroups() == null ? List.of() : overview.unmatchedPaymentGroups(),
            snapshotGeneratedAt,
            refreshInProgress,
            lastRefreshStartedAt,
            lastRefreshCompletedAt,
            lastRefreshError == null ? "" : lastRefreshError,
            latestAudit
        );
    }

    private SupplierDebtSourceStatusDto success(String source, int recordCount, BigDecimal total, boolean cached) {
        return new SupplierDebtSourceStatusDto(
            source,
            "OK",
            cached ? "cached source data" : "fresh source data",
            "",
            recordCount,
            MoneyUtil.round(total)
        );
    }

    private SupplierDebtSourceStatusDto rsgeStatus(
        int recordCount,
        BigDecimal total,
        boolean cached,
        RsgePurchaseChangeSummary changeSummary
    ) {
        String sourceState = cached ? "cached source data" : "fresh source data";
        if (changeSummary == null || !changeSummary.hasAnyChange()) {
            return new SupplierDebtSourceStatusDto(RSGE, "OK", sourceState + "; no RS.ge row changes", "", recordCount, MoneyUtil.round(total));
        }
        String message = sourceState + "; " + changeSummary.shortMessage();
        return new SupplierDebtSourceStatusDto(
            RSGE,
            changeSummary.hasRisk() ? "WARNING" : "OK",
            message,
            changeSummary.technicalDetails(),
            recordCount,
            MoneyUtil.round(total)
        );
    }

    private SupplierDebtSourceStatusDto failed(String source, String message, String technicalDetails) {
        return new SupplierDebtSourceStatusDto(
            source,
            "FAILED",
            message == null ? "" : message,
            technicalDetails == null ? "" : technicalDetails,
            0,
            BigDecimal.ZERO
        );
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return MoneyUtil.round(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private String normalizeTin(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("[^\\d]", "");
        return normalized.isBlank() ? "" : normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String exceptionSummary(RuntimeException exception) {
        String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private String stackTrace(RuntimeException exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private record SupplierIdentity(String key, String tin, String name) {
    }

    private record PaymentMatch(SupplierBucket bucket, String reason) {
    }

    private record RangeKey(LocalDate dateFrom, LocalDate dateTo) {
    }

    private record SourceFetchResult<T>(
        String source,
        List<T> records,
        boolean cached,
        String errorMessage,
        String errorDetails
    ) {
        private boolean failed() {
            return errorMessage != null && !errorMessage.isBlank();
        }
    }

    private record MatchIdentifier(String type, String value) {
    }

    private static class SupplierBucket {
        private final SupplierIdentity identity;
        private final List<SupplierDebtPurchaseDto> purchases = new ArrayList<>();
        private final List<SupplierDebtPaymentDto> payments = new ArrayList<>();
        private BigDecimal purchaseTotal = BigDecimal.ZERO;
        private BigDecimal bogPaidTotal = BigDecimal.ZERO;
        private BigDecimal tbcPaidTotal = BigDecimal.ZERO;
        private BigDecimal cashPaidTotal = BigDecimal.ZERO;
        private int bogPaymentCount;
        private int tbcPaymentCount;
        private int cashPaymentCount;

        private SupplierBucket(SupplierIdentity identity) {
            this.identity = identity;
        }

        private void addPurchase(SupplierDebtPurchaseDto purchase) {
            purchases.add(purchase);
            purchaseTotal = purchaseTotal.add(purchase.amount());
        }

        private void addPayment(SupplierDebtPaymentDto payment) {
            payments.add(payment);
            if (BOG.equals(payment.provider())) {
                bogPaidTotal = bogPaidTotal.add(payment.amount());
                bogPaymentCount++;
            } else if (TBC.equals(payment.provider())) {
                tbcPaidTotal = tbcPaidTotal.add(payment.amount());
                tbcPaymentCount++;
            } else if (CASH.equals(payment.provider())) {
                cashPaidTotal = cashPaidTotal.add(payment.amount());
                cashPaymentCount++;
            }
        }

        private SupplierDebtRowDto toDto(boolean includeDetails) {
            LocalDate lastPurchaseDate = purchases.stream()
                .map(SupplierDebtPurchaseDto::date)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(null);
            LocalDate lastPaymentDate = payments.stream()
                .map(SupplierDebtPaymentDto::date)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(null);
            BigDecimal paidTotal = MoneyUtil.round(bogPaidTotal.add(tbcPaidTotal).add(cashPaidTotal));
            return new SupplierDebtRowDto(
                identity.key(),
                identity.tin(),
                identity.name(),
                MoneyUtil.round(purchaseTotal),
                purchases.size(),
                MoneyUtil.round(bogPaidTotal),
                bogPaymentCount,
                MoneyUtil.round(tbcPaidTotal),
                tbcPaymentCount,
                MoneyUtil.round(cashPaidTotal),
                cashPaymentCount,
                paidTotal,
                payments.size(),
                MoneyUtil.round(purchaseTotal.subtract(paidTotal)),
                lastPurchaseDate,
                lastPaymentDate,
                false,
                includeDetails
                    ? purchases.stream()
                        .sorted(Comparator.comparing(SupplierDebtPurchaseDto::date, Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList()
                    : List.of(),
                includeDetails
                    ? payments.stream()
                        .sorted(Comparator.comparing(SupplierDebtPaymentDto::date, Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList()
                    : List.of()
            );
        }
    }

    private static class UnmatchedGroupBucket {
        private final String groupKey;
        private final MatchIdentifier identifier;
        private final SupplierDebtPaymentDto firstPayment;
        private final List<SupplierDebtPaymentDto> examples = new ArrayList<>();
        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal largestTransaction = BigDecimal.ZERO;
        private int transactionCount;

        private UnmatchedGroupBucket(String groupKey, MatchIdentifier identifier, SupplierDebtPaymentDto firstPayment) {
            this.groupKey = groupKey;
            this.identifier = identifier;
            this.firstPayment = firstPayment;
        }

        private void add(SupplierDebtPaymentDto payment) {
            amount = amount.add(payment.amount());
            if (payment.amount().compareTo(largestTransaction) > 0) {
                largestTransaction = payment.amount();
            }
            transactionCount++;
            if (examples.size() < UNMATCHED_GROUP_EXAMPLE_LIMIT) {
                examples.add(payment);
            }
        }

        private SupplierDebtUnmatchedGroupDto toDto() {
            return new SupplierDebtUnmatchedGroupDto(
                groupKey,
                firstPayment.provider(),
                identifier.value(),
                identifier.type(),
                firstPayment.counterparty(),
                firstPayment.counterpartyInn(),
                firstPayment.counterpartyAccount(),
                firstPayment.description(),
                MoneyUtil.round(amount),
                transactionCount,
                MoneyUtil.round(largestTransaction),
                examples
            );
        }
    }
}
