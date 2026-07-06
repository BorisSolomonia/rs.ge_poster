package ge.camora.erp.module.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.ProductMapping;
import ge.camora.erp.model.config.SupplierMapping;
import ge.camora.erp.model.dto.ReconciliationLineResult;
import ge.camora.erp.model.dto.ReconciliationResult;
import ge.camora.erp.model.dto.ReconciliationStatus;
import ge.camora.erp.model.record.PosterRecord;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.store.ConfigStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationEngineTest {

    private final FakeConfigStore configStore = new FakeConfigStore();
    private final CamoraProperties properties = buildProperties();
    private final ReconciliationEngine engine = new ReconciliationEngine(configStore, properties);

    private static CamoraProperties buildProperties() {
        CamoraProperties properties = new CamoraProperties();
        properties.setMatchThreshold(new BigDecimal("0.01"));
        properties.getPlatforms().setRsge("RSGE");
        properties.getPlatforms().setPoster("POSTER");
        properties.getMessages().setMissingInPosterTemplate("missing in poster %s %s");
        properties.getMessages().setMissingInRsgeTemplate("missing in rsge %s %s");
        properties.getMessages().setDiscrepancyCorrectionTemplate("%s %s %s %s %s %s %s %s");
        properties.getReconciliation().setResultExpireAfterHours(12);
        return properties;
    }

    @Test
    void excludedProductPatternRemovesRecordsFromTotals() {
        SupplierMapping mapping = mapping("m1", "(1) Alpha", "Alpha");
        configStore.mappings.add(mapping);
        configStore.products.put("m1", List.of(
            excludedProduct("m1", "delivery", "delivery")
        ));

        List<RsgeRecord> rsge = List.of(
            rsgeRecord("(1) Alpha", "Product A", "100.00"),
            rsgeRecord("(1) Alpha", "Delivery Fee", "50.00")
        );
        List<PosterRecord> poster = List.of(
            posterRecord("Alpha", "goods", "100.00")
        );

        ReconciliationResult result = engine.run(rsge, poster, from(), to());

        assertThat(result.lines()).hasSize(1);
        ReconciliationLineResult line = result.lines().get(0);
        assertThat(line.rsgeTotal()).isEqualByComparingTo("100.00");
        assertThat(line.posterTotal()).isEqualByComparingTo("100.00");
        assertThat(line.status()).isEqualTo(ReconciliationStatus.MATCH);
    }

    @Test
    void groupsMultipleRsgeMappingsForOnePosterAlias() {
        configStore.mappings.add(mapping("m1", "(1) Alpha LLC", "Shared"));
        configStore.mappings.add(mapping("m2", "(2) Alpha Group", "Shared"));

        List<RsgeRecord> rsge = List.of(
            rsgeRecord("(1) Alpha LLC", "Product A", "60.00"),
            rsgeRecord("(2) Alpha Group", "Product B", "40.00")
        );
        List<PosterRecord> poster = List.of(
            posterRecord("Shared", "goods", "100.00")
        );

        ReconciliationResult result = engine.run(rsge, poster, from(), to());

        assertThat(result.lines()).hasSize(1);
        ReconciliationLineResult line = result.lines().get(0);
        assertThat(line.rsgeTotal()).isEqualByComparingTo("100.00");
        assertThat(line.posterTotal()).isEqualByComparingTo("100.00");
        assertThat(line.status()).isEqualTo(ReconciliationStatus.MATCH);
        assertThat(result.summary().discrepancy()).isZero();
        assertThat(result.summary().missingPoster()).isZero();
        assertThat(result.summary().missingRsge()).isZero();
    }

    @Test
    void doublyExcludedMappingSuppressesBothSides() {
        SupplierMapping excluded = mapping("m1", "(1) Alpha", "Alpha");
        excluded.setPosterExcluded(true);
        excluded.setRsgeExcluded(true);
        configStore.mappings.add(excluded);

        List<RsgeRecord> rsge = List.of(rsgeRecord("(1) Alpha", "Product A", "70.00"));
        List<PosterRecord> poster = List.of(posterRecord("Alpha", "goods", "80.00"));

        ReconciliationResult result = engine.run(rsge, poster, from(), to());

        assertThat(result.lines()).isEmpty();
    }

    @Test
    void skippedRowCountsFlowIntoResult() {
        ReconciliationResult result = engine.run(List.of(), List.of(), from(), to(), 3, 2);

        assertThat(result.skippedRsgeRows()).isEqualTo(3);
        assertThat(result.skippedPosterRows()).isEqualTo(2);
    }

    private static LocalDate from() {
        return LocalDate.of(2026, 1, 1);
    }

    private static LocalDate to() {
        return LocalDate.of(2026, 1, 31);
    }

    private static SupplierMapping mapping(String id, String rsgeRawValue, String posterAlias) {
        SupplierMapping mapping = new SupplierMapping();
        mapping.setId(id);
        mapping.setRsgeRawValue(rsgeRawValue);
        mapping.setPosterAlias(posterAlias);
        return mapping;
    }

    private static ProductMapping excludedProduct(String supplierMappingId, String rsgePattern, String posterPattern) {
        ProductMapping product = new ProductMapping();
        product.setId("p-" + supplierMappingId);
        product.setSupplierMappingId(supplierMappingId);
        product.setRsgeProductPattern(rsgePattern);
        product.setPosterProductPattern(posterPattern);
        product.setRegex(false);
        product.setExcluded(true);
        return product;
    }

    private static RsgeRecord rsgeRecord(String supplierRaw, String productName, String total) {
        return new RsgeRecord("WB-1", supplierRaw, productName, "kg",
            BigDecimal.ONE, new BigDecimal(total), new BigDecimal(total),
            LocalDateTime.of(2026, 1, 10, 12, 0));
    }

    private static PosterRecord posterRecord(String alias, String productsRaw, String total) {
        return new PosterRecord(1, alias, productsRaw, new BigDecimal(total),
            LocalDateTime.of(2026, 1, 12, 12, 0));
    }

    private static class FakeConfigStore extends ConfigStore {
        private final List<SupplierMapping> mappings = new ArrayList<>();
        private final Map<String, List<ProductMapping>> products = new HashMap<>();

        private FakeConfigStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }

        @Override
        public List<SupplierMapping> getAllSupplierMappings() {
            return List.copyOf(mappings);
        }

        @Override
        public List<ProductMapping> getProductMappings(String supplierMappingId) {
            return products.getOrDefault(supplierMappingId, List.of());
        }

        @Override
        public Set<String> getExcludedStandaloneNames(String platform) {
            return Set.of();
        }

        @Override
        public void registerStandaloneSupplier(String platform, String name) {
            // no-op for tests
        }
    }
}
