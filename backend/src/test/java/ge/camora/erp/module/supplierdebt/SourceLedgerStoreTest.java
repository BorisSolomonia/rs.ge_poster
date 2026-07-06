package ge.camora.erp.module.supplierdebt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.module.bankanalysis.BankTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceLedgerStoreTest {

    @TempDir
    Path tempDir;

    private SourceLedgerStore store;
    private final List<LocalDate[]> fetchCalls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        CamoraProperties properties = new CamoraProperties();
        properties.setConfigDir(tempDir.toString());
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        store = new SourceLedgerStore(mapper, properties);
        store.init();
    }

    @Test
    void firstBankSyncBackfillsFullRange() {
        LocalDate from = LocalDate.now().minusDays(90);
        LocalDate to = LocalDate.now();
        List<BankTransaction> fetched = List.of(payment(LocalDate.now().minusDays(40), "500.00"));

        List<BankTransaction> result = store.syncBank("TBC", from, to, recordingFetcher(fetched));

        assertThat(fetchCalls).hasSize(1);
        assertThat(fetchCalls.get(0)[0]).isEqualTo(from);
        assertThat(result).hasSize(1);
    }

    @Test
    void secondBankSyncFetchesOnlyOverlapWindowAndKeepsOlderRows() {
        LocalDate from = LocalDate.now().minusDays(90);
        LocalDate to = LocalDate.now();
        LocalDate firstPaymentDate = LocalDate.now().minusDays(30);
        BankTransaction colaPayment = payment(firstPaymentDate, "500.00");
        BankTransaction olderPayment = payment(LocalDate.now().minusDays(60), "200.00");
        store.syncBank("TBC", from, to, recordingFetcher(List.of(olderPayment, colaPayment)));
        fetchCalls.clear();

        BankTransaction secondColaPayment = payment(LocalDate.now().minusDays(1), "600.00");
        // The delta fetch only sees rows inside the overlap window.
        List<BankTransaction> result = store.syncBank(
            "TBC", from, to, recordingFetcher(List.of(colaPayment, secondColaPayment)));

        assertThat(fetchCalls).hasSize(1);
        assertThat(fetchCalls.get(0)[0]).isEqualTo(firstPaymentDate.minusDays(3));
        assertThat(result).hasSize(3);
        assertThat(result).extracting(BankTransaction::amount)
            .containsExactlyInAnyOrder(new BigDecimal("200.00"), new BigDecimal("500.00"), new BigDecimal("600.00"));
    }

    @Test
    void purchaseSyncReplacesRecheckWindowAndKeepsOlderRows() {
        LocalDate from = LocalDate.now().minusDays(200);
        LocalDate to = LocalDate.now();
        RsgeRecord oldWaybill = waybill("WB-OLD", LocalDate.now().minusDays(100), "100.00");
        RsgeRecord recentWaybill = waybill("WB-NEW", LocalDate.now().minusDays(10), "50.00");
        store.syncPurchases(from, to, recordingPurchaseFetcher(List.of(oldWaybill, recentWaybill)));
        fetchCalls.clear();

        // RS.ge corrected the recent waybill; the recheck window is replaced.
        RsgeRecord corrected = waybill("WB-NEW", LocalDate.now().minusDays(10), "75.00");
        List<RsgeRecord> result = store.syncPurchases(from, to, recordingPurchaseFetcher(List.of(corrected)));

        assertThat(fetchCalls).hasSize(1);
        assertThat(fetchCalls.get(0)[0]).isEqualTo(LocalDate.now().minusDays(62));
        assertThat(result).hasSize(2);
        assertThat(result).extracting(RsgeRecord::totalPrice)
            .containsExactlyInAnyOrder(new BigDecimal("100.00"), new BigDecimal("75.00"));
    }

    @Test
    void extendingRangeEarlierTriggersFullBackfill() {
        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to = LocalDate.now();
        store.syncBank("BOG", from, to, recordingFetcher(List.of(payment(LocalDate.now().minusDays(5), "100.00"))));
        fetchCalls.clear();

        LocalDate earlierFrom = LocalDate.now().minusDays(120);
        store.syncBank("BOG", earlierFrom, to, recordingFetcher(List.of()));

        assertThat(fetchCalls).hasSize(1);
        assertThat(fetchCalls.get(0)[0]).isEqualTo(earlierFrom);
    }

    @Test
    void returnedRowsAreFilteredToRequestedRange() {
        LocalDate from = LocalDate.now().minusDays(90);
        LocalDate to = LocalDate.now();
        store.syncBank("TBC", from, to, recordingFetcher(List.of(
            payment(LocalDate.now().minusDays(40), "500.00"),
            payment(LocalDate.now().minusDays(2), "600.00")
        )));

        List<BankTransaction> narrow = store.syncBank(
            "TBC", LocalDate.now().minusDays(7), to, recordingFetcher(List.of(payment(LocalDate.now().minusDays(2), "600.00"))));

        assertThat(narrow).hasSize(1);
        assertThat(narrow.get(0).amount()).isEqualByComparingTo("600.00");
    }

    private SourceLedgerStore.SourceFetcher<BankTransaction> recordingFetcher(List<BankTransaction> result) {
        return (from, to) -> {
            fetchCalls.add(new LocalDate[]{from, to});
            return result;
        };
    }

    private SourceLedgerStore.SourceFetcher<RsgeRecord> recordingPurchaseFetcher(List<RsgeRecord> result) {
        return (from, to) -> {
            fetchCalls.add(new LocalDate[]{from, to});
            return result;
        };
    }

    private static BankTransaction payment(LocalDate date, String amount) {
        return new BankTransaction(
            date,
            BankTransaction.DEBIT,
            new BigDecimal(amount),
            "GEL",
            "GE00TB0000000000000000",
            "Cola Supplier",
            "123456789",
            "GE00XX0000000000000001",
            "payment",
            "DOC-" + date + "-" + amount,
            ""
        );
    }

    private static RsgeRecord waybill(String number, LocalDate date, String amount) {
        return new RsgeRecord(
            number,
            "(123456789) Supplier",
            "product",
            "kg",
            BigDecimal.ONE,
            new BigDecimal(amount),
            new BigDecimal(amount),
            date.atStartOfDay()
        );
    }
}
