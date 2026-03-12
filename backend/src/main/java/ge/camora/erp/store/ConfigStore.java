package ge.camora.erp.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.ProductMapping;
import ge.camora.erp.model.config.SalesEvent;
import ge.camora.erp.model.config.SalesProductExclusion;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    private List<SalesProductExclusion> salesProductExclusions = new ArrayList<>();
    private List<SalesEvent> salesEvents = new ArrayList<>();

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
        salesProductExclusions = loadJson(configDir.resolve(properties.getConfigFiles().getSalesProductExclusions()),
                                      new TypeReference<>() {});
        salesEvents = loadJson(configDir.resolve(properties.getConfigFiles().getSalesEvents()),
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

    public List<SalesProductExclusion> getSalesProductExclusions() {
        lock.readLock().lock();
        try {
            return salesProductExclusions.stream()
                .sorted(Comparator.comparing(SalesProductExclusion::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void registerSalesProduct(String displayName) {
        String normalizedName = normalizeSalesKey(displayName);
        if (normalizedName.isBlank()) {
            return;
        }
        lock.writeLock().lock();
        try {
            boolean exists = salesProductExclusions.stream()
                .anyMatch(entry -> entry.getNormalizedName().equals(normalizedName));
            if (!exists) {
                salesProductExclusions.add(new SalesProductExclusion(
                    normalizedName,
                    displayName.trim(),
                    false,
                    "discovered",
                    LocalDateTime.now()
                ));
                persistSalesProductExclusions();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SalesProductExclusion upsertSalesProduct(String displayName, boolean excluded, String source) {
        String normalizedName = normalizeSalesKey(displayName);
        lock.writeLock().lock();
        try {
            for (SalesProductExclusion entry : salesProductExclusions) {
                if (entry.getNormalizedName().equals(normalizedName)) {
                    entry.setDisplayName(displayName.trim());
                    entry.setExcluded(excluded);
                    entry.setSource(source);
                    persistSalesProductExclusions();
                    return entry;
                }
            }
            SalesProductExclusion created = new SalesProductExclusion(
                normalizedName,
                displayName.trim(),
                excluded,
                source,
                LocalDateTime.now()
            );
            salesProductExclusions.add(created);
            persistSalesProductExclusions();
            return created;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void setSalesProductExcluded(String normalizedName, boolean excluded) {
        lock.writeLock().lock();
        try {
            SalesProductExclusion entry = salesProductExclusions.stream()
                .filter(product -> product.getNormalizedName().equals(normalizedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    properties.getMessages().getGenericNotFoundPrefix() + normalizedName
                ));
            entry.setExcluded(excluded);
            persistSalesProductExclusions();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isSalesProductExcluded(String displayName) {
        String normalizedName = normalizeSalesKey(displayName);
        lock.readLock().lock();
        try {
            return salesProductExclusions.stream()
                .anyMatch(entry -> entry.getNormalizedName().equals(normalizedName) && entry.isExcluded());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<SalesEvent> getSalesEvents() {
        lock.readLock().lock();
        try {
            return salesEvents.stream()
                .sorted(Comparator.comparing(SalesEvent::getDate))
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<SalesEvent> findSalesEventByDate(LocalDate date) {
        lock.readLock().lock();
        try {
            return salesEvents.stream().filter(event -> event.getDate().equals(date)).findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public SalesEvent upsertSalesEvent(LocalDate date, String name) {
        String normalizedName = normalizeSalesKey(name);
        lock.writeLock().lock();
        try {
            for (SalesEvent event : salesEvents) {
                if (event.getDate().equals(date)) {
                    event.setName(name.trim());
                    event.setNormalizedName(normalizedName);
                    event.setUpdatedAt(LocalDateTime.now());
                    persistSalesEvents();
                    return event;
                }
            }

            SalesEvent created = new SalesEvent(date, name.trim(), normalizedName, LocalDateTime.now(), LocalDateTime.now());
            salesEvents.add(created);
            persistSalesEvents();
            return created;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteSalesEvent(LocalDate date) {
        lock.writeLock().lock();
        try {
            salesEvents.removeIf(event -> event.getDate().equals(date));
            persistSalesEvents();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<String> suggestSalesEventNames(String query, int limit, int maxDistance) {
        String normalizedQuery = normalizeSalesKey(query);
        lock.readLock().lock();
        try {
            return salesEvents.stream()
                .map(SalesEvent::getName)
                .distinct()
                .sorted((left, right) -> Integer.compare(
                    suggestionScore(normalizedQuery, normalizeSalesKey(left), maxDistance),
                    suggestionScore(normalizedQuery, normalizeSalesKey(right), maxDistance)
                ))
                .filter(name -> suggestionScore(normalizedQuery, normalizeSalesKey(name), maxDistance) < Integer.MAX_VALUE)
                .limit(limit)
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

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

    private void persistSalesProductExclusions() {
        writeJson(configDir.resolve(properties.getConfigFiles().getSalesProductExclusions()), salesProductExclusions);
    }

    private void persistSalesEvents() {
        writeJson(configDir.resolve(properties.getConfigFiles().getSalesEvents()), salesEvents);
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

    public static String normalizeSalesKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private int suggestionScore(String query, String candidate, int maxDistance) {
        if (query.isBlank() || candidate.isBlank()) {
            return Integer.MAX_VALUE;
        }
        if (candidate.equals(query)) {
            return 0;
        }
        if (candidate.startsWith(query)) {
            return 1;
        }
        if (candidate.contains(query)) {
            return 2;
        }
        int distance = levenshtein(query, candidate);
        return distance <= maxDistance ? 10 + distance : Integer.MAX_VALUE;
    }

    private int levenshtein(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[left.length()][right.length()];
    }
}
