package ge.camora.erp.module.supplierdebt;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.dto.SupplierDebtOverviewDto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class SupplierDebtSnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(SupplierDebtSnapshotStore.class);

    private final ObjectMapper objectMapper;
    private final CamoraProperties properties;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Path snapshotPath;

    public SupplierDebtSnapshotStore(ObjectMapper objectMapper, CamoraProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        Path configDir = Path.of(properties.getConfigDir());
        snapshotPath = configDir.resolve(properties.getConfigFiles().getSupplierDebtSnapshot());
        try {
            Files.createDirectories(configDir);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create config dir: " + configDir, exception);
        }
    }

    public Optional<SupplierDebtOverviewDto> load() {
        lock.readLock().lock();
        try {
            if (!Files.exists(snapshotPath)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(snapshotPath.toFile(), SupplierDebtOverviewDto.class));
        } catch (IOException exception) {
            log.warn("Could not load supplier debt snapshot {}: {}", snapshotPath, exception.getMessage());
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    public SupplierDebtOverviewDto save(SupplierDebtOverviewDto snapshot) {
        lock.writeLock().lock();
        try {
            Path parent = snapshotPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, snapshotPath.getFileName().toString(), ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), snapshot);
                Files.move(tempFile, snapshotPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException innerException) {
                Files.deleteIfExists(tempFile);
                throw innerException;
            }
            return snapshot;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist supplier debt snapshot: " + snapshotPath, exception);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
