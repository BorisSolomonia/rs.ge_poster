package ge.camora.erp.module.supplierdebt;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class RsgePurchaseLedgerStore {

    private static final Logger log = LoggerFactory.getLogger(RsgePurchaseLedgerStore.class);
    private static final String ACTIVE = "ACTIVE";
    private static final String MISSING_FROM_SOURCE = "MISSING_FROM_SOURCE";
    private static final String DEFAULT_FILE_NAME = "rsge-purchase-ledger.json";
    private static final int EXAMPLE_LIMIT = 8;

    private final ObjectMapper objectMapper;
    private final CamoraProperties properties;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Path ledgerPath;

    public RsgePurchaseLedgerStore(ObjectMapper objectMapper, CamoraProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        Path configDir = Path.of(properties.getConfigDir());
        String configured = properties.getConfigFiles().getRsgePurchaseLedger();
        ledgerPath = configDir.resolve(configured == null || configured.isBlank() ? DEFAULT_FILE_NAME : configured);
        try {
            Files.createDirectories(configDir);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create config dir: " + configDir, exception);
        }
    }

    public RsgePurchaseChangeSummary compareAndSave(
        LocalDate dateFrom,
        LocalDate dateTo,
        List<RsgePurchaseFingerprint> currentFingerprints
    ) {
        lock.writeLock().lock();
        try {
            LedgerFile ledger = load();
            LocalDateTime now = LocalDateTime.now();
            Map<String, LedgerEntry> entries = new LinkedHashMap<>(ledger.entries());
            Set<String> currentKeys = new HashSet<>();
            List<String> examples = new ArrayList<>();
            int newCount = 0;
            int changedCount = 0;
            int restoredCount = 0;
            int unchangedCount = 0;

            for (RsgePurchaseFingerprint fingerprint : currentFingerprints) {
                currentKeys.add(fingerprint.rowKey());
                LedgerEntry existing = entries.get(fingerprint.rowKey());
                if (existing == null) {
                    newCount++;
                    addExample(examples, "NEW", fingerprint);
                    entries.put(fingerprint.rowKey(), entry(fingerprint, ACTIVE, now, now, now, null));
                    continue;
                }
                if (!fingerprint.contentHash().equals(existing.contentHash())) {
                    changedCount++;
                    addExample(examples, "CHANGED", fingerprint);
                    entries.put(
                        fingerprint.rowKey(),
                        entry(fingerprint, ACTIVE, existing.firstSeenAt(), now, now, existing.contentHash())
                    );
                    continue;
                }
                if (MISSING_FROM_SOURCE.equals(existing.status())) {
                    restoredCount++;
                    addExample(examples, "RESTORED", fingerprint);
                    entries.put(fingerprint.rowKey(), entry(fingerprint, ACTIVE, existing.firstSeenAt(), now, now, existing.previousHash()));
                    continue;
                }
                unchangedCount++;
                entries.put(fingerprint.rowKey(), entry(fingerprint, ACTIVE, existing.firstSeenAt(), now, existing.changedAt(), existing.previousHash()));
            }

            int missingCount = 0;
            for (Map.Entry<String, LedgerEntry> entry : new ArrayList<>(entries.entrySet())) {
                LedgerEntry stored = entry.getValue();
                if (!ACTIVE.equals(stored.status()) || !inRange(stored.date(), dateFrom, dateTo) || currentKeys.contains(entry.getKey())) {
                    continue;
                }
                missingCount++;
                addExample(examples, "MISSING", stored);
                entries.put(
                    entry.getKey(),
                    new LedgerEntry(
                        stored.rowKey(),
                        stored.contentHash(),
                        MISSING_FROM_SOURCE,
                        stored.date(),
                        stored.waybillNumber(),
                        stored.supplierTin(),
                        stored.supplierName(),
                        stored.amount(),
                        stored.firstSeenAt(),
                        now,
                        stored.changedAt(),
                        stored.previousHash()
                    )
                );
            }

            save(new LedgerFile(1, now, entries));
            return new RsgePurchaseChangeSummary(
                newCount,
                changedCount,
                missingCount,
                restoredCount,
                unchangedCount,
                now,
                List.copyOf(examples)
            );
        } finally {
            lock.writeLock().unlock();
        }
    }

    private LedgerFile load() {
        if (ledgerPath == null || !Files.exists(ledgerPath)) {
            return new LedgerFile(1, null, Map.of());
        }
        try {
            LedgerFile file = objectMapper.readValue(ledgerPath.toFile(), LedgerFile.class);
            return file.entries() == null
                ? new LedgerFile(1, file.updatedAt(), Map.of())
                : file;
        } catch (IOException exception) {
            log.warn("Could not load RS.ge purchase ledger {}: {}", ledgerPath, exception.getMessage());
            return new LedgerFile(1, null, Map.of());
        }
    }

    private void save(LedgerFile ledger) {
        try {
            Path parent = ledgerPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, ledgerPath.getFileName().toString(), ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), ledger);
                Files.move(tempFile, ledgerPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException innerException) {
                Files.deleteIfExists(tempFile);
                throw innerException;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist RS.ge purchase ledger: " + ledgerPath, exception);
        }
    }

    private LedgerEntry entry(
        RsgePurchaseFingerprint fingerprint,
        String status,
        LocalDateTime firstSeenAt,
        LocalDateTime lastSeenAt,
        LocalDateTime changedAt,
        String previousHash
    ) {
        return new LedgerEntry(
            fingerprint.rowKey(),
            fingerprint.contentHash(),
            status,
            fingerprint.date(),
            fingerprint.waybillNumber(),
            fingerprint.supplierTin(),
            fingerprint.supplierName(),
            fingerprint.amount(),
            firstSeenAt,
            lastSeenAt,
            changedAt,
            previousHash
        );
    }

    private boolean inRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        return date != null && !date.isBefore(dateFrom) && !date.isAfter(dateTo);
    }

    private void addExample(List<String> examples, String action, RsgePurchaseFingerprint fingerprint) {
        if (examples.size() >= EXAMPLE_LIMIT) {
            return;
        }
        examples.add(action + " " + describe(
            fingerprint.date(),
            fingerprint.waybillNumber(),
            fingerprint.supplierTin(),
            fingerprint.supplierName(),
            fingerprint.amount()
        ));
    }

    private void addExample(List<String> examples, String action, LedgerEntry entry) {
        if (examples.size() >= EXAMPLE_LIMIT) {
            return;
        }
        examples.add(action + " " + describe(
            entry.date(),
            entry.waybillNumber(),
            entry.supplierTin(),
            entry.supplierName(),
            entry.amount()
        ));
    }

    private String describe(LocalDate date, String waybillNumber, String supplierTin, String supplierName, BigDecimal amount) {
        return "date=" + (date == null ? "-" : date)
            + ", waybill=" + safe(waybillNumber)
            + ", tin=" + safe(supplierTin)
            + ", supplier=" + safe(supplierName)
            + ", amount=" + (amount == null ? "0" : amount);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record LedgerFile(
        int schemaVersion,
        LocalDateTime updatedAt,
        Map<String, LedgerEntry> entries
    ) {
    }

    public record LedgerEntry(
        String rowKey,
        String contentHash,
        String status,
        LocalDate date,
        String waybillNumber,
        String supplierTin,
        String supplierName,
        BigDecimal amount,
        LocalDateTime firstSeenAt,
        LocalDateTime lastSeenAt,
        LocalDateTime changedAt,
        String previousHash
    ) {
    }
}
