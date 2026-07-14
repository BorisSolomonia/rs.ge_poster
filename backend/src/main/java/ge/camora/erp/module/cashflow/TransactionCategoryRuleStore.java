package ge.camora.erp.module.cashflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.store.ConfigStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * JSON-backed store of global identifier -> category rules (the
 * "Transaction_Mapping_Rules" table). Rule ids are deterministic
 * ({@code matchType:matchValue[:direction]}) so an "Apply to All" cascade upsert
 * is idempotent and never produces duplicate rules.
 */
@Service
public class TransactionCategoryRuleStore {

    private static final Logger log = LoggerFactory.getLogger(TransactionCategoryRuleStore.class);
    private static final String FILE_NAME = "cash-flow-rules.json";

    private final ObjectMapper objectMapper;
    private final CamoraProperties properties;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Path storePath;

    public TransactionCategoryRuleStore(ObjectMapper objectMapper, CamoraProperties properties) {
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

    /** Normalizes a raw identifier value the same way for rule storage and resolution. */
    public static String normalizeValue(CashFlowMatchType matchType, String rawValue) {
        if (matchType == CashFlowMatchType.TAX_ID) {
            return CashFlowFingerprint.normalizeTin(rawValue);
        }
        return ConfigStore.normalizeSalesKey(rawValue);
    }

    public static String ruleId(CashFlowMatchType matchType, String normalizedValue, CashFlowDirection direction) {
        String base = matchType.name() + ":" + normalizedValue;
        return direction == null ? base : base + ":" + direction.name();
    }

    public List<TransactionCategoryRule> list() {
        lock.readLock().lock();
        try {
            return load().stream()
                .sorted(Comparator.comparing((TransactionCategoryRule rule) -> rule.getMatchType().name())
                    .thenComparing(TransactionCategoryRule::getMatchValue, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public TransactionCategoryRule upsert(CashFlowMatchType matchType, String rawValue,
                                          CashFlowDirection direction, String categoryId, String source) {
        if (matchType == null) {
            throw new IllegalArgumentException("Rule match type is required");
        }
        String normalizedValue = normalizeValue(matchType, rawValue);
        if (normalizedValue.isBlank()) {
            throw new IllegalArgumentException("Rule match value is required");
        }
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("Rule category is required");
        }
        String id = ruleId(matchType, normalizedValue, direction);
        lock.writeLock().lock();
        try {
            List<TransactionCategoryRule> rules = load();
            LocalDateTime now = LocalDateTime.now();
            for (TransactionCategoryRule rule : rules) {
                if (rule.getId().equals(id)) {
                    rule.setCategoryId(categoryId);
                    rule.setSource(source);
                    rule.setUpdatedAt(now);
                    write(rules);
                    return rule;
                }
            }
            TransactionCategoryRule created = new TransactionCategoryRule(
                id, matchType, normalizedValue, direction, categoryId, source, now, now);
            rules.add(created);
            write(rules);
            return created;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void delete(String id) {
        lock.writeLock().lock();
        try {
            List<TransactionCategoryRule> rules = load();
            if (!rules.removeIf(rule -> rule.getId().equals(id))) {
                throw new IllegalArgumentException("Cash-flow rule not found: " + id);
            }
            write(rules);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private List<TransactionCategoryRule> load() {
        if (storePath == null || !Files.exists(storePath)) {
            return new ArrayList<>();
        }
        try {
            List<TransactionCategoryRule> loaded = objectMapper.readValue(storePath.toFile(), new TypeReference<>() {});
            return loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
        } catch (IOException exception) {
            quarantineCorrupt(storePath, exception);
            return new ArrayList<>();
        }
    }

    private void write(List<TransactionCategoryRule> rules) {
        try {
            Path tempFile = Files.createTempFile(storePath.getParent(), FILE_NAME, ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), rules);
                Files.move(tempFile, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException innerException) {
                Files.deleteIfExists(tempFile);
                throw innerException;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist cash-flow rules: " + storePath, exception);
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
}
