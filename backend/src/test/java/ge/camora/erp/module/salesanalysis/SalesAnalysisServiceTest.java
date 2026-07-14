package ge.camora.erp.module.salesanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.SalesEvent;
import ge.camora.erp.model.dto.SalesAnalysisPeriodRow;
import ge.camora.erp.model.dto.SalesAnalysisProductPoint;
import ge.camora.erp.model.dto.SalesAnalysisProductSeries;
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

    @Test
    void productSeriesBucketsRowsCorrectlyAcrossMultipleProductsAndInterleavedDates() {
        CamoraProperties properties = new CamoraProperties();
        properties.getSalesAnalysis().setWeekStartsOnIso(1);
        properties.getSalesAnalysis().setMatchThreshold(new BigDecimal("0.01"));

        LocalDate dateFrom = LocalDate.of(2026, 2, 10);
        LocalDate dateTo = LocalDate.of(2026, 3, 20);

        // Rows deliberately interleave products and jump between the Feb and Mar month buckets.
        // Two rows fall outside [dateFrom, dateTo] and must be excluded from every bucket, yet
        // still count toward each product's gross-revenue total used for ordering the options.
        SalesAnalysisService service = new SalesAnalysisService(
            new FakeSalesParser(List.of(
                new SalesRow(LocalDate.of(2026, 2, 15), "Cola", new BigDecimal("100.00"), new BigDecimal("2"), new BigDecimal("30.00")),
                new SalesRow(LocalDate.of(2026, 3, 5), "Fanta", new BigDecimal("200.00"), new BigDecimal("4"), new BigDecimal("50.00")),
                new SalesRow(LocalDate.of(2026, 3, 10), "Cola", new BigDecimal("40.00"), new BigDecimal("1"), new BigDecimal("10.00")),
                new SalesRow(LocalDate.of(2026, 2, 20), "Fanta", new BigDecimal("60.00"), new BigDecimal("3"), new BigDecimal("15.00")),
                new SalesRow(LocalDate.of(2026, 2, 25), "Cola", new BigDecimal("25.00"), new BigDecimal("1"), new BigDecimal("5.00")),
                new SalesRow(LocalDate.of(2026, 3, 18), "Fanta", new BigDecimal("80.00"), new BigDecimal("2"), new BigDecimal("20.00")),
                new SalesRow(LocalDate.of(2026, 2, 5), "Cola", new BigDecimal("999.00"), new BigDecimal("9"), new BigDecimal("99.00")),
                new SalesRow(LocalDate.of(2026, 3, 25), "Fanta", new BigDecimal("777.00"), new BigDecimal("7"), new BigDecimal("77.00")))),
            new FakeConfigStore(),
            properties,
            new FakeCashFlowService(Map.of()));

        SalesAnalysisResult result = service.analyze(new ByteArrayInputStream(new byte[0]), dateFrom, dateTo);

        List<SalesAnalysisProductSeries> series = result.month().productSeries();
        // Cola total 1164.00 > Fanta total 1117.00, so options (and series) are Cola then Fanta.
        assertThat(series).extracting(SalesAnalysisProductSeries::productName).containsExactly("Cola", "Fanta");

        SalesAnalysisProductSeries cola = series.get(0);
        assertThat(cola.periods()).hasSize(2); // Feb bucket, Mar bucket
        SalesAnalysisProductPoint colaFeb = cola.periods().get(0);
        assertThat(colaFeb.dateFrom()).isEqualTo("2026-02-01");
        assertThat(colaFeb.grossRevenue()).isEqualByComparingTo("125.00"); // 100 + 25 (02-05 excluded)
        assertThat(colaFeb.quantity()).isEqualByComparingTo("3");
        assertThat(colaFeb.profit()).isEqualByComparingTo("35.00");
        assertThat(colaFeb.profitPercentage()).isEqualByComparingTo("0.28"); // 35 / 125
        SalesAnalysisProductPoint colaMar = cola.periods().get(1);
        assertThat(colaMar.dateFrom()).isEqualTo("2026-03-01");
        assertThat(colaMar.grossRevenue()).isEqualByComparingTo("40.00");
        assertThat(colaMar.quantity()).isEqualByComparingTo("1");
        assertThat(colaMar.profit()).isEqualByComparingTo("10.00");

        SalesAnalysisProductSeries fanta = series.get(1);
        assertThat(fanta.periods()).hasSize(2);
        SalesAnalysisProductPoint fantaFeb = fanta.periods().get(0);
        assertThat(fantaFeb.grossRevenue()).isEqualByComparingTo("60.00");
        assertThat(fantaFeb.quantity()).isEqualByComparingTo("3");
        assertThat(fantaFeb.profit()).isEqualByComparingTo("15.00");
        SalesAnalysisProductPoint fantaMar = fanta.periods().get(1);
        assertThat(fantaMar.grossRevenue()).isEqualByComparingTo("280.00"); // 200 + 80 (03-25 excluded)
        assertThat(fantaMar.quantity()).isEqualByComparingTo("6");
        assertThat(fantaMar.profit()).isEqualByComparingTo("70.00");
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
