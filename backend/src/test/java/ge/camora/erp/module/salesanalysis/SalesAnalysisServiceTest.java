package ge.camora.erp.module.salesanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.SalesEvent;
import ge.camora.erp.model.dto.SalesAnalysisPeriodRow;
import ge.camora.erp.model.dto.SalesAnalysisResult;
import ge.camora.erp.module.cashflow.CashFlowService;
import ge.camora.erp.store.ConfigStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SalesAnalysisServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 2, 2); // Monday
    private static final LocalDate D2 = LocalDate.of(2026, 2, 3);

    @Test
    void bankIncomeIsSourcedFromCashFlowBackendAndFeedsMetrics() {
        CamoraProperties properties = new CamoraProperties();
        properties.getSalesAnalysis().setWeekStartsOnIso(1);
        properties.getSalesAnalysis().setMatchThreshold(new BigDecimal("0.01"));

        SalesAnalysisService service = new SalesAnalysisService(
            new FakeSalesParser(List.of(
                new SalesRow(D1, "Cola", new BigDecimal("100.00"), BigDecimal.ONE, BigDecimal.ZERO),
                new SalesRow(D2, "Cola", new BigDecimal("40.00"), BigDecimal.ONE, BigDecimal.ZERO))),
            new FakeConfigStore(),
            properties,
            new FakeCashFlowService(Map.of(
                "TBC", Map.of(D1, new BigDecimal("90.00")),
                "BOG", Map.of(D1, new BigDecimal("30.00")))));

        SalesAnalysisResult result = service.analyze(new ByteArrayInputStream(new byte[0]), D1, D2);

        SalesAnalysisPeriodRow day1 = result.day().periods().stream()
            .filter(period -> period.dateFrom().equals(D1.toString()))
            .findFirst().orElseThrow();
        assertThat(day1.sales()).isEqualByComparingTo("100.00");
        assertThat(day1.tbcIncome()).isEqualByComparingTo("90.00");
        assertThat(day1.bogIncome()).isEqualByComparingTo("30.00");
        assertThat(day1.bankIncome()).isEqualByComparingTo("120.00");
        assertThat(day1.variance()).isEqualByComparingTo("20.00"); // bank - sales

        assertThat(result.month().summary().totalSales().current()).isEqualByComparingTo("140.00");
        assertThat(result.month().summary().totalBankIncome().current()).isEqualByComparingTo("120.00");
    }

    private static final class FakeSalesParser extends SalesSpreadsheetParser {
        private final List<SalesRow> rows;

        private FakeSalesParser(List<SalesRow> rows) {
            super(new CamoraProperties());
            this.rows = rows;
        }

        @Override
        public List<SalesRow> parse(InputStream stream) {
            return rows;
        }
    }

    private static final class FakeConfigStore extends ConfigStore {
        private FakeConfigStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }

        @Override
        public void registerSalesProduct(String displayName) {
            // no-op in tests
        }

        @Override
        public boolean isSalesProductExcluded(String displayName) {
            return false;
        }

        @Override
        public List<SalesEvent> getSalesEvents() {
            return List.of();
        }
    }

    private static final class FakeCashFlowService extends CashFlowService {
        private final Map<String, Map<LocalDate, BigDecimal>> income;

        private FakeCashFlowService(Map<String, Map<LocalDate, BigDecimal>> income) {
            super(null, null, null, null, null, null, new CamoraProperties());
            this.income = income;
        }

        @Override
        public Map<String, Map<LocalDate, BigDecimal>> operatingIncomeByBankAndDate(LocalDate dateFrom, LocalDate dateTo) {
            return income;
        }
    }
}
