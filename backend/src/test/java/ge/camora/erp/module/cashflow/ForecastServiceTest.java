package ge.camora.erp.module.cashflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.dto.BudgetForecastCellDto;
import ge.camora.erp.model.dto.BudgetForecastDto;
import ge.camora.erp.model.dto.BudgetForecastRowDto;
import ge.camora.erp.model.dto.BudgetForecastTotalDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full engine pipeline: canned categorized history → seasonal forecast → override
 * merge → per-period totals. Uses fakes so no bank sync / file IO is involved.
 */
class ForecastServiceTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 7, 15);
    private static final String TARGET_MONTH = "2026-08";

    @Test
    void forecastsPerCategoryMergesOverridesAndTotals() {
        Map<String, Map<LocalDate, BigDecimal>> daily = new LinkedHashMap<>();
        daily.put("sales", series(
            entry(2025, 8, 15, 1000), // prior-year point for 2026-08
            entry(2025, 4, 10, 100), entry(2025, 5, 10, 100), entry(2025, 6, 10, 100),
            entry(2026, 4, 10, 100), entry(2026, 5, 10, 100), entry(2026, 6, 10, 100)));
        daily.put("rent", series(
            entry(2025, 8, 1, 200),
            entry(2025, 4, 1, 50), entry(2025, 5, 1, 50), entry(2025, 6, 1, 50),
            entry(2026, 4, 1, 50), entry(2026, 5, 1, 50), entry(2026, 6, 1, 50)));
        daily.put(CashFlowCategoryDefaults.UNCATEGORIZED_INFLOW, series(entry(2026, 6, 20, 500)));

        FakeOverrideStore overrides = new FakeOverrideStore();
        ForecastService service = new ForecastService(new FakeCashFlowService(daily), new FakeCategoryStore(), overrides);

        BudgetForecastDto forecast = service.forecast(AS_OF);

        // sales 2026-08: seasonal 1000*1.0, EWMA[100,100,100]=100 → 0.7*1000 + 0.3*100 = 730
        BudgetForecastCellDto salesAug = cell(forecast, "sales", TARGET_MONTH);
        assertThat(salesAug.baseline()).isEqualByComparingTo("730.00");
        assertThat(salesAug.overridden()).isFalse();
        assertThat(salesAug.basis()).isEqualTo("SEASONAL_GROWTH");

        // rent 2026-08: 0.7*200 + 0.3*50 = 155
        assertThat(cell(forecast, "rent", TARGET_MONTH).baseline()).isEqualByComparingTo("155.00");

        BudgetForecastTotalDto augTotal = total(forecast, TARGET_MONTH);
        assertThat(augTotal.inflowBaseline()).isEqualByComparingTo("730.00");
        assertThat(augTotal.outflowBaseline()).isEqualByComparingTo("155.00");
        assertThat(augTotal.netBaseline()).isEqualByComparingTo("575.00");

        // uncategorized history present → the "categorize your income" hint should fire
        assertThat(forecast.historyUncategorized()).isTrue();

        // Manager overrides the sales number; baseline is preserved, amount + net follow the override.
        service.setOverride("MONTH", TARGET_MONTH, "sales", new BigDecimal("900.00"));
        BudgetForecastDto edited = service.forecast(AS_OF);
        BudgetForecastCellDto editedCell = cell(edited, "sales", TARGET_MONTH);
        assertThat(editedCell.overridden()).isTrue();
        assertThat(editedCell.baseline()).isEqualByComparingTo("730.00");
        assertThat(editedCell.amount()).isEqualByComparingTo("900.00");
        assertThat(total(edited, TARGET_MONTH).netAmount()).isEqualByComparingTo("745.00"); // 900 - 155

        // Reset restores the algorithm baseline.
        service.clearOverride("MONTH", TARGET_MONTH, "sales");
        assertThat(cell(service.forecast(AS_OF), "sales", TARGET_MONTH).overridden()).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static BudgetForecastCellDto cell(BudgetForecastDto forecast, String categoryId, String periodKey) {
        BudgetForecastRowDto row = forecast.rows().stream()
            .filter(r -> r.categoryId().equals(categoryId)).findFirst().orElseThrow();
        return row.cells().stream().filter(c -> c.periodKey().equals(periodKey)).findFirst().orElseThrow();
    }

    private static BudgetForecastTotalDto total(BudgetForecastDto forecast, String periodKey) {
        return forecast.totals().stream().filter(t -> t.periodKey().equals(periodKey)).findFirst().orElseThrow();
    }

    @SafeVarargs
    private static Map<LocalDate, BigDecimal> series(Map.Entry<LocalDate, BigDecimal>... entries) {
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : entries) {
            map.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
        }
        return map;
    }

    private static Map.Entry<LocalDate, BigDecimal> entry(int year, int month, int day, int amount) {
        return Map.entry(LocalDate.of(year, month, day), BigDecimal.valueOf(amount));
    }

    private static final class FakeCashFlowService extends CashFlowService {
        private final Map<String, Map<LocalDate, BigDecimal>> daily;

        private FakeCashFlowService(Map<String, Map<LocalDate, BigDecimal>> daily) {
            super(null, null, null, null, null, null, new CamoraProperties());
            this.daily = daily;
        }

        @Override
        public Map<String, Map<LocalDate, BigDecimal>> categorizedDailySeries(LocalDate dateFrom, LocalDate dateTo) {
            return daily;
        }
    }

    private static final class FakeCategoryStore extends CashFlowCategoryStore {
        private FakeCategoryStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }

        @Override
        public List<CashFlowCategory> list() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
            return List.of(
                new CashFlowCategory("sales", "sales", CashFlowSection.OPERATING, CashFlowDirection.INFLOW,
                    "რეალიზაცია", null, 1, true, now, now),
                new CashFlowCategory("rent", "rent", CashFlowSection.OPERATING, CashFlowDirection.OUTFLOW,
                    "ქირა", null, 3, true, now, now));
        }

        @Override
        public Optional<CashFlowCategory> findById(String id) {
            return list().stream().filter(category -> category.getId().equals(id)).findFirst();
        }
    }

    private static final class FakeOverrideStore extends ForecastOverrideStore {
        private final Map<String, BigDecimal> memory = new LinkedHashMap<>();

        private FakeOverrideStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }

        @Override
        public Map<String, BigDecimal> snapshot() {
            return new LinkedHashMap<>(memory);
        }

        @Override
        public void put(String periodType, String categoryId, String periodKey, BigDecimal amount) {
            memory.put(ForecastOverrideStore.key(periodType, categoryId, periodKey), amount);
        }

        @Override
        public void remove(String periodType, String categoryId, String periodKey) {
            memory.remove(ForecastOverrideStore.key(periodType, categoryId, periodKey));
        }
    }
}
