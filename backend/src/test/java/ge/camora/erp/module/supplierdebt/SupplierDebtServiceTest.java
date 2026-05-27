package ge.camora.erp.module.supplierdebt;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.SupplierCashPayment;
import ge.camora.erp.model.config.SupplierPaymentMapping;
import ge.camora.erp.model.dto.SupplierDebtOverviewDto;
import ge.camora.erp.model.dto.SupplierDebtRowDto;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.module.bankanalysis.BankTransaction;
import ge.camora.erp.module.bankanalysis.BogBusinessOnlineClient;
import ge.camora.erp.module.bankanalysis.TbcDbiClient;
import ge.camora.erp.module.rsge.RsgePurchaseWaybillService;
import ge.camora.erp.store.ConfigStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierDebtServiceTest {

    private final FakeRsgePurchaseWaybillService rsge = new FakeRsgePurchaseWaybillService();
    private final FakeBogBusinessOnlineClient bog = new FakeBogBusinessOnlineClient();
    private final FakeTbcDbiClient tbc = new FakeTbcDbiClient();
    private final FakeConfigStore configStore = new FakeConfigStore();
    private final FakeSupplierDebtSnapshotStore snapshotStore = new FakeSupplierDebtSnapshotStore();
    private final FakeRsgePurchaseLedgerStore rsgeLedgerStore = new FakeRsgePurchaseLedgerStore();
    private final SupplierDebtService service = new SupplierDebtService(
        rsge,
        bog,
        tbc,
        configStore,
        new CamoraProperties(),
        snapshotStore,
        rsgeLedgerStore
    );

    @Test
    void subtractsMatchedBogDebitsFromSupplierPurchases() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("300.00", "Supplier X", "123456789")
        );

        var result = service.analyze(from, to);

        assertThat(result.purchaseTotal()).isEqualByComparingTo("599.00");
        assertThat(result.bogPaidTotal()).isEqualByComparingTo("300.00");
        assertThat(result.tbcPaidTotal()).isEqualByComparingTo("0.00");
        assertThat(result.paidTotal()).isEqualByComparingTo("300.00");
        assertThat(result.debtTotal()).isEqualByComparingTo("299.00");
        assertThat(result.suppliers()).hasSize(1);
        assertThat(result.suppliers().get(0).debtLeft()).isEqualByComparingTo("299.00");
    }

    @Test
    void matchesPurchasesToBankDebitsBySellerTinAndBeneficiaryInn() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchaseWithSellerTin("WB-1", "Supplier X", "123456789", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("300.00", "Supplier X Display Name", "123456789")
        );

        var result = service.analyze(from, to);

        assertThat(result.suppliers()).hasSize(1);
        assertThat(result.suppliers().get(0).supplierKey()).isEqualTo("tin:123456789");
        assertThat(result.suppliers().get(0).bogPaidTotal()).isEqualByComparingTo("300.00");
        assertThat(result.unmatchedPaymentCount()).isZero();
    }

    @Test
    void doesNotAutoMatchSameNameWithDifferentTaxCode() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchaseWithSellerTin("WB-1", "Supplier X", "123456789", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("300.00", "Supplier X", "987654321")
        );

        var result = service.analyze(from, to);

        assertThat(result.suppliers().get(0).debtLeft()).isEqualByComparingTo("599.00");
        assertThat(result.unmatchedPaymentCount()).isEqualTo(1);
        assertThat(result.unmatchedPaymentTotal()).isEqualByComparingTo("300.00");
    }

    @Test
    void excludesUnmatchedBogDebitsFromSupplierDebt() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("300.00", "Other Supplier", "987654321")
        );

        var result = service.analyze(from, to);

        assertThat(result.paidTotal()).isEqualByComparingTo("0.00");
        assertThat(result.debtTotal()).isEqualByComparingTo("599.00");
        assertThat(result.unmatchedPaymentTotal()).isEqualByComparingTo("300.00");
        assertThat(result.unmatchedPaymentCount()).isEqualTo(1);
    }

    @Test
    void subtractsTbcAndCashPaymentsFromSupplierDebt() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        tbc.transactions = List.of(
            debit("150.00", "Supplier X", "123456789")
        );
        configStore.cashPayments = List.of(new SupplierCashPayment(
            "cash-1",
            "tin:123456789",
            "123456789",
            "Supplier X",
            LocalDate.of(2025, 1, 12),
            new BigDecimal("49.00"),
            "cash receipt",
            "user",
            LocalDateTime.now(),
            LocalDateTime.now()
        ));

        var result = service.analyze(from, to);

        assertThat(result.tbcPaidTotal()).isEqualByComparingTo("150.00");
        assertThat(result.cashPaidTotal()).isEqualByComparingTo("49.00");
        assertThat(result.paidTotal()).isEqualByComparingTo("199.00");
        assertThat(result.debtTotal()).isEqualByComparingTo("400.00");
        assertThat(result.suppliers().get(0).cashPaymentCount()).isEqualTo(1);
    }

    @Test
    void reusesCachedRemoteSourcesUntilForcedRefresh() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("300.00", "Supplier X", "123456789")
        );
        tbc.transactions = List.of(
            debit("100.00", "Supplier X", "123456789")
        );

        service.analyze(from, to);
        service.analyze(from, to);

        assertThat(rsge.fetchCount).isEqualTo(1);
        assertThat(bog.fetchCount).isEqualTo(1);
        assertThat(tbc.fetchCount).isEqualTo(1);

        service.analyze(from, to, true);

        assertThat(rsge.fetchCount).isEqualTo(2);
        assertThat(bog.fetchCount).isEqualTo(2);
        assertThat(tbc.fetchCount).isEqualTo(2);
    }

    @Test
    void fetchesBogSupplierDebtStatementsInBankAnalysisSizedWindows() {
        CamoraProperties properties = new CamoraProperties();
        properties.getBogApi().setStatementChunkDays(31);
        SupplierDebtService windowedService = new SupplierDebtService(
            rsge,
            bog,
            tbc,
            configStore,
            properties,
            snapshotStore,
            rsgeLedgerStore
        );
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 3, 5);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );

        windowedService.analyze(from, to, true);

        assertThat(bog.requests).containsExactly(
            new RequestedRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)),
            new RequestedRange(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28)),
            new RequestedRange(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 5))
        );
    }

    @Test
    void storesOverviewSnapshotWithoutTransactionDetails() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("300.00", "Supplier X", "123456789")
        );

        var result = service.overview(from, to, true);

        assertThat(result.snapshotGeneratedAt()).isNotNull();
        assertThat(result.suppliers()).hasSize(1);
        assertThat(result.suppliers().get(0).purchases()).isEmpty();
        assertThat(result.suppliers().get(0).payments()).isEmpty();
        assertThat(snapshotStore.load()).contains(result);
    }

    @Test
    void marksRsgeSuppliersAsNewOnlyAfterPreviousSnapshotExists() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );

        var firstSnapshot = service.overview(from, to, true);

        assertThat(firstSnapshot.suppliers()).allMatch(row -> !row.newFromRsge());

        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10)),
            purchase("WB-2", "(987654321) Supplier Y", "225.00", LocalDate.of(2025, 1, 12))
        );

        var secondSnapshot = service.overview(from, to, true);

        assertThat(secondSnapshot.suppliers())
            .filteredOn(row -> row.supplierKey().equals("tin:123456789"))
            .singleElement()
            .extracting(SupplierDebtRowDto::newFromRsge)
            .isEqualTo(false);
        assertThat(secondSnapshot.suppliers())
            .filteredOn(row -> row.supplierKey().equals("tin:987654321"))
            .singleElement()
            .extracting(SupplierDebtRowDto::newFromRsge)
            .isEqualTo(true);
    }

    @Test
    void servesFreshSnapshotWithoutStartingAnotherRefresh() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("300.00", "Supplier X", "123456789")
        );

        service.overview(from, to, true);
        var saved = service.overview(from, to, false);

        assertThat(saved.refreshInProgress()).isFalse();
        assertThat(rsge.fetchCount).isEqualTo(1);
        assertThat(bog.fetchCount).isEqualTo(1);
        assertThat(tbc.fetchCount).isEqualTo(1);
    }


    @Test
    void loadsSupplierTransactionDetailsSeparately() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("300.00", "Supplier X", "123456789")
        );

        var result = service.supplierTransactions("tin:123456789", from, to, false);

        assertThat(result.purchases()).hasSize(1);
        assertThat(result.payments()).hasSize(1);
    }

    @Test
    void groupsUnmatchedPaymentsByCounterpartyIdentifier() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("100.00", "Other Supplier", "987654321"),
            debit("125.00", "Other Supplier", "987654321")
        );

        var result = service.overview(from, to, true);

        assertThat(result.unmatchedPaymentGroups()).hasSize(1);
        assertThat(result.unmatchedPaymentGroups().get(0).transactionCount()).isEqualTo(2);
        assertThat(result.unmatchedPaymentGroups().get(0).amount()).isEqualByComparingTo("225.00");
        assertThat(result.unmatchedPaymentGroups().get(0).matchType()).isEqualTo("TIN");
        assertThat(result.unmatchedPaymentGroups().get(0).matchText()).isEqualTo("987654321");
    }

    @Test
    void exposesSourceFailureTechnicalTrace() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.failure = new RuntimeException("Failed to fetch BOG statement: Received RST_STREAM: Internal error");

        var result = service.analyze(from, to, true);

        var bogStatus = result.sourceStatuses().stream()
            .filter(status -> status.source().equals("BOG"))
            .findFirst()
            .orElseThrow();
        assertThat(bogStatus.status()).isEqualTo("FAILED");
        assertThat(bogStatus.message()).contains("RST_STREAM");
        assertThat(bogStatus.technicalDetails()).contains("RuntimeException");
        assertThat(bogStatus.technicalDetails()).contains("Received RST_STREAM");
    }

    @Test
    void warnsWhenRsgePurchaseLedgerDetectsChangedRows() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        rsgeLedgerStore.summary = new RsgePurchaseChangeSummary(
            0,
            1,
            1,
            0,
            8,
            LocalDateTime.of(2025, 1, 31, 12, 0),
            List.of("CHANGED date=2025-01-10, waybill=WB-1, tin=123456789, supplier=Supplier X, amount=599.00")
        );

        var result = service.analyze(from, to, true);

        var rsgeStatus = result.sourceStatuses().stream()
            .filter(status -> status.source().equals("RSGE"))
            .findFirst()
            .orElseThrow();
        assertThat(rsgeStatus.status()).isEqualTo("WARNING");
        assertThat(rsgeStatus.message()).contains("1 changed");
        assertThat(rsgeStatus.message()).contains("1 missing");
        assertThat(rsgeStatus.technicalDetails()).contains("Review warning");
    }

    private RsgeRecord purchase(String waybill, String supplierRaw, String amount, LocalDate date) {
        return new RsgeRecord(
            waybill,
            supplierRaw,
            "",
            "",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal(amount),
            LocalDateTime.of(date, java.time.LocalTime.NOON)
        );
    }

    private RsgeRecord purchaseWithSellerTin(String waybill, String supplierName, String sellerTin, String amount, LocalDate date) {
        return new RsgeRecord(
            waybill,
            supplierName,
            "",
            "",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal(amount),
            LocalDateTime.of(date, java.time.LocalTime.NOON),
            sellerTin,
            supplierName
        );
    }

    private BankTransaction debit(String amount, String counterparty, String inn) {
        return new BankTransaction(
            LocalDate.of(2025, 1, 11),
            "DEBIT",
            new BigDecimal(amount),
            "GEL",
            "GE00BG0000000000000000GEL",
            counterparty,
            inn,
            "GE00BG0000000000000001GEL",
            "payment",
            "REF-1",
            "{}"
        );
    }

    private static final class FakeRsgePurchaseWaybillService extends RsgePurchaseWaybillService {
        private List<RsgeRecord> records = List.of();
        private int fetchCount;

        private FakeRsgePurchaseWaybillService() {
            super(null, null);
        }

        @Override
        public List<RsgeRecord> fetchPurchaseRecords(LocalDate startDate, LocalDate endDate) {
            fetchCount++;
            return records;
        }
    }

    private static final class FakeBogBusinessOnlineClient extends BogBusinessOnlineClient {
        private List<BankTransaction> transactions = List.of();
        private final List<RequestedRange> requests = new ArrayList<>();
        private RuntimeException failure;
        private int fetchCount;

        private FakeBogBusinessOnlineClient() {
            super(new CamoraProperties(), new ObjectMapper());
        }

        @Override
        public List<BankTransaction> getStatement(LocalDate dateFrom, LocalDate dateTo) {
            fetchCount++;
            requests.add(new RequestedRange(dateFrom, dateTo));
            if (failure != null) {
                throw failure;
            }
            return transactions;
        }
    }

    private record RequestedRange(LocalDate dateFrom, LocalDate dateTo) {
    }

    private static final class FakeTbcDbiClient extends TbcDbiClient {
        private List<BankTransaction> transactions = List.of();
        private int fetchCount;

        private FakeTbcDbiClient() {
            super(new CamoraProperties());
        }

        @Override
        public List<BankTransaction> getAccountMovements(LocalDate dateFrom, LocalDate dateTo) {
            fetchCount++;
            return transactions;
        }
    }

    private static final class FakeConfigStore extends ConfigStore {
        private List<SupplierCashPayment> cashPayments = List.of();

        private FakeConfigStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }

        @Override
        public List<SupplierPaymentMapping> getSupplierPaymentMappings() {
            return List.of();
        }

        @Override
        public List<SupplierCashPayment> getSupplierCashPayments(LocalDate dateFrom, LocalDate dateTo) {
            return cashPayments;
        }
    }

    private static final class FakeRsgePurchaseLedgerStore extends RsgePurchaseLedgerStore {
        private RsgePurchaseChangeSummary summary = RsgePurchaseChangeSummary.empty();

        private FakeRsgePurchaseLedgerStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }

        @Override
        public RsgePurchaseChangeSummary compareAndSave(
            LocalDate dateFrom,
            LocalDate dateTo,
            List<RsgePurchaseFingerprint> currentFingerprints
        ) {
            return summary;
        }
    }

    private static final class FakeSupplierDebtSnapshotStore extends SupplierDebtSnapshotStore {
        private SupplierDebtOverviewDto snapshot;

        private FakeSupplierDebtSnapshotStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }

        @Override
        public Optional<SupplierDebtOverviewDto> load() {
            return Optional.ofNullable(snapshot);
        }

        @Override
        public SupplierDebtOverviewDto save(SupplierDebtOverviewDto snapshot) {
            this.snapshot = snapshot;
            return snapshot;
        }
    }
}
