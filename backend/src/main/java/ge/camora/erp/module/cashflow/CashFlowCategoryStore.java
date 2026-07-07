package ge.camora.erp.module.cashflow;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * JSON-backed store of the editable cash-flow category tree. Seeds the Georgian
 * default tree on first run (empty file), then serves CRUD. Follows the standalone
 * store pattern (atomic temp-file + move, own lock) used by SupplierCreditorStore.
 */
@Service
public class CashFlowCategoryStore {

    private static final Logger log = LoggerFactory.getLogger(CashFlowCategoryStore.class);
    private static final String FILE_NAME = "cash-flow-categories.json";

    private final ObjectMapper objectMapper;
    private final CamoraProperties properties;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Path storePath;

    public CashFlowCategoryStore(ObjectMapper objectMapper, CamoraProperties properties) {
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
        lock.writeLock().lock();
        try {
            if (load().isEmpty()) {
                List<CashFlowCategory> seeded = CashFlowCategoryDefaults.list(LocalDateTime.now());
                write(seeded);
                log.info("Seeded {} default cash-flow categories", seeded.size());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<CashFlowCategory> list() {
        lock.readLock().lock();
        try {
            return load().stream()
                .sorted(Comparator.comparingInt(CashFlowCategory::getOrder)
                    .thenComparing(CashFlowCategory::getNameKa, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<CashFlowCategory> findById(String id) {
        return list().stream().filter(category -> category.getId().equals(id)).findFirst();
    }

    public CashFlowCategory create(CashFlowSection section, CashFlowDirection direction, String nameKa,
                                   String parentId, Integer order) {
        if (nameKa == null || nameKa.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        lock.writeLock().lock();
        try {
            List<CashFlowCategory> categories = load();
            CashFlowSection effectiveSection = section;
            CashFlowDirection effectiveDirection = direction;
            String effectiveParentId = null;

            if (parentId != null && !parentId.isBlank()) {
                CashFlowCategory parent = categories.stream()
                    .filter(category -> category.getId().equals(parentId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found: " + parentId));
                if (parent.getParentId() != null) {
                    throw new IllegalArgumentException("Sub-categories can be nested only one level deep");
                }
                // A sub-category always inherits its parent's section and direction so
                // the direction-matching rule (credit->inflow, debit->outflow) holds.
                effectiveSection = parent.getSection();
                effectiveDirection = parent.getDirection();
                effectiveParentId = parent.getId();
            } else if (section == null || direction == null) {
                throw new IllegalArgumentException("Top-level category requires section and direction");
            }

            LocalDateTime now = LocalDateTime.now();
            int effectiveOrder = order != null ? order
                : categories.stream().mapToInt(CashFlowCategory::getOrder).max().orElse(0) + 1;
            String id = UUID.randomUUID().toString();
            CashFlowCategory created = new CashFlowCategory(
                id, id, effectiveSection, effectiveDirection, nameKa.trim(), effectiveParentId, effectiveOrder, false, now, now);
            categories.add(created);
            write(categories);
            return created;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CashFlowCategory update(String id, CashFlowSection section, CashFlowDirection direction, String nameKa, Integer order) {
        lock.writeLock().lock();
        try {
            List<CashFlowCategory> categories = load();
            CashFlowCategory existing = categories.stream()
                .filter(category -> category.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cash-flow category not found: " + id));
            if (section != null) {
                existing.setSection(section);
            }
            if (direction != null) {
                existing.setDirection(direction);
            }
            if (nameKa != null && !nameKa.isBlank()) {
                existing.setNameKa(nameKa.trim());
            }
            if (order != null) {
                existing.setOrder(order);
            }
            existing.setUpdatedAt(LocalDateTime.now());
            write(categories);
            return existing;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void delete(String id) {
        lock.writeLock().lock();
        try {
            List<CashFlowCategory> categories = load();
            CashFlowCategory existing = categories.stream()
                .filter(category -> category.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cash-flow category not found: " + id));
            if (existing.isBuiltin()) {
                throw new IllegalArgumentException("Built-in categories cannot be deleted");
            }
            boolean hasChildren = categories.stream().anyMatch(category -> id.equals(category.getParentId()));
            if (hasChildren) {
                throw new IllegalArgumentException("Delete the sub-categories first");
            }
            categories.removeIf(category -> category.getId().equals(id));
            write(categories);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private List<CashFlowCategory> load() {
        if (storePath == null || !Files.exists(storePath)) {
            return new ArrayList<>();
        }
        try {
            List<CashFlowCategory> loaded = objectMapper.readValue(storePath.toFile(), new TypeReference<>() {});
            return loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
        } catch (IOException exception) {
            quarantineCorrupt(storePath, exception);
            return new ArrayList<>();
        }
    }

    private void write(List<CashFlowCategory> categories) {
        try {
            Path tempFile = Files.createTempFile(storePath.getParent(), FILE_NAME, ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), categories);
                Files.move(tempFile, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException innerException) {
                Files.deleteIfExists(tempFile);
                throw innerException;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist cash-flow categories: " + storePath, exception);
        }
    }

    // Category data is user-authored, but a corrupt file must not brick the app;
    // it is quarantined and the default tree is re-seeded on next init.
    private void quarantineCorrupt(Path path, IOException exception) {
        Path quarantined = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(path, quarantined, StandardCopyOption.REPLACE_EXISTING);
            log.warn("Could not load {} ({}); moved to {} and re-seeding", path, exception.getMessage(), quarantined);
        } catch (IOException moveException) {
            log.warn("Could not load or quarantine {}: {}", path, exception.getMessage());
        }
    }
}
