package ge.camora.erp.module.supplierdebt;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.module.bankanalysis.BankTransaction;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Local ledger of already-fetched source data so refreshes only hit the
 * external APIs for a small recent window instead of the full history.
 *
 * Bank statements (TBC/BOG) are append-only, so each sync re-fetches only
 * from the newest stored transaction date minus a small overlap and
 * REPLACES that window (bank rows carry no time component, so a strict
 * "after last saved" watermark would drop later same-day payments; window
 * replacement also needs no dedup keys, which keeps two identical legit
 * payments on the same day intact).
 *
 * RS.ge waybills can change, but never further back than 60 days, so each
 * sync re-fetches and replaces the recheck window (default 62 days) while
 * rows older than the window stay as stored.
 */
@Service
public class SourceLedgerStore {

    private static final Logger log = LoggerFactory.getLogger(SourceLedgerStore.class);
    private static final int SCHEMA_VERSION = 1;
    private static final int DEFAULT_BANK_OVERLAP_DAYS = 3;
    private static final int DEFAULT_RSGE_RECHECK_DAYS = 62;
    private static final String PURCHASE_LEDGER_FILE = "rsge-purchase-records.json";

    private final ObjectMapper objectMapper;
    private final CamoraProperties properties;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private Path configDir;

    public SourceLedgerStore(ObjectMapper objectMapper, CamoraProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        configDir = Path.of(properties.getConfigDir());
        try {
            Files.createDirectories(configDir);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create config dir: " + configDir, exception);
        }
    }

    @FunctionalInterface
    public interface SourceFetcher<T> {
        List<T> fetch(LocalDate from, LocalDate to);
    }

    public List<BankTransaction> syncBank(
        String source,
        LocalDate rangeFrom,
        LocalDate rangeTo,
        SourceFetcher<BankTransaction> fetcher
    ) {
        Path path = configDir.resolve("bank-ledger-" + source.toLowerCase(Locale.ROOT) + ".json");
        ReentrantLock lock = lockFor(path);
        lock.lock();
        try {
            BankLedgerFile ledger = loadBank(path);
            List<BankTransaction> stored = ledger.transactions() == null ? List.of() : ledger.transactions();
            if (freshEnough(ledger.lastSyncedAt(), ledger.coverageFrom(), rangeFrom)) {
                return filterByRange(stored, BankTransaction::date, rangeFrom, rangeTo);
            }
            LocalDate today = LocalDate.now();
            LocalDate fetchTo = rangeTo.isAfter(today) ? rangeTo : today;
            LocalDate fetchFrom = bankFetchFrom(ledger, stored, rangeFrom);

            List<BankTransaction> fetched = fetcher.fetch(fetchFrom, fetchTo);
            List<BankTransaction> merged = mergeWindow(stored, fetched, BankTransaction::date, fetchFrom);

            saveJson(path, new BankLedgerFile(
                SCHEMA_VERSION,
                earliest(ledger.coverageFrom(), rangeFrom),
                LocalDateTime.now(),
                merged
            ));
            log.info("{} bank ledger synced: fetched {}..{} ({} rows), ledger now {} rows",
                source, fetchFrom, fetchTo, fetched.size(), merged.size());
            return filterByRange(merged, BankTransaction::date, rangeFrom, rangeTo);
        } finally {
            lock.unlock();
        }
    }

    public List<RsgeRecord> syncPurchases(
        LocalDate rangeFrom,
        LocalDate rangeTo,
        SourceFetcher<RsgeRecord> fetcher
    ) {
        Path path = configDir.resolve(PURCHASE_LEDGER_FILE);
        ReentrantLock lock = lockFor(path);
        lock.lock();
        try {
            PurchaseLedgerFile ledger = loadPurchases(path);
            List<RsgeRecord> stored = ledger.records() == null ? List.of() : ledger.records();
            if (freshEnough(ledger.lastSyncedAt(), ledger.coverageFrom(), rangeFrom)) {
                return filterByRange(stored, SourceLedgerStore::recordDate, rangeFrom, rangeTo);
            }
            LocalDate today = LocalDate.now();
            LocalDate fetchTo = rangeTo.isAfter(today) ? rangeTo : today;
            LocalDate fetchFrom = purchaseFetchFrom(ledger, stored, rangeFrom, today);

            List<RsgeRecord> fetched = fetcher.fetch(fetchFrom, fetchTo);
            List<RsgeRecord> merged = mergeWindow(stored, fetched, SourceLedgerStore::recordDate, fetchFrom);

            saveJson(path, new PurchaseLedgerFile(
                SCHEMA_VERSION,
                earliest(ledger.coverageFrom(), rangeFrom),
                LocalDateTime.now(),
                merged
            ));
            log.info("RSGE purchase ledger synced: fetched {}..{} ({} rows), ledger now {} rows",
                fetchFrom, fetchTo, fetched.size(), merged.size());
            return filterByRange(merged, SourceLedgerStore::recordDate, rangeFrom, rangeTo);
        } finally {
            lock.unlock();
        }
    }

    // Per-supplier creditor syncs arrive in bursts; a ledger synced this
    // recently is served as-is instead of re-fetching the window N times.
    private boolean freshEnough(LocalDateTime lastSyncedAt, LocalDate coverageFrom, LocalDate rangeFrom) {
        long minIntervalSeconds = properties.getSupplierDebt().getMinSyncIntervalSeconds();
        return minIntervalSeconds > 0
            && lastSyncedAt != null
            && coverageFrom != null
            && !rangeFrom.isBefore(coverageFrom)
            && lastSyncedAt.isAfter(LocalDateTime.now().minusSeconds(minIntervalSeconds));
    }

    private LocalDate bankFetchFrom(BankLedgerFile ledger, List<BankTransaction> stored, LocalDate rangeFrom) {
        if (ledger.coverageFrom() == null || stored.isEmpty() || rangeFrom.isBefore(ledger.coverageFrom())) {
            return rangeFrom;
        }
        int overlapDays = properties.getSupplierDebt().getBankSyncOverlapDays();
        if (overlapDays <= 0) {
            overlapDays = DEFAULT_BANK_OVERLAP_DAYS;
        }
        LocalDate newest = stored.stream()
            .map(BankTransaction::date)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
        if (newest == null) {
            return rangeFrom;
        }
        LocalDate from = newest.minusDays(overlapDays);
        return from.isBefore(ledger.coverageFrom()) ? ledger.coverageFrom() : from;
    }

    private LocalDate purchaseFetchFrom(
        PurchaseLedgerFile ledger,
        List<RsgeRecord> stored,
        LocalDate rangeFrom,
        LocalDate today
    ) {
        if (ledger.coverageFrom() == null || stored.isEmpty() || rangeFrom.isBefore(ledger.coverageFrom())) {
            return rangeFrom;
        }
        int recheckDays = properties.getSupplierDebt().getRsgeRecheckDays();
        if (recheckDays <= 0) {
            recheckDays = DEFAULT_RSGE_RECHECK_DAYS;
        }
        LocalDate recheckFrom = today.minusDays(recheckDays);
        return recheckFrom.isBefore(ledger.coverageFrom()) ? ledger.coverageFrom() : recheckFrom;
    }

    // Rows dated before the fetch window are kept as stored; the window itself
    // is replaced wholesale with the fresh fetch. Rows without a date cannot be
    // placed outside the window, so they are always taken from the fresh fetch.
    private <T> List<T> mergeWindow(List<T> stored, List<T> fetched, Function<T, LocalDate> dateOf, LocalDate fetchFrom) {
        List<T> merged = new ArrayList<>();
        for (T row : stored) {
            LocalDate date = dateOf.apply(row);
            if (date != null && date.isBefore(fetchFrom)) {
                merged.add(row);
            }
        }
        merged.addAll(fetched);
        return merged;
    }

    private <T> List<T> filterByRange(List<T> rows, Function<T, LocalDate> dateOf, LocalDate from, LocalDate to) {
        return rows.stream()
            .filter(row -> {
                LocalDate date = dateOf.apply(row);
                return date != null && !date.isBefore(from) && !date.isAfter(to);
            })
            .toList();
    }

    private static LocalDate recordDate(RsgeRecord record) {
        return record.recordDate() == null ? null : record.recordDate().toLocalDate();
    }

    private LocalDate earliest(LocalDate left, LocalDate right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isBefore(right) ? left : right;
    }

    private ReentrantLock lockFor(Path path) {
        return locks.computeIfAbsent(path.toString(), ignored -> new ReentrantLock());
    }

    private BankLedgerFile loadBank(Path path) {
        if (!Files.exists(path)) {
            return new BankLedgerFile(SCHEMA_VERSION, null, null, List.of());
        }
        try {
            return objectMapper.readValue(path.toFile(), BankLedgerFile.class);
        } catch (IOException exception) {
            quarantineCorrupt(path, exception);
            return new BankLedgerFile(SCHEMA_VERSION, null, null, List.of());
        }
    }

    private PurchaseLedgerFile loadPurchases(Path path) {
        if (!Files.exists(path)) {
            return new PurchaseLedgerFile(SCHEMA_VERSION, null, null, List.of());
        }
        try {
            return objectMapper.readValue(path.toFile(), PurchaseLedgerFile.class);
        } catch (IOException exception) {
            quarantineCorrupt(path, exception);
            return new PurchaseLedgerFile(SCHEMA_VERSION, null, null, List.of());
        }
    }

    // Unlike ConfigStore data, ledgers are rebuildable caches of remote data:
    // a corrupt file is set aside and the next sync performs a full backfill.
    private void quarantineCorrupt(Path path, IOException exception) {
        Path quarantined = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(path, quarantined, StandardCopyOption.REPLACE_EXISTING);
            log.warn("Could not load source ledger {} ({}); moved to {} and starting fresh",
                path, exception.getMessage(), quarantined);
        } catch (IOException moveException) {
            log.warn("Could not load or quarantine source ledger {}: {}", path, exception.getMessage());
        }
    }

    private void saveJson(Path path, Object payload) {
        try {
            Path tempFile = Files.createTempFile(configDir, path.getFileName().toString(), ".tmp");
            try {
                objectMapper.writeValue(tempFile.toFile(), payload);
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException innerException) {
                Files.deleteIfExists(tempFile);
                throw innerException;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist source ledger: " + path, exception);
        }
    }

    public record BankLedgerFile(
        int schemaVersion,
        LocalDate coverageFrom,
        LocalDateTime lastSyncedAt,
        List<BankTransaction> transactions
    ) {
    }

    public record PurchaseLedgerFile(
        int schemaVersion,
        LocalDate coverageFrom,
        LocalDateTime lastSyncedAt,
        List<RsgeRecord> records
    ) {
    }
}
