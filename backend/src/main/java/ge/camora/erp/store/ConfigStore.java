package ge.camora.erp.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.ProductMapping;
import ge.camora.erp.model.config.StandaloneSupplier;
import ge.camora.erp.model.config.SupplierMapping;
import ge.camora.erp.model.dto.SupplierMappingStatusView;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ConfigStore {

    private static final Logger log = LoggerFactory.getLogger(ConfigStore.class);
    private static final Pattern TAX_ID_PATTERN = Pattern.compile("\\((\\d+)\\)");

    private final ObjectMapper objectMapper;
    private final CamoraProperties properties;
    private Path configDir;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private List<SupplierMapping>    supplierMappings    = new ArrayList<>();
    private List<ProductMapping>     productMappings     = new ArrayList<>();
    private List<StandaloneSupplier> standaloneSuppliers = new ArrayList<>();

    public ConfigStore(ObjectMapper objectMapper, CamoraProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    public void load() {
        configDir = Path.of(properties.getConfigDir());
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create config dir: " + configDir, e);
        }
        supplierMappings    = loadJson(configDir.resolve(properties.getConfigFiles().getSupplierMappings()),
                                      new TypeReference<>() {});
        productMappings     = loadJson(configDir.resolve(properties.getConfigFiles().getProductMappings()),
                                      new TypeReference<>() {});
        standaloneSuppliers = loadJson(configDir.resolve(properties.getConfigFiles().getStandaloneSuppliers()),
                                      new TypeReference<>() {});
        log.info("ConfigStore loaded: {} supplier mappings, {} product mappings, {} standalone",
                 supplierMappings.size(), productMappings.size(), standaloneSuppliers.size());
    }

    // ─── SUPPLIER MAPPINGS ────────────────────────────────────────────────────

    public List<SupplierMapping> getAllSupplierMappings() {
        lock.readLock().lock();
        try {
            return List.copyOf(supplierMappings);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<SupplierMapping> findById(String id) {
        lock.readLock().lock();
        try {
            return supplierMappings.stream().filter(m -> m.getId().equals(id)).findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<SupplierMapping> findSupplierMappingByPosterAlias(String alias) {
        lock.readLock().lock();
        try {
            return supplierMappings.stream()
                .filter(m -> m.getPosterAlias().equalsIgnoreCase(alias))
                .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<SupplierMapping> findSupplierMappingByRsgeRawValue(String rawValue) {
        lock.readLock().lock();
        try {
            return supplierMappings.stream()
                .filter(m -> m.getRsgeRawValue().equals(rawValue))
                .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public SupplierMapping addSupplierMapping(SupplierMapping mapping) {
        lock.writeLock().lock();
        try {
            if (mapping.getId() == null) mapping.setId(UUID.randomUUID().toString());
            if (mapping.getCreatedAt() == null) mapping.setCreatedAt(LocalDateTime.now());
            enrichFromRawValue(mapping);
            supplierMappings.add(mapping);
            persistSupplierMappings();
            return mapping;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SupplierMapping updateSupplierMapping(String id, SupplierMapping updated) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < supplierMappings.size(); i++) {
                if (supplierMappings.get(i).getId().equals(id)) {
                    updated.setId(id);
                    updated.setCreatedAt(supplierMappings.get(i).getCreatedAt());
                    enrichFromRawValue(updated);
                    supplierMappings.set(i, updated);
                    persistSupplierMappings();
                    return updated;
                }
            }
            throw new IllegalArgumentException(properties.getMessages().getSupplierMappingNotFoundPrefix() + id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteSupplierMapping(String id) {
        lock.writeLock().lock();
        try {
            supplierMappings.removeIf(m -> m.getId().equals(id));
            productMappings.removeIf(p -> p.getSupplierMappingId().equals(id));
            persistSupplierMappings();
            persistProductMappings();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SupplierMappingStatusView getStatusView() {
        lock.readLock().lock();
        try {
            Set<String> knownRsgeValues = supplierMappings.stream()
                .map(SupplierMapping::getRsgeRawValue)
                .collect(Collectors.toSet());
            Set<String> knownPosterAliases = supplierMappings.stream()
                .map(SupplierMapping::getPosterAlias)
                .collect(Collectors.toSet());
            var unmappedPoster = standaloneSuppliers.stream()
                .filter(s -> properties.getPlatforms().getPoster().equals(s.getPlatform()))
                .filter(s -> !knownPosterAliases.contains(s.getName()))
                .collect(Collectors.toList());
            var unmappedRsge = standaloneSuppliers.stream()
                .filter(s -> properties.getPlatforms().getRsge().equals(s.getPlatform()))
                .filter(s -> !knownRsgeValues.contains(s.getName()))
                .collect(Collectors.toList());
            return new SupplierMappingStatusView(
                List.copyOf(supplierMappings), unmappedPoster, unmappedRsge);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── PRODUCT MAPPINGS ─────────────────────────────────────────────────────

    public List<ProductMapping> getProductMappings(String supplierMappingId) {
        lock.readLock().lock();
        try {
            return productMappings.stream()
                .filter(p -> p.getSupplierMappingId().equals(supplierMappingId))
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    public ProductMapping addProductMapping(ProductMapping mapping) {
        lock.writeLock().lock();
        try {
            if (mapping.getId() == null) mapping.setId(UUID.randomUUID().toString());
            if (mapping.getCreatedAt() == null) mapping.setCreatedAt(LocalDateTime.now());
            productMappings.add(mapping);
            persistProductMappings();
            return mapping;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ProductMapping updateProductMapping(String id, ProductMapping updated) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < productMappings.size(); i++) {
                if (productMappings.get(i).getId().equals(id)) {
                    updated.setId(id);
                    updated.setCreatedAt(productMappings.get(i).getCreatedAt());
                    productMappings.set(i, updated);
                    persistProductMappings();
                    return updated;
                }
            }
            throw new IllegalArgumentException(properties.getMessages().getProductMappingNotFoundPrefix() + id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteProductMapping(String id) {
        lock.writeLock().lock();
        try {
            productMappings.removeIf(p -> p.getId().equals(id));
            persistProductMappings();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── STANDALONE SUPPLIERS ────────────────────────────────────────────────

    public void registerStandaloneSupplier(String platform, String name) {
        lock.writeLock().lock();
        try {
            // Skip if already covered by a mapping
            boolean alreadyMapped = properties.getPlatforms().getRsge().equals(platform)
                ? supplierMappings.stream().anyMatch(m -> m.getRsgeRawValue().equals(name))
                : supplierMappings.stream().anyMatch(m -> m.getPosterAlias().equals(name));
            if (alreadyMapped) return;

            boolean exists = standaloneSuppliers.stream()
                .anyMatch(s -> s.getPlatform().equals(platform) && s.getName().equals(name));
            if (!exists) {
                standaloneSuppliers.add(new StandaloneSupplier(platform, name, false, LocalDateTime.now()));
                persistStandaloneSuppliers();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void markStandaloneExcluded(String platform, String name) {
        lock.writeLock().lock();
        try {
            standaloneSuppliers.stream()
                .filter(s -> s.getPlatform().equals(platform) && s.getName().equals(name))
                .forEach(s -> s.setExcluded(true));
            persistStandaloneSuppliers();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<StandaloneSupplier> getUnmappedSuppliers() {
        lock.readLock().lock();
        try {
            return List.copyOf(standaloneSuppliers);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return supplierMappings.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── PRIVATE ──────────────────────────────────────────────────────────────

    private void enrichFromRawValue(SupplierMapping mapping) {
        if (mapping.getRsgeRawValue() == null) return;
        var matcher = TAX_ID_PATTERN.matcher(mapping.getRsgeRawValue());
        if (matcher.find()) {
            mapping.setRsgeTaxId(matcher.group(1));
            int closeIdx = mapping.getRsgeRawValue().indexOf(") ");
            if (closeIdx >= 0) {
                mapping.setRsgeOfficialName(mapping.getRsgeRawValue().substring(closeIdx + 2).trim());
            }
        } else {
            mapping.setRsgeOfficialName(mapping.getRsgeRawValue().trim());
        }
    }

    private void persistSupplierMappings() {
        writeJson(configDir.resolve(properties.getConfigFiles().getSupplierMappings()), supplierMappings);
    }

    private void persistProductMappings() {
        writeJson(configDir.resolve(properties.getConfigFiles().getProductMappings()), productMappings);
    }

    private void persistStandaloneSuppliers() {
        writeJson(configDir.resolve(properties.getConfigFiles().getStandaloneSuppliers()), standaloneSuppliers);
    }

    private <T> List<T> loadJson(Path path, TypeReference<List<T>> type) {
        if (!Files.exists(path)) return new ArrayList<>();
        try {
            return objectMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            log.warn("Could not load {}: {}", path, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void writeJson(Path path, Object data) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), data);
        } catch (IOException e) {
            log.error("Failed to write {}: {}", path, e.getMessage(), e);
        }
    }
}
