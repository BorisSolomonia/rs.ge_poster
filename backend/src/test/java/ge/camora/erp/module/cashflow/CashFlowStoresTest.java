package ge.camora.erp.module.cashflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.module.bankanalysis.BankTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashFlowStoresTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private CamoraProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        properties = new CamoraProperties();
        properties.setConfigDir(tempDir.toString());
    }

    private CashFlowCategoryStore categoryStore() {
        CashFlowCategoryStore store = new CashFlowCategoryStore(objectMapper, properties);
        store.init();
        return store;
    }

    private TransactionCategoryRuleStore ruleStore() {
        TransactionCategoryRuleStore store = new TransactionCategoryRuleStore(objectMapper, properties);
        store.init();
        return store;
    }

    private TransactionCategoryOverrideStore overrideStore() {
        TransactionCategoryOverrideStore store = new TransactionCategoryOverrideStore(objectMapper, properties);
        store.init();
        return store;
    }

    @Test
    void categoryStoreSeedsDefaultTreeOnlyWhenEmpty() {
        CashFlowCategoryStore store = categoryStore();
        List<CashFlowCategory> seeded = store.list();
        assertThat(seeded).isNotEmpty();
        assertThat(seeded).anyMatch(c -> c.getId().equals(CashFlowCategoryDefaults.UNCATEGORIZED_INFLOW));
        assertThat(seeded).anyMatch(c -> c.getId().equals(CashFlowCategoryDefaults.UNCATEGORIZED_OUTFLOW));
        assertThat(seeded).allMatch(c -> c.getId().equals(c.getCode())); // builtin id == code

        // Re-init must not duplicate the seed.
        int seededCount = seeded.size();
        CashFlowCategoryStore reopened = categoryStore();
        assertThat(reopened.list()).hasSize(seededCount);
    }

    @Test
    void categoryCrudRoundTripsAndBlocksBuiltinDelete() {
        CashFlowCategoryStore store = categoryStore();
        CashFlowCategory created = store.create(CashFlowSection.OPERATING, CashFlowDirection.OUTFLOW, "მარკეტინგი", null, null);
        assertThat(created.getId()).isNotBlank();
        assertThat(created.isBuiltin()).isFalse();

        CashFlowCategoryStore reopened = categoryStore();
        assertThat(reopened.findById(created.getId())).isPresent();

        store.update(created.getId(), null, null, "რეკლამა", null);
        assertThat(categoryStore().findById(created.getId())).get()
            .extracting(CashFlowCategory::getNameKa).isEqualTo("რეკლამა");

        store.delete(created.getId());
        assertThat(categoryStore().findById(created.getId())).isEmpty();

        assertThatThrownBy(() -> store.delete(CashFlowCategoryDefaults.UNCATEGORIZED_INFLOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subCategoryInheritsParentAndEnforcesOneLevelAndBlocksParentDelete() {
        CashFlowCategoryStore store = categoryStore();
        CashFlowCategory parent = store.create(CashFlowSection.FINANCING, CashFlowDirection.OUTFLOW, "სესხები", null, null);
        CashFlowCategory child = store.create(null, null, "პროცენტი", parent.getId(), null);

        assertThat(child.getParentId()).isEqualTo(parent.getId());
        assertThat(child.getSection()).isEqualTo(CashFlowSection.FINANCING);
        assertThat(child.getDirection()).isEqualTo(CashFlowDirection.OUTFLOW);

        // Nesting is one level deep only.
        assertThatThrownBy(() -> store.create(null, null, "ქვე-ქვე", child.getId(), null))
            .isInstanceOf(IllegalArgumentException.class);

        // A parent that still has sub-categories cannot be deleted.
        assertThatThrownBy(() -> store.delete(parent.getId()))
            .isInstanceOf(IllegalArgumentException.class);

        store.delete(child.getId());
        store.delete(parent.getId()); // now allowed
        assertThat(categoryStore().findById(parent.getId())).isEmpty();
    }

    @Test
    void ruleUpsertIsIdempotentByDeterministicId() {
        TransactionCategoryRuleStore store = ruleStore();
        TransactionCategoryRule first = store.upsert(CashFlowMatchType.TAX_ID, "123-456-789", null, "salaries", "user");
        assertThat(first.getMatchValue()).isEqualTo("123456789");
        assertThat(first.getId()).isEqualTo("TAX_ID:123456789");

        // Same identifier, new category → updates in place, no duplicate.
        TransactionCategoryRule second = store.upsert(CashFlowMatchType.TAX_ID, "123 456 789", null, "taxes", "user");
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(store.list()).hasSize(1);
        assertThat(store.list().get(0).getCategoryId()).isEqualTo("taxes");

        // Direction-specific rule is a distinct row.
        store.upsert(CashFlowMatchType.TAX_ID, "123456789", CashFlowDirection.INFLOW, "sales", "user");
        assertThat(store.list()).hasSize(2);

        assertThat(ruleStore().list()).hasSize(2); // persisted across reopen
    }

    @Test
    void overridePutGetRemoveRoundTrips() {
        TransactionCategoryOverrideStore store = overrideStore();
        store.put("fp-1", "rent");
        assertThat(store.categoryFor("fp-1")).contains("rent");
        assertThat(overrideStore().categoryByFingerprint()).containsEntry("fp-1", "rent");

        store.put("fp-1", "utilities"); // upsert keeps single entry
        assertThat(store.categoryFor("fp-1")).contains("utilities");

        store.remove("fp-1");
        assertThat(overrideStore().categoryFor("fp-1")).isEmpty();
    }

    @Test
    void fingerprintIsStableAndDirectionDistinguishesPairedRows() {
        BankTransaction credit = txn("CREDIT", "500.00");
        BankTransaction sameCredit = txn("CREDIT", "500.00");
        BankTransaction debit = txn("DEBIT", "500.00");

        assertThat(CashFlowFingerprint.of("BOG", credit))
            .isEqualTo(CashFlowFingerprint.of("BOG", sameCredit));
        assertThat(CashFlowFingerprint.of("BOG", credit))
            .isNotEqualTo(CashFlowFingerprint.of("BOG", debit));
        assertThat(CashFlowFingerprint.of("BOG", credit))
            .isNotEqualTo(CashFlowFingerprint.of("TBC", credit));
    }

    private static BankTransaction txn(String direction, String amount) {
        return new BankTransaction(
            LocalDate.of(2026, 2, 1),
            direction,
            new BigDecimal(amount),
            "GEL",
            "GE00BG0000000000000001",
            "Cola Distributor",
            "123456789",
            "GE00XX0000000000000009",
            "POS deposit",
            "DOC-1",
            "raw"
        );
    }
}
