package ge.camora.erp.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.BankTransactionMapping;
import ge.camora.erp.model.config.ProductMapping;
import ge.camora.erp.model.config.SalesEvent;
import ge.camora.erp.model.config.SalesProductExclusion;
import ge.camora.erp.model.config.StandaloneSupplier;
import ge.camora.erp.model.config.SupplierCashPayment;
import ge.camora.erp.model.config.SupplierMapping;
import ge.camora.erp.model.config.SupplierPaymentMapping;
import ge.camora.erp.model.dto.SupplierMappingStatusView;
import ge.camora.erp.util.StringUtil;
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
    private List<BankTransactionMapping> bankTransactionMappings = new ArrayList<>();
    private List<SupplierPaymentMapping> supplierPaymentMappings = new ArrayList<>();
    private List<SupplierCashPayment> supplierCashPayments = new ArrayList<>();

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
        bankTransactionMappings = loadJson(configDir.resolve(properties.getConfigFiles().getBankTransactionMappings()),
                                      new TypeReference<>() {});
        supplierPaymentMappings = loadJson(configDir.resolve(properties.getConfigFiles().getSupplierPaymentMappings()),
                                      new TypeReference<>() {});
        supplierCashPayments = loadJson(configDir.resolve(properties.getConfigFiles().getSupplierCashPayments()),
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
            assertNoDuplicateMapping(mapping, null);
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

    // The same rs.ge entity mapped twice is always ambiguous, so it is rejected;
    // several rs.ge entities sharing one Poster alias is a legitimate many-to-one
    // case and stays allowed (the engine reconciles those as one group).
    private void assertNoDuplicateMapping(SupplierMapping candidate, String ignoreId) {
        String rawValue = candidate.getRsgeRawValue() == null ? "" : candidate.getRsgeRawValue().trim();
        if (rawValue.isEmpty()) return;
        for (SupplierMapping existing : supplierMappings) {
            if (existing.getId() != null && existing.getId().equals(ignoreId)) continue;
            String existingRaw = existing.getRsgeRawValue() == null ? "" : existing.getRsgeRawValue().trim();
            if (existingRaw.equalsIgnoreCase(rawValue)) {
                throw new IllegalArgumentException(
                    "rs.ge supplier '" + candidate.getRsgeRawValue() + "' is already mapped to Poster supplier '"
                        + existing.getPosterAlias() + "'");
            }
        }
    }

    public SupplierMapping updateSupplierMapping(String id, SupplierMapping updated) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < supplierMappings.size(); i++) {
                if (supplierMappings.get(i).getId().equals(id)) {
                    assertNoDuplicateMapping(updated, id);
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
        List<SupplierMapping> previousSuppliers = new ArrayList<>(supplierMappings);
        List<ProductMapping> previousProducts = new ArrayList<>(productMappings);
        try {
            supplierMappings.removeIf(m -> m.getId().equals(id));
            productMappings.removeIf(p -> p.getSupplierMappingId().equals(id));
            persistSupplierMappings();
            try {
                persistProductMappings();
            } catch (RuntimeException exception) {
                supplierMappings = previousSuppliers;
                persistSupplierMappings();
                throw exception;
            }
        } catch (RuntimeException exception) {
            supplierMappings = previousSuppliers;
            productMappings = previousProducts;
            throw exception;
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

    public Set<String> getExcludedStandaloneNames(String platform) {
        lock.readLock().lock();
        try {
            return standaloneSuppliers.stream()
                .filter(s -> s.getPlatform().equals(platform) && s.isExcluded())
                .map(StandaloneSupplier::getName)
                .collect(Collectors.toSet());
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

    public List<BankTransactionMapping> getBankTransactionMappings() {
        lock.readLock().lock();
        try {
            return bankTransactionMappings.stream()
                .sorted(Comparator.comparing(BankTransactionMapping::getCategory, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(BankTransactionMapping::getMatchText, String.CASE_INSENSITIVE_ORDER))
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public BankTransactionMapping upsertBankTransactionMapping(
        String direction,
        String matchText,
        String category,
        String source
    ) {
        String normalizedMatchText = normalizeSalesKey(matchText);
        String normalizedCategory = normalizeSalesKey(category);
        String normalizedDirection = normalizeBankDirection(direction);
        if (normalizedMatchText.isBlank()) {
            throw new IllegalArgumentException("Bank mapping match text is required");
        }
        if (normalizedCategory.isBlank()) {
            throw new IllegalArgumentException("Bank mapping category is required");
        }
        lock.writeLock().lock();
        try {
            List<BankTransactionMapping> previous = copyBankTransactionMappings();
            LocalDateTime now = LocalDateTime.now();
            for (BankTransactionMapping mapping : bankTransactionMappings) {
                if (mapping.getDirection().equals(normalizedDirection)
                    && mapping.getNormalizedMatchText().equals(normalizedMatchText)) {
                    mapping.setMatchText(matchText.trim());
                    mapping.setCategory(category.trim());
                    mapping.setNormalizedCategory(normalizedCategory);
                    mapping.setSource(source);
                    mapping.setUpdatedAt(now);
                    persistBankTransactionMappingsWithRollback(previous);
                    return mapping;
                }
            }
            BankTransactionMapping created = new BankTransactionMapping(
                UUID.randomUUID().toString(),
                normalizedDirection,
                matchText.trim(),
                normalizedMatchText,
                category.trim(),
                normalizedCategory,
                source,
                now,
                now
            );
            bankTransactionMappings.add(created);
            persistBankTransactionMappingsWithRollback(previous);
            return created;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteBankTransactionMapping(String id) {
        lock.writeLock().lock();
        try {
            List<BankTransactionMapping> previous = copyBankTransactionMappings();
            bankTransactionMappings.removeIf(mapping -> mapping.getId().equals(id));
            persistBankTransactionMappingsWithRollback(previous);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<SupplierPaymentMapping> getSupplierPaymentMappings() {
        lock.readLock().lock();
        try {
            return supplierPaymentMappings.stream()
                .sorted(Comparator.comparing(SupplierPaymentMapping::getSupplierName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(SupplierPaymentMapping::getMatchText, String.CASE_INSENSITIVE_ORDER))
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public SupplierPaymentMapping upsertSupplierPaymentMapping(
        String provider,
        String matchText,
        String supplierKey,
        String supplierTin,
        String supplierName,
        String source
    ) {
        String normalizedProvider = provider == null || provider.isBlank()
            ? "BOG"
            : provider.trim().toUpperCase(Locale.ROOT);
        String normalizedMatchText = normalizeSalesKey(matchText);
        String normalizedSupplierKey = normalizeSalesKey(supplierKey);
        if (normalizedMatchText.isBlank()) {
            throw new IllegalArgumentException("Supplier payment mapping match text is required");
        }
        if (normalizedSupplierKey.isBlank()) {
            throw new IllegalArgumentException("Supplier payment mapping supplier key is required");
        }
        lock.writeLock().lock();
        try {
            List<SupplierPaymentMapping> previous = copySupplierPaymentMappings();
            LocalDateTime now = LocalDateTime.now();
            for (SupplierPaymentMapping mapping : supplierPaymentMappings) {
                if (mapping.getProvider().equals(normalizedProvider)
                    && mapping.getNormalizedMatchText().equals(normalizedMatchText)) {
                    mapping.setMatchText(matchText.trim());
                    mapping.setSupplierKey(supplierKey.trim());
                    mapping.setSupplierTin(supplierTin == null ? "" : supplierTin.trim());
                    mapping.setSupplierName(supplierName == null ? supplierKey.trim() : supplierName.trim());
                    mapping.setSource(source);
                    mapping.setUpdatedAt(now);
                    persistSupplierPaymentMappingsWithRollback(previous);
                    return mapping;
                }
            }
            SupplierPaymentMapping created = new SupplierPaymentMapping(
                UUID.randomUUID().toString(),
                normalizedProvider,
                matchText.trim(),
                normalizedMatchText,
                supplierKey.trim(),
                supplierTin == null ? "" : supplierTin.trim(),
                supplierName == null ? supplierKey.trim() : supplierName.trim(),
                source,
                now,
                now
            );
            supplierPaymentMappings.add(created);
            persistSupplierPaymentMappingsWithRollback(previous);
            return created;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteSupplierPaymentMapping(String id) {
        lock.writeLock().lock();
        try {
            List<SupplierPaymentMapping> previous = copySupplierPaymentMappings();
            supplierPaymentMappings.removeIf(mapping -> mapping.getId().equals(id));
            persistSupplierPaymentMappingsWithRollback(previous);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<SupplierCashPayment> getSupplierCashPayments(LocalDate dateFrom, LocalDate dateTo) {
        lock.readLock().lock();
        try {
            return supplierCashPayments.stream()
                .filter(payment -> payment.getDate() != null)
                .filter(payment -> !payment.getDate().isBefore(dateFrom) && !payment.getDate().isAfter(dateTo))
                .sorted(Comparator.comparing(SupplierCashPayment::getDate).reversed()
                    .thenComparing(SupplierCashPayment::getSupplierName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public SupplierCashPayment addSupplierCashPayment(
        String supplierKey,
        String supplierTin,
        String supplierName,
        LocalDate date,
        java.math.BigDecimal amount,
        String note,
        String source
    ) {
        String normalizedSupplierKey = normalizeSalesKey(supplierKey);
        if (normalizedSupplierKey.isBlank()) {
            throw new IllegalArgumentException("Cash payment supplier is required");
        }
        if (date == null) {
            throw new IllegalArgumentException("Cash payment date is required");
        }
        if (amount == null || amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Cash payment amount must be greater than zero");
        }
        lock.writeLock().lock();
        try {
            List<SupplierCashPayment> previous = copySupplierCashPayments();
            LocalDateTime now = LocalDateTime.now();
            SupplierCashPayment created = new SupplierCashPayment(
                UUID.randomUUID().toString(),
                supplierKey.trim(),
                supplierTin == null ? "" : supplierTin.trim(),
                supplierName == null || supplierName.isBlank() ? supplierKey.trim() : supplierName.trim(),
                date,
                amount,
                note == null ? "" : note.trim(),
                source,
                now,
                now
            );
            supplierCashPayments.add(created);
            persistSupplierCashPaymentsWithRollback(previous);
            return created;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteSupplierCashPayment(String id) {
        lock.writeLock().lock();
        try {
            List<SupplierCashPayment> previous = copySupplierCashPayments();
            supplierCashPayments.removeIf(payment -> payment.getId().equals(id));
            persistSupplierCashPaymentsWithRollback(previous);
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

    private void persistBankTransactionMappings() {
        writeJson(configDir.resolve(properties.getConfigFiles().getBankTransactionMappings()), bankTransactionMappings);
    }

    private void persistSupplierPaymentMappings() {
        writeJson(configDir.resolve(properties.getConfigFiles().getSupplierPaymentMappings()), supplierPaymentMappings);
    }

    private void persistSupplierCashPayments() {
        writeJson(configDir.resolve(properties.getConfigFiles().getSupplierCashPayments()), supplierCashPayments);
    }

    private void persistBankTransactionMappingsWithRollback(List<BankTransactionMapping> previous) {
        try {
            persistBankTransactionMappings();
        } catch (RuntimeException exception) {
            bankTransactionMappings = previous;
            throw exception;
        }
    }

    private List<BankTransactionMapping> copyBankTransactionMappings() {
        return bankTransactionMappings.stream()
            .map(mapping -> new BankTransactionMapping(
                mapping.getId(),
                mapping.getDirection(),
                mapping.getMatchText(),
                mapping.getNormalizedMatchText(),
                mapping.getCategory(),
                mapping.getNormalizedCategory(),
                mapping.getSource(),
                mapping.getCreatedAt(),
                mapping.getUpdatedAt()
            ))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private void persistSupplierPaymentMappingsWithRollback(List<SupplierPaymentMapping> previous) {
        try {
            persistSupplierPaymentMappings();
        } catch (RuntimeException exception) {
            supplierPaymentMappings = previous;
            throw exception;
        }
    }

    private List<SupplierPaymentMapping> copySupplierPaymentMappings() {
        return supplierPaymentMappings.stream()
            .map(mapping -> new SupplierPaymentMapping(
                mapping.getId(),
                mapping.getProvider(),
                mapping.getMatchText(),
                mapping.getNormalizedMatchText(),
                mapping.getSupplierKey(),
                mapping.getSupplierTin(),
                mapping.getSupplierName(),
                mapping.getSource(),
                mapping.getCreatedAt(),
                mapping.getUpdatedAt()
            ))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private void persistSupplierCashPaymentsWithRollback(List<SupplierCashPayment> previous) {
        try {
            persistSupplierCashPayments();
        } catch (RuntimeException exception) {
            supplierCashPayments = previous;
            throw exception;
        }
    }

    private List<SupplierCashPayment> copySupplierCashPayments() {
        return supplierCashPayments.stream()
            .map(payment -> new SupplierCashPayment(
                payment.getId(),
                payment.getSupplierKey(),
                payment.getSupplierTin(),
                payment.getSupplierName(),
                payment.getDate(),
                payment.getAmount(),
                payment.getNote(),
                payment.getSource(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
            ))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private <T> List<T> loadJson(Path path, TypeReference<List<T>> type) {
        if (!Files.exists(path)) return new ArrayList<>();
        try {
            return objectMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            log.error("Could not load {}: {}", path, e.getMessage(), e);
            throw new IllegalStateException("Failed to load config file " + path + " — refusing to start with possibly corrupt data", e);
        }
    }

    private void writeJson(Path path, Object data) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), data);
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException innerException) {
                Files.deleteIfExists(tempFile);
                throw innerException;
            }
        } catch (IOException e) {
            log.error("Failed to write {}: {}", path, e.getMessage(), e);
            throw new IllegalStateException("Failed to persist config file: " + path, e);
        }
    }

    public static String normalizeSalesKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value
            .replace('\u00A0', ' ')
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .trim()
            .replaceAll("\\s+", " ");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeBankDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return "BOTH";
        }
        String normalized = direction.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("CREDIT") && !normalized.equals("DEBIT") && !normalized.equals("BOTH")) {
            throw new IllegalArgumentException("Unsupported bank mapping direction: " + direction);
        }
        return normalized;
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
        int distance = StringUtil.levenshtein(query, candidate);
        return distance <= maxDistance ? 10 + distance : Integer.MAX_VALUE;
    }
}
