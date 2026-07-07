package ge.camora.erp.module.cashflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.dto.CashFlowMatrixCategoryDto;
import ge.camora.erp.model.dto.CashFlowMatrixDto;
import ge.camora.erp.module.bankanalysis.BankTransaction;
import ge.camora.erp.module.bankanalysis.BogBusinessOnlineClient;
import ge.camora.erp.module.bankanalysis.TbcDbiClient;
import ge.camora.erp.module.supplierdebt.SourceLedgerStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CashFlowServiceTest {

    @TempDir
    Path tempDir;

    private final FakeTbc tbc = new FakeTbc();
    private final FakeBog bog = new FakeBog();
    private CashFlowService service;

    private static final LocalDate FROM = LocalDate.of(2026, 2, 1);
    private static final LocalDate TO = LocalDate.of(2026, 2, 28);

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CamoraProperties properties = new CamoraProperties();
        properties.setConfigDir(tempDir.toString());

        CashFlowCategoryStore categoryStore = new CashFlowCategoryStore(mapper, properties);
        categoryStore.init();
        TransactionCategoryRuleStore ruleStore = new TransactionCategoryRuleStore(mapper, properties);
        ruleStore.init();
        TransactionCategoryOverrideStore overrideStore = new TransactionCategoryOverrideStore(mapper, properties);
        overrideStore.init();

        service = new CashFlowService(tbc, bog, new PassThroughLedger(mapper, properties),
            categoryStore, ruleStore, overrideStore, properties);
        this.ruleStore = ruleStore;
        this.overrideStore = overrideStore;
    }

    private TransactionCategoryRuleStore ruleStore;
    private TransactionCategoryOverrideStore overrideStore;

    @Test
    void unmatchedCreditAndDebitLandInDirectionSentinels() {
        bog.transactions = List.of(
            credit("500.00", "111111111", "Random Payer"),
            debit("300.00", "222222222", "Random Vendor")
        );
        CashFlowMatrixDto matrix = service.matrix(FROM, TO);

        assertThat(cell(matrix, CashFlowCategoryDefaults.UNCATEGORIZED_INFLOW)).isEqualByComparingTo("500.00");
        assertThat(cell(matrix, CashFlowCategoryDefaults.UNCATEGORIZED_OUTFLOW)).isEqualByComparingTo("300.00");
        assertThat(matrix.months()).containsExactly("2026-02");
    }

    @Test
    void taxIdRuleBeatsIbanAndName() {
        ruleStore.upsert(CashFlowMatchType.TAX_ID, "123456789", null, "salaries", "user");
        ruleStore.upsert(CashFlowMatchType.NAME, "acme llc", null, "rent", "user");
        bog.transactions = List.of(debit("400.00", "123456789", "Acme LLC"));

        CashFlowMatrixDto matrix = service.matrix(FROM, TO);
        assertThat(cell(matrix, "salaries")).isEqualByComparingTo("400.00");
        assertThat(hasCategory(matrix, "rent")).isFalse();
    }

    @Test
    void reversalNetsDownTheOutflowTotal() {
        ruleStore.upsert(CashFlowMatchType.TAX_ID, "123456789", null, "rent", "user");
        bog.transactions = List.of(
            debit("1000.00", "123456789", "Landlord"),
            debit("-250.00", "123456789", "Landlord") // reversal
        );
        CashFlowMatrixDto matrix = service.matrix(FROM, TO);
        assertThat(cell(matrix, "rent")).isEqualByComparingTo("750.00");
    }

    @Test
    void singleOverrideBeatsGlobalRule() {
        ruleStore.upsert(CashFlowMatchType.TAX_ID, "123456789", null, "salaries", "user");
        BankTransaction txn = debit("400.00", "123456789", "Acme LLC");
        bog.transactions = List.of(txn);
        String fingerprint = CashFlowFingerprint.of("BOG", txn);

        service.categorizeSingle(fingerprint, "rent");
        CashFlowMatrixDto matrix = service.matrix(FROM, TO);
        assertThat(cell(matrix, "rent")).isEqualByComparingTo("400.00");
        assertThat(hasCategory(matrix, "salaries")).isFalse();
    }

    @Test
    void cascadeCreatesRuleAndClearsOverride() {
        BankTransaction txn = debit("400.00", "123456789", "Acme LLC");
        bog.transactions = List.of(txn);
        String fingerprint = CashFlowFingerprint.of("BOG", txn);

        service.categorizeSingle(fingerprint, "rent");
        assertThat(overrideStore.categoryFor(fingerprint)).contains("rent");

        service.categorizeCascade(fingerprint, "salaries", "123456789", "GE00X", "Acme LLC");
        assertThat(overrideStore.categoryFor(fingerprint)).isEmpty();
        assertThat(ruleStore.list()).anyMatch(rule ->
            rule.getMatchType() == CashFlowMatchType.TAX_ID && rule.getCategoryId().equals("salaries"));

        CashFlowMatrixDto matrix = service.matrix(FROM, TO);
        assertThat(cell(matrix, "salaries")).isEqualByComparingTo("400.00");
    }

    @Test
    void tbcAndBogRowsAreBothIngested() {
        ruleStore.upsert(CashFlowMatchType.NAME, "shop", null, "sales", "user");
        bog.transactions = List.of(credit("100.00", "", "Shop"));
        tbc.transactions = List.of(credit("50.00", "", "Shop"));
        CashFlowMatrixDto matrix = service.matrix(FROM, TO);
        assertThat(cell(matrix, "sales")).isEqualByComparingTo("150.00");
    }

    @Test
    void drilldownReturnsTransactionsForCategoryAndMonth() {
        ruleStore.upsert(CashFlowMatchType.TAX_ID, "123456789", null, "rent", "user");
        bog.transactions = List.of(
            debit("100.00", "123456789", "Landlord"),
            debit("40.00", "123456789", "Landlord")
        );
        var drilldown = service.drilldown("rent", "2026-02", FROM, TO);
        assertThat(drilldown.transactions()).hasSize(2);
        assertThat(drilldown.total()).isEqualByComparingTo("140.00");
        assertThat(drilldown.transactions()).allMatch(txn -> txn.resolvedBy().equals("RULE_TAX_ID"));
        assertThat(drilldown.transactions().get(0).fingerprint()).isNotBlank();
    }

    @Test
    void categoriesExposeSeededGeorgianTree() {
        var categories = service.categories();
        assertThat(categories).anyMatch(c -> c.id().equals("rent") && c.directionNameKa().equals("გასავლები"));
        assertThat(categories).anyMatch(c -> c.sectionNameKa().equals("საოპერაციო საქმიანობა"));
    }

    // helpers ------------------------------------------------------------------

    private static BigDecimal cell(CashFlowMatrixDto matrix, String categoryId) {
        return findCategory(matrix, categoryId).map(CashFlowMatrixCategoryDto::total).orElse(BigDecimal.ZERO);
    }

    private static boolean hasCategory(CashFlowMatrixDto matrix, String categoryId) {
        return findCategory(matrix, categoryId).isPresent();
    }

    private static Optional<CashFlowMatrixCategoryDto> findCategory(CashFlowMatrixDto matrix, String categoryId) {
        return matrix.sections().stream()
            .flatMap(section -> section.directions().stream())
            .flatMap(direction -> direction.categories().stream())
            .filter(category -> category.categoryId().equals(categoryId))
            .findFirst();
    }

    private static BankTransaction credit(String amount, String inn, String name) {
        return txn(BankTransaction.CREDIT, amount, inn, name);
    }

    private static BankTransaction debit(String amount, String inn, String name) {
        return txn(BankTransaction.DEBIT, amount, inn, name);
    }

    private static BankTransaction txn(String direction, String amount, String inn, String name) {
        return new BankTransaction(
            LocalDate.of(2026, 2, 10), direction, new BigDecimal(amount), "GEL",
            "GE00OURACCOUNT", name, inn, "GE00COUNTERPARTY", "desc", "REF-" + amount, "raw");
    }

    private static final class FakeTbc extends TbcDbiClient {
        private List<BankTransaction> transactions = List.of();

        private FakeTbc() {
            super(new CamoraProperties());
        }

        @Override
        public List<BankTransaction> getAccountMovements(LocalDate dateFrom, LocalDate dateTo) {
            return transactions;
        }
    }

    private static final class FakeBog extends BogBusinessOnlineClient {
        private List<BankTransaction> transactions = List.of();

        private FakeBog() {
            super(new CamoraProperties(), new ObjectMapper());
        }

        @Override
        public List<BankTransaction> getStatement(LocalDate dateFrom, LocalDate dateTo) {
            return transactions;
        }
    }

    /** Bypasses ledger persistence/windowing so the fetcher output is used verbatim. */
    private static final class PassThroughLedger extends SourceLedgerStore {
        private PassThroughLedger(ObjectMapper mapper, CamoraProperties properties) {
            super(mapper, properties);
        }

        @Override
        public List<BankTransaction> syncBank(String source, LocalDate rangeFrom, LocalDate rangeTo,
                                              SourceFetcher<BankTransaction> fetcher) {
            return fetcher.fetch(rangeFrom, rangeTo);
        }
    }
}
