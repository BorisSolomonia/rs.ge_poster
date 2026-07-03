package ge.camora.erp.module.supplierdebt;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.dto.SupplierDebtRowDto;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class SupplierCreditorStore {

    private static final Logger log = LoggerFactory.getLogger(SupplierCreditorStore.class);

    private final ObjectMapper objectMapper;
    private final CamoraProperties properties;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Path storePath;

    public SupplierCreditorStore(ObjectMapper objectMapper, CamoraProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        Path configDir = Path.of(properties.getConfigDir());
        storePath = configDir.resolve(properties.getConfigFiles().getSupplierCreditors());
        try {
            Files.createDirectories(configDir);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create config dir: " + configDir, exception);
        }
    }

    public List<SavedSupplierCreditor> rows(LocalDate dateFrom, LocalDate dateTo) {
        return state().rows().stream()
            .filter(row -> dateFrom.equals(row.dateFrom()) && dateTo.equals(row.dateTo()))
            .toList();
    }

    public Optional<SavedSupplierCreditor> find(LocalDate dateFrom, LocalDate dateTo, String supplierKey) {
        return rows(dateFrom, dateTo).stream()
            .filter(row -> row.supplierKey().equals(supplierKey))
            .findFirst();
    }

    public SupplierCreditorPreference preference(String supplierKey) {
        return state().preferences().stream()
            .filter(preference -> preference.supplierKey().equals(supplierKey))
            .findFirst()
            .orElseGet(() -> new SupplierCreditorPreference(supplierKey, true));
    }

    public SavedSupplierCreditor save(LocalDate dateFrom, LocalDate dateTo, SupplierDebtRowDto row, LocalDateTime syncedAt, String syncError) {
        lock.writeLock().lock();
        try {
            SupplierCreditorState current = loadState();
            List<SavedSupplierCreditor> rows = new ArrayList<>(current.rows());
            rows.removeIf(saved -> dateFrom.equals(saved.dateFrom())
                && dateTo.equals(saved.dateTo())
                && row.supplierKey().equals(saved.supplierKey()));
            SavedSupplierCreditor saved = new SavedSupplierCreditor(
                dateFrom,
                dateTo,
                row.supplierKey(),
                row.supplierTin(),
                row.supplierName(),
                syncedAt,
                syncError == null ? "" : syncError,
                row
            );
            rows.add(saved);
            writeState(new SupplierCreditorState(rows, current.preferences()));
            return saved;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SupplierCreditorPreference setActive(String supplierKey, boolean active) {
        lock.writeLock().lock();
        try {
            SupplierCreditorState current = loadState();
            List<SupplierCreditorPreference> preferences = new ArrayList<>(current.preferences());
            preferences.removeIf(preference -> preference.supplierKey().equals(supplierKey));
            SupplierCreditorPreference saved = new SupplierCreditorPreference(supplierKey, active);
            preferences.add(saved);
            writeState(new SupplierCreditorState(current.rows(), preferences));
            return saved;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private SupplierCreditorState state() {
        lock.readLock().lock();
        try {
            return loadState();
        } finally {
            lock.readLock().unlock();
        }
    }

    private SupplierCreditorState loadState() {
        if (storePath == null || !Files.exists(storePath)) {
            return SupplierCreditorState.empty();
        }
        try {
            SupplierCreditorState loaded = objectMapper.readValue(storePath.toFile(), SupplierCreditorState.class);
            return new SupplierCreditorState(
                loaded.rows() == null ? List.of() : loaded.rows(),
                loaded.preferences() == null ? List.of() : loaded.preferences()
            );
        } catch (IOException exception) {
            log.warn("Could not load supplier creditor store {}: {}", storePath, exception.getMessage());
            return SupplierCreditorState.empty();
        }
    }

    private void writeState(SupplierCreditorState state) {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, storePath.getFileName().toString(), ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), state);
                Files.move(tempFile, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException innerException) {
                Files.deleteIfExists(tempFile);
                throw innerException;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist supplier creditor store: " + storePath, exception);
        }
    }

    public record SupplierCreditorState(
        List<SavedSupplierCreditor> rows,
        List<SupplierCreditorPreference> preferences
    ) {
        private static SupplierCreditorState empty() {
            return new SupplierCreditorState(List.of(), List.of());
        }
    }

    public record SavedSupplierCreditor(
        LocalDate dateFrom,
        LocalDate dateTo,
        String supplierKey,
        String supplierTin,
        String supplierName,
        LocalDateTime lastSyncedAt,
        String lastSyncError,
        SupplierDebtRowDto row
    ) {
    }

    public record SupplierCreditorPreference(String supplierKey, boolean active) {
    }
}