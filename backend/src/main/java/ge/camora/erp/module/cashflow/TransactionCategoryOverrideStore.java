package ge.camora.erp.module.cashflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * JSON-backed store of per-transaction category overrides ("Apply to This Only").
 * Keyed by the transaction fingerprint from {@link CashFlowFingerprint}; because
 * the resolver checks overrides before any global rule, an override bypasses the
 * rule dictionary for exactly that transaction. Single file, one map, atomic write
 * (mirrors RsgePurchaseLedgerStore's map-in-a-file approach).
 */
@Service
public class TransactionCategoryOverrideStore {

    private static final Logger log = LoggerFactory.getLogger(TransactionCategoryOverrideStore.class);
    private static final String FILE_NAME = "cash-flow-overrides.json";

    private final ObjectMapper objectMapper;
    private final CamoraProperties properties;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Path storePath;

    public TransactionCategoryOverrideStore(ObjectMapper objectMapper, CamoraProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        Path configDir = Path.of(properties.getConfigDir());
        storePath = configDir.resolve(FILE_NAME);
        try {
            Files.createDirectories(configDir);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create config dir: " + configDir, exception);
        }
    }

    /** Snapshot of fingerprint -> categoryId for fast resolution while building a matrix. */
    public Map<String, String> categoryByFingerprint() {
        lock.readLock().lock();
        try {
            Map<String, String> result = new LinkedHashMap<>();
            load().entries().forEach((fingerprint, entry) -> result.put(fingerprint, entry.categoryId()));
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<String> categoryFor(String fingerprint) {
        lock.readLock().lock();
        try {
            OverrideEntry entry = load().entries().get(fingerprint);
            return entry == null ? Optional.empty() : Optional.of(entry.categoryId());
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(String fingerprint, String categoryId) {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("Transaction fingerprint is required");
        }
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("Override category is required");
        }
        lock.writeLock().lock();
        try {
            OverrideFile file = load();
            Map<String, OverrideEntry> entries = new LinkedHashMap<>(file.entries());
            LocalDateTime now = LocalDateTime.now();
            OverrideEntry existing = entries.get(fingerprint);
            entries.put(fingerprint, new OverrideEntry(
                categoryId,
                existing == null ? now : existing.createdAt(),
                now
            ));
            write(new OverrideFile(1, entries));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return;
        }
        lock.writeLock().lock();
        try {
            OverrideFile file = load();
            if (!file.entries().containsKey(fingerprint)) {
                return;
            }
            Map<String, OverrideEntry> entries = new LinkedHashMap<>(file.entries());
            entries.remove(fingerprint);
            write(new OverrideFile(file.schemaVersion(), entries));
        } finally {
            lock.writeLock().unlock();
        }
    }

    private OverrideFile load() {
        if (storePath == null || !Files.exists(storePath)) {
            return new OverrideFile(1, new LinkedHashMap<>());
        }
        try {
            OverrideFile file = objectMapper.readValue(storePath.toFile(), OverrideFile.class);
            return file == null || file.entries() == null ? new OverrideFile(1, new LinkedHashMap<>()) : file;
        } catch (IOException exception) {
            quarantineCorrupt(storePath, exception);
            return new OverrideFile(1, new LinkedHashMap<>());
        }
    }

    private void write(OverrideFile file) {
        try {
            Path tempFile = Files.createTempFile(storePath.getParent(), FILE_NAME, ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), file);
                Files.move(tempFile, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException innerException) {
                Files.deleteIfExists(tempFile);
                throw innerException;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist cash-flow overrides: " + storePath, exception);
        }
    }

    private void quarantineCorrupt(Path path, IOException exception) {
        Path quarantined = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(path, quarantined, StandardCopyOption.REPLACE_EXISTING);
            log.warn("Could not load {} ({}); moved to {} and starting fresh", path, exception.getMessage(), quarantined);
        } catch (IOException moveException) {
            log.warn("Could not load or quarantine {}: {}", path, exception.getMessage());
        }
    }

    public record OverrideFile(int schemaVersion, Map<String, OverrideEntry> entries) {
    }

    public record OverrideEntry(String categoryId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
