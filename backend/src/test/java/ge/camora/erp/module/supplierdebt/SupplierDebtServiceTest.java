package ge.camora.erp.module.supplierdebt;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.StandaloneSupplier;
import ge.camora.erp.model.config.SupplierCashPayment;
import ge.camora.erp.model.config.SupplierMapping;
import ge.camora.erp.model.config.SupplierPaymentMapping;
import ge.camora.erp.model.dto.SupplierDebtOverviewDto;
import ge.camora.erp.model.dto.SupplierDebtRowDto;
import ge.camora.erp.model.dto.SupplierDebtSourceStatusDto;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierDebtServiceTest {

    private final FakeRsgePurchaseWaybillService rsge = new FakeRsgePurchaseWaybillService();
    private final FakeBogBusinessOnlineClient bog = new FakeBogBusinessOnlineClient();
    private final FakeTbcDbiClient tbc = new FakeTbcDbiClient();
    private final FakeConfigStore configStore = new FakeConfigStore();
    private final FakeSupplierDebtSnapshotStore snapshotStore = new FakeSupplierDebtSnapshotStore();
    private final FakeRsgePurchaseLedgerStore rsgeLedgerStore = new FakeRsgePurchaseLedgerStore();
    private final FakeSupplierCreditorStore creditorStore = new FakeSupplierCreditorStore();
    private final SupplierDebtService service = new SupplierDebtService(
        rsge,
        bog,
        tbc,
        configStore,
        new CamoraProperties(),
        new ObjectMapper(),
        snapshotStore,
        rsgeLedgerStore,
        creditorStore,
        new PassThroughSourceLedgerStore()
    );

    /** Bypasses ledger persistence so service tests exercise the fetchers directly. */
    private static final class PassThroughSourceLedgerStore extends SourceLedgerStore {
        private PassThroughSourceLedgerStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }

        @Override
        public List<BankTransaction> syncBank(String source, LocalDate rangeFrom, LocalDate rangeTo, SourceFetcher<BankTransaction> fetcher) {
            return fetcher.fetch(rangeFrom, rangeTo);
        }

        @Override
        public List<RsgeRecord> syncPurchases(LocalDate rangeFrom, LocalDate rangeTo, SourceFetcher<RsgeRecord> fetcher) {
            return fetcher.fetch(rangeFrom, rangeTo);
        }
    }

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
    void subtractsNegativeRsgeWaybillsFromSupplierPurchases() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchaseWithSellerTin("WB-POSITIVE", "Supplier X", "123456789", "16008.48", LocalDate.of(2025, 1, 10)),
            purchaseWithSellerTin("WB-CORRECTION", "Supplier X", "123456789", "-16000.00", LocalDate.of(2025, 1, 11))
        );

        var result = service.analyze(from, to);

        assertThat(result.purchaseTotal()).isEqualByComparingTo("8.48");
        assertThat(result.debtTotal()).isEqualByComparingTo("8.48");
        assertThat(result.suppliers()).hasSize(1);
        assertThat(result.suppliers().get(0).purchaseTotal()).isEqualByComparingTo("8.48");
        assertThat(result.suppliers().get(0).debtLeft()).isEqualByComparingTo("8.48");
        assertThat(result.suppliers().get(0).purchaseCount()).isEqualTo(2);
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
    void creditorOverviewListsKnownSuppliersWithoutFetchingRemoteSources() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        configStore.supplierMappings = List.of(supplierMapping("123456789", "Supplier X"));

        var overview = service.creditorOverview(from, to);

        assertThat(overview.totalSupplierCount()).isEqualTo(1);
        assertThat(overview.syncedSupplierCount()).isZero();
        assertThat(overview.approximateDebtTotal()).isEqualByComparingTo("0.00");
        assertThat(overview.suppliers().get(0).supplierKey()).isEqualTo("tin:123456789");
        assertThat(overview.suppliers().get(0).synced()).isFalse();
        assertThat(rsge.fetchCount).isZero();
        assertThat(bog.fetchCount).isZero();
        assertThat(tbc.fetchCount).isZero();
    }

    @Test
    void syncCreditorSupplierForcesFreshSourcesAndSavesOnlySelectedSupplier() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        configStore.supplierMappings = List.of(
            supplierMapping("123456789", "Supplier X"),
            supplierMapping("987654321", "Supplier Y")
        );
        rsge.records = List.of(
            purchaseWithSellerTin("WB-1", "Supplier X", "123456789", "599.00", LocalDate.of(2025, 1, 10)),
            purchaseWithSellerTin("WB-2", "Supplier Y", "987654321", "700.00", LocalDate.of(2025, 1, 11))
        );
        bog.transactions = List.of(
            debit("300.00", "Supplier X", "123456789")
        );

        var row = service.syncCreditorSupplier("tin:123456789", from, to);
        var overview = service.creditorOverview(from, to);

        assertThat(row.synced()).isTrue();
        assertThat(row.purchaseTotal()).isEqualByComparingTo("599.00");
        assertThat(row.paidTotal()).isEqualByComparingTo("300.00");
        assertThat(row.debtLeft()).isEqualByComparingTo("299.00");
        assertThat(row.lastSyncedAt()).isNotNull();
        assertThat(overview.syncedSupplierCount()).isEqualTo(1);
        assertThat(overview.approximateDebtTotal()).isEqualByComparingTo("299.00");
        assertThat(creditorStore.rows(from, to)).hasSize(1);
        assertThat(rsge.fetchCount).isEqualTo(1);
        assertThat(bog.fetchCount).isEqualTo(1);
        assertThat(tbc.fetchCount).isEqualTo(1);
    }

    @Test
    void syncCreditorSupplierIgnoresBankIncomeRows() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        configStore.supplierMappings = List.of(
            supplierMapping("123456789", "Supplier X")
        );
        rsge.records = List.of(
            purchaseWithSellerTin("WB-1", "Supplier X", "123456789", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            credit("300.00", "Supplier X", "123456789"),
            debit("100.00", "Supplier X", "123456789")
        );

        var row = service.syncCreditorSupplier("tin:123456789", from, to);
        var overview = service.creditorOverview(from, to);

        assertThat(row.synced()).isTrue();
        assertThat(row.purchaseTotal()).isEqualByComparingTo("599.00");
        assertThat(row.bogPaidTotal()).isEqualByComparingTo("100.00");
        assertThat(row.bogPaymentCount()).isEqualTo(1);
        assertThat(row.paidTotal()).isEqualByComparingTo("100.00");
        assertThat(row.debtLeft()).isEqualByComparingTo("499.00");
        assertThat(row.payments())
            .singleElement()
            .extracting(payment -> payment.amount())
            .isEqualTo(new BigDecimal("100.00"));
        assertThat(overview.approximateDebtTotal()).isEqualByComparingTo("499.00");
    }

    @Test
    void creditorActiveFlagIsSeparateAndMovesUncheckedSuppliersToBottom() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        configStore.supplierMappings = List.of(
            supplierMapping("123456789", "Supplier X"),
            supplierMapping("987654321", "Supplier Y")
        );

        var overview = service.setCreditorActive("tin:123456789", false, from, to);

        assertThat(overview.suppliers()).extracting("supplierKey")
            .containsExactly("tin:987654321", "tin:123456789");
        assertThat(overview.suppliers().get(1).active()).isFalse();
        assertThat(configStore.supplierMappings.get(0).isRsgeExcluded()).isFalse();
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
            new ObjectMapper(),
            snapshotStore,
            rsgeLedgerStore,
        creditorStore,
            new PassThroughSourceLedgerStore()
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
    void fetchesBogSupplierDebtStatementsInConfiguredSmallWindows() {
        CamoraProperties properties = new CamoraProperties();
        properties.getSupplierDebt().setBogStatementWindowDays(1);
        properties.getBogApi().setStatementChunkDays(31);
        SupplierDebtService windowedService = new SupplierDebtService(
            rsge,
            bog,
            tbc,
            configStore,
            properties,
            new ObjectMapper(),
            snapshotStore,
            rsgeLedgerStore,
        creditorStore,
            new PassThroughSourceLedgerStore()
        );
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 3);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 1))
        );

        windowedService.analyze(from, to, true);

        assertThat(bog.requests).containsExactly(
            new RequestedRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)),
            new RequestedRange(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 2)),
            new RequestedRange(LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 3))
        );
    }

    @Test
    void startsSourceRefreshInBackgroundAndReturnsCurrentStateImmediately() {
        SupplierDebtService asyncService = new SupplierDebtService(
            rsge,
            bog,
            tbc,
            configStore,
            new CamoraProperties(),
            new ObjectMapper(),
            snapshotStore,
            rsgeLedgerStore,
        creditorStore,
            new PassThroughSourceLedgerStore()
        );
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.blockFetch = new CountDownLatch(1);

        var result = asyncService.startAsyncRefresh(from, to);
        var pollingResult = asyncService.overview(from, to, false);
        rsge.blockFetch.countDown();

        assertThat(result.refreshInProgress()).isTrue();
        assertThat(result.suppliers()).isEmpty();
        assertThat(pollingResult.refreshInProgress()).isTrue();
        assertThat(pollingResult.suppliers()).isEmpty();
    }

    @Test
    void startsMissingSnapshotOverviewInBackgroundInsteadOfBlockingDateChanges() {
        SupplierDebtService asyncService = new SupplierDebtService(
            rsge,
            bog,
            tbc,
            configStore,
            new CamoraProperties(),
            new ObjectMapper(),
            snapshotStore,
            rsgeLedgerStore,
        creditorStore,
            new PassThroughSourceLedgerStore()
        );
        LocalDate from = LocalDate.of(2026, 5, 27);
        LocalDate to = LocalDate.of(2026, 5, 27);
        rsge.blockFetch = new CountDownLatch(1);

        var result = asyncService.overview(from, to, false);
        rsge.blockFetch.countDown();

        assertThat(result.refreshInProgress()).isTrue();
        assertThat(result.dateFrom()).isEqualTo(from);
        assertThat(result.dateTo()).isEqualTo(to);
        assertThat(result.suppliers()).isEmpty();
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
    void syncNowForcesFreshSourcesAndReplacesSavedSnapshot() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("300.00", "Supplier X", "123456789")
        );

        var saved = service.overview(from, to, true);
        rsge.records = List.of(
            purchase("WB-2", "(123456789) Supplier X", "800.00", LocalDate.of(2025, 1, 11))
        );
        bog.transactions = List.of(
            debit("500.00", "Supplier X", "123456789")
        );

        var staleRead = service.overview(from, to, false);
        var synced = service.syncNow(from, to);

        assertThat(staleRead.purchaseTotal()).isEqualByComparingTo(saved.purchaseTotal());
        assertThat(synced.purchaseTotal()).isEqualByComparingTo("800.00");
        assertThat(synced.paidTotal()).isEqualByComparingTo("500.00");
        assertThat(synced.debtTotal()).isEqualByComparingTo("300.00");
        assertThat(rsge.fetchCount).isEqualTo(2);
        assertThat(bog.fetchCount).isEqualTo(2);
        assertThat(tbc.fetchCount).isEqualTo(2);
        assertThat(snapshotStore.load()).contains(synced);
    }


    @Test
    void invalidatesSavedSnapshotWhenRsgeWaybillTypesMarkerIsMissing() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        SupplierDebtOverviewDto saved = service.overview(from, to, true);
        snapshotStore.snapshot = withSourceStatuses(saved, List.of(new SupplierDebtSourceStatusDto(
            "RSGE",
            "OK",
            "fresh source data; no RS.ge row changes",
            "",
            1,
            new BigDecimal("599.00")
        )));
        rsge.blockFetch = new CountDownLatch(1);

        var result = service.overview(from, to, false);
        rsge.blockFetch.countDown();

        assertThat(result.refreshInProgress()).isTrue();
        assertThat(result.suppliers()).isEmpty();
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

        service.analyze(from, to, true);
        var result = service.supplierTransactions("tin:123456789", from, to, false);

        assertThat(result.purchases()).hasSize(1);
        assertThat(result.payments()).hasSize(1);
        assertThat(rsge.fetchCount).isEqualTo(1);
        assertThat(bog.fetchCount).isEqualTo(1);
        assertThat(tbc.fetchCount).isEqualTo(1);
    }

    @Test
    void returnsSavedSupplierSummaryImmediatelyWhenTransactionCachesAreCold() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("300.00", "Supplier X", "123456789")
        );
        service.overview(from, to, true);

        FakeRsgePurchaseWaybillService restartedRsge = new FakeRsgePurchaseWaybillService();
        restartedRsge.blockFetch = new CountDownLatch(1);
        SupplierDebtService restartedService = new SupplierDebtService(
            restartedRsge,
            new FakeBogBusinessOnlineClient(),
            new FakeTbcDbiClient(),
            configStore,
            new CamoraProperties(),
            new ObjectMapper(),
            snapshotStore,
            rsgeLedgerStore,
        creditorStore,
            new PassThroughSourceLedgerStore()
        );

        var result = restartedService.supplierTransactions("tin:123456789", from, to, false);
        restartedRsge.blockFetch.countDown();

        assertThat(result.supplierName()).isEqualTo("Supplier X");
        assertThat(result.debtLeft()).isEqualByComparingTo("299.00");
        assertThat(result.purchases()).isEmpty();
        assertThat(result.payments()).isEmpty();
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
    void doesNotOverwriteSavedSnapshotWhenSourceRefreshFails() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.records = List.of(
            purchase("WB-1", "(123456789) Supplier X", "599.00", LocalDate.of(2025, 1, 10))
        );
        bog.transactions = List.of(
            debit("300.00", "Supplier X", "123456789")
        );
        SupplierDebtOverviewDto goodSnapshot = service.overview(from, to, true);

        bog.failure = new RuntimeException("BOG source unavailable");

        assertThatThrownBy(() -> service.overview(from, to, true))
            .hasMessageContaining("snapshot was not overwritten")
            .hasMessageContaining("BOG");
        assertThat(snapshotStore.load()).contains(goodSnapshot);
    }

    @Test
    void exposesRawPayloadsForSupplierDebtSources() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        rsge.rawWaybills = List.of(Map.of(
            "ID", "WB-1",
            "SELLER_NAME", "Supplier X",
            "SELLER_TIN", "123456789",
            "FULL_AMOUNT", "25.50"
        ), Map.of(
            "ID", "WB-2",
            "WAYBILL_NUMBER", "VISIBLE-WB-2",
            "TYPE", "5",
            "SELLER_NAME", "Supplier X",
            "SELLER_TIN", "123456789",
            "FULL_AMOUNT", "20.00"
        ));
        bog.transactions = List.of(
            debit("300.00", "Supplier X", "123456789")
        );

        var result = service.rawPayloads(from, to, true);

        assertThat(result.sources()).hasSize(3);
        var rsgeSource = result.sources().stream()
            .filter(source -> source.source().equals("RSGE"))
            .findFirst()
            .orElseThrow();
        var bogSource = result.sources().stream()
            .filter(source -> source.source().equals("BOG"))
            .findFirst()
            .orElseThrow();
        assertThat(rsgeSource.recordCount()).isEqualTo(2);
        assertThat(rsgeSource.total()).isEqualByComparingTo("5.50");
        assertThat(rsgeSource.payloads().get(0).rawPayload()).contains("WB-1");
        assertThat(rsgeSource.payloads().get(1).amount()).isEqualByComparingTo("-20.00");
        assertThat(rsgeSource.payloads().get(1).reference()).isEqualTo("VISIBLE-WB-2");
        assertThat(bogSource.recordCount()).isEqualTo(1);
        assertThat(bogSource.payloads().get(0).rawPayload()).isEqualTo("{}");
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

    private SupplierMapping supplierMapping(String tin, String name) {
        SupplierMapping mapping = new SupplierMapping();
        mapping.setPosterAlias(name);
        mapping.setRsgeRawValue(name);
        mapping.setRsgeTaxId(tin);
        mapping.setRsgeOfficialName(name);
        return mapping;
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

    private BankTransaction credit(String amount, String counterparty, String inn) {
        return new BankTransaction(
            LocalDate.of(2025, 1, 11),
            "CREDIT",
            new BigDecimal(amount),
            "GEL",
            "GE00BG0000000000000000GEL",
            counterparty,
            inn,
            "GE00BG0000000000000001GEL",
            "incoming payment",
            "REF-INCOME-1",
            "{}"
        );
    }

    private SupplierDebtOverviewDto withSourceStatuses(
        SupplierDebtOverviewDto overview,
        List<SupplierDebtSourceStatusDto> sourceStatuses
    ) {
        return new SupplierDebtOverviewDto(
            overview.dateFrom(),
            overview.dateTo(),
            overview.purchaseTotal(),
            overview.bogPaidTotal(),
            overview.tbcPaidTotal(),
            overview.cashPaidTotal(),
            overview.bankPaidTotal(),
            overview.paidTotal(),
            overview.debtTotal(),
            overview.supplierCount(),
            overview.unmatchedPaymentTotal(),
            overview.unmatchedPaymentCount(),
            overview.suppliers(),
            overview.unmatchedPayments(),
            overview.mappings(),
            sourceStatuses,
            overview.unmatchedPaymentGroups(),
            overview.snapshotGeneratedAt(),
            overview.refreshInProgress(),
            overview.lastRefreshStartedAt(),
            overview.lastRefreshCompletedAt(),
            overview.lastRefreshError(),
            overview.latestAudit()
        );
    }

    private static final class FakeRsgePurchaseWaybillService extends RsgePurchaseWaybillService {
        private List<RsgeRecord> records = List.of();
        private List<Map<String, Object>> rawWaybills = List.of();
        private CountDownLatch blockFetch;
        private int fetchCount;

        private FakeRsgePurchaseWaybillService() {
            super(null, null);
        }

        @Override
        public List<RsgeRecord> fetchPurchaseRecords(LocalDate startDate, LocalDate endDate) {
            fetchCount++;
            if (blockFetch != null) {
                try {
                    blockFetch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(exception);
                }
            }
            return records;
        }

        @Override
        public List<Map<String, Object>> fetchRawPurchaseWaybills(LocalDate startDate, LocalDate endDate) {
            fetchCount++;
            return rawWaybills;
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
        private List<SupplierMapping> supplierMappings = List.of();
        private List<StandaloneSupplier> standaloneSuppliers = List.of();

        private FakeConfigStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }

        @Override
        public List<SupplierMapping> getAllSupplierMappings() {
            return supplierMappings;
        }

        @Override
        public List<StandaloneSupplier> getUnmappedSuppliers() {
            return standaloneSuppliers;
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

    private static final class FakeSupplierCreditorStore extends SupplierCreditorStore {
        private final List<SavedSupplierCreditor> rows = new ArrayList<>();
        private final List<SupplierCreditorPreference> preferences = new ArrayList<>();

        private FakeSupplierCreditorStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }

        @Override
        public List<SavedSupplierCreditor> rows(LocalDate dateFrom, LocalDate dateTo) {
            return rows.stream()
                .filter(row -> dateFrom.equals(row.dateFrom()) && dateTo.equals(row.dateTo()))
                .toList();
        }

        @Override
        public Optional<SavedSupplierCreditor> find(LocalDate dateFrom, LocalDate dateTo, String supplierKey) {
            return rows(dateFrom, dateTo).stream()
                .filter(row -> row.supplierKey().equals(supplierKey))
                .findFirst();
        }

        @Override
        public SupplierCreditorPreference preference(String supplierKey) {
            return preferences.stream()
                .filter(preference -> preference.supplierKey().equals(supplierKey))
                .findFirst()
                .orElseGet(() -> new SupplierCreditorPreference(supplierKey, true));
        }

        @Override
        public SavedSupplierCreditor save(LocalDate dateFrom, LocalDate dateTo, SupplierDebtRowDto row, LocalDateTime syncedAt, String syncError) {
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
            return saved;
        }

        @Override
        public SupplierCreditorPreference setActive(String supplierKey, boolean active) {
            preferences.removeIf(preference -> preference.supplierKey().equals(supplierKey));
            SupplierCreditorPreference saved = new SupplierCreditorPreference(supplierKey, active);
            preferences.add(saved);
            return saved;
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
