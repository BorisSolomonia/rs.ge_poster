package ge.camora.erp.module.supplierdebt;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.SupplierPaymentMapping;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.module.bankanalysis.BankTransaction;
import ge.camora.erp.module.bankanalysis.BogBusinessOnlineClient;
import ge.camora.erp.module.rsge.RsgePurchaseWaybillService;
import ge.camora.erp.store.ConfigStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierDebtServiceTest {

    private final FakeRsgePurchaseWaybillService rsge = new FakeRsgePurchaseWaybillService();
    private final FakeBogBusinessOnlineClient bog = new FakeBogBusinessOnlineClient();
    private final FakeConfigStore configStore = new FakeConfigStore();
    private final SupplierDebtService service = new SupplierDebtService(rsge, bog, configStore);

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
        assertThat(result.paidTotal()).isEqualByComparingTo("300.00");
        assertThat(result.debtTotal()).isEqualByComparingTo("299.00");
        assertThat(result.suppliers()).hasSize(1);
        assertThat(result.suppliers().get(0).debtLeft()).isEqualByComparingTo("299.00");
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

        private FakeRsgePurchaseWaybillService() {
            super(null, null);
        }

        @Override
        public List<RsgeRecord> fetchPurchaseRecords(LocalDate startDate, LocalDate endDate) {
            return records;
        }
    }

    private static final class FakeBogBusinessOnlineClient extends BogBusinessOnlineClient {
        private List<BankTransaction> transactions = List.of();

        private FakeBogBusinessOnlineClient() {
            super(new CamoraProperties(), new ObjectMapper());
        }

        @Override
        public List<BankTransaction> getStatement(LocalDate dateFrom, LocalDate dateTo) {
            return transactions;
        }
    }

    private static final class FakeConfigStore extends ConfigStore {
        private FakeConfigStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }

        @Override
        public List<SupplierPaymentMapping> getSupplierPaymentMappings() {
            return List.of();
        }
    }
}
