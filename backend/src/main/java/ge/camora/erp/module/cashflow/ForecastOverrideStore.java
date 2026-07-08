package ge.camora.erp.module.cashflow;

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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * JSON-backed store of manual budget-forecast overrides: management overwriting the
 * algorithm's baseline for a specific (category, period) cell with their own number
 * ("a big invoice lands in March", "rent is fixed"). Keyed by
 * {@code PERIODTYPE|categoryId|periodKey} so a week and a month cell for the same
 * category never collide. Overrides are stored separately and merged over the baseline
 * at read time — the computed forecast is never mutated, so "reset to algorithm" is
 * always possible and recomputation stays honest.
 *
 * <p>Mirrors {@link TransactionCategoryOverrideStore}: single file, one map, atomic
 * temp-file + {@code ATOMIC_MOVE} write, quarantine-on-corrupt load, own lock (no
 * contention with the cash-flow matrix read path).
 */
@Service
public class ForecastOverrideStore {

    private static final Logger log = LoggerFactory.getLogger(ForecastOverrideStore.class);
    private static final String FILE_NAME = "budget-forecast-overrides.json";

    private final ObjectMapper objectMapper;
    private final CamoraProperties properties;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Path storePath;

    public ForecastOverrideStore(ObjectMapper objectMapper, CamoraProperties properties) {
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

    /** Compose the storage key for a forecast cell. */
    public static String key(String periodType, String categoryId, String periodKey) {
        return periodType + "|" + categoryId + "|" + periodKey;
    }

    /** Snapshot of key -> override amount for fast merge while building a forecast. */
    public Map<String, BigDecimal> snapshot() {
        lock.readLock().lock();
        try {
            Map<String, BigDecimal> result = new LinkedHashMap<>();
            load().entries().forEach((key, entry) -> result.put(key, entry.amount()));
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(String periodType, String categoryId, String periodKey, BigDecimal amount) {
        if (periodType == null || periodType.isBlank()
            || categoryId == null || categoryId.isBlank()
            || periodKey == null || periodKey.isBlank()) {
            throw new IllegalArgumentException("periodType, categoryId and periodKey are required");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Override amount is required");
        }
        String compositeKey = key(periodType, categoryId, periodKey);
        lock.writeLock().lock();
        try {
            OverrideFile file = load();
            Map<String, OverrideEntry> entries = new LinkedHashMap<>(file.entries());
            LocalDateTime now = LocalDateTime.now();
            OverrideEntry existing = entries.get(compositeKey);
            entries.put(compositeKey, new OverrideEntry(
                amount.setScale(2, java.math.RoundingMode.HALF_UP),
                existing == null ? now : existing.createdAt(),
                now
            ));
            write(new OverrideFile(1, entries));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(String periodType, String categoryId, String periodKey) {
        String compositeKey = key(periodType, categoryId, periodKey);
        lock.writeLock().lock();
        try {
            OverrideFile file = load();
            if (!file.entries().containsKey(compositeKey)) {
                return;
            }
            Map<String, OverrideEntry> entries = new LinkedHashMap<>(file.entries());
            entries.remove(compositeKey);
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
            throw new IllegalStateException("Failed to persist budget overrides: " + storePath, exception);
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

    public record OverrideEntry(BigDecimal amount, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
