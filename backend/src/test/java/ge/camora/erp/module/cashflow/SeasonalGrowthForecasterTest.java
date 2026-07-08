package ge.camora.erp.module.cashflow;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-Path math. Verifies the seasonal×growth ⊕ EWMA blend and the degradation
 * ladder (trend-only → average → zero) — the core of the forecasting engine.
 */
class SeasonalGrowthForecasterTest {

    private static final YearMonth CURRENT_MONTH = YearMonth.of(2026, 7);

    @Test
    void growthFactorIsRecentOverPriorYear() {
        Map<YearMonth, BigDecimal> monthly = new LinkedHashMap<>();
        // last 3 completed months (Apr–Jun 2026) sum 300; same months 2025 sum 150 → ratio 2.0
        monthly.put(YearMonth.of(2026, 6), bd(120));
        monthly.put(YearMonth.of(2026, 5), bd(100));
        monthly.put(YearMonth.of(2026, 4), bd(80));
        monthly.put(YearMonth.of(2025, 6), bd(60));
        monthly.put(YearMonth.of(2025, 5), bd(50));
        monthly.put(YearMonth.of(2025, 4), bd(40));

        assertThat(SeasonalGrowthForecaster.growthFactor(monthly, CURRENT_MONTH)).isEqualByComparingTo("2.00");
    }

    @Test
    void growthFactorIsNullWithoutPriorYear() {
        Map<YearMonth, BigDecimal> monthly = new LinkedHashMap<>();
        monthly.put(YearMonth.of(2026, 6), bd(120));
        assertThat(SeasonalGrowthForecaster.growthFactor(monthly, CURRENT_MONTH)).isNull();
    }

    @Test
    void monthBlendsSeasonalWithRecentTrend() {
        Map<YearMonth, BigDecimal> monthly = new LinkedHashMap<>();
        monthly.put(YearMonth.of(2025, 8), bd(100)); // prior-year point for target 2026-08
        monthly.put(YearMonth.of(2026, 4), bd(40));  // recent completed months → EWMA
        monthly.put(YearMonth.of(2026, 5), bd(50));
        monthly.put(YearMonth.of(2026, 6), bd(60));

        SeasonalGrowthForecaster.Point point = SeasonalGrowthForecaster.forecastMonth(
            monthly, YearMonth.of(2026, 8), CURRENT_MONTH, BigDecimal.ONE);

        // EWMA(alpha .5) over [40,50,60] = 52.5 ; 0.7*100 + 0.3*52.5 = 85.75
        assertThat(point.basis()).isEqualTo(SeasonalGrowthForecaster.Basis.SEASONAL_GROWTH);
        assertThat(point.amount()).isEqualByComparingTo("85.75");
    }

    @Test
    void monthFallsBackToTrendWhenNoPriorYear() {
        Map<YearMonth, BigDecimal> monthly = new LinkedHashMap<>();
        monthly.put(YearMonth.of(2026, 4), bd(40));
        monthly.put(YearMonth.of(2026, 5), bd(50));
        monthly.put(YearMonth.of(2026, 6), bd(60));

        SeasonalGrowthForecaster.Point point = SeasonalGrowthForecaster.forecastMonth(
            monthly, YearMonth.of(2026, 8), CURRENT_MONTH, null);

        assertThat(point.basis()).isEqualTo(SeasonalGrowthForecaster.Basis.TREND);
        assertThat(point.amount()).isEqualByComparingTo("52.50");
    }

    @Test
    void monthFallsBackToAverageThenZero() {
        Map<YearMonth, BigDecimal> onlyOld = new LinkedHashMap<>();
        onlyOld.put(YearMonth.of(2025, 1), bd(30)); // no prior-year for target, not in recent window
        SeasonalGrowthForecaster.Point average = SeasonalGrowthForecaster.forecastMonth(
            onlyOld, YearMonth.of(2026, 8), CURRENT_MONTH, null);
        assertThat(average.basis()).isEqualTo(SeasonalGrowthForecaster.Basis.AVERAGE);
        assertThat(average.amount()).isEqualByComparingTo("30.00");

        SeasonalGrowthForecaster.Point none = SeasonalGrowthForecaster.forecastMonth(
            new LinkedHashMap<>(), YearMonth.of(2026, 8), CURRENT_MONTH, null);
        assertThat(none.basis()).isEqualTo(SeasonalGrowthForecaster.Basis.NONE);
        assertThat(none.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void weekBlendsSeasonalWithRecentTrend() {
        LocalDate currentWeekStart = LocalDate.of(2026, 7, 6);
        LocalDate targetWeekStart = LocalDate.of(2026, 8, 3);
        Map<LocalDate, BigDecimal> weekly = new LinkedHashMap<>();
        weekly.put(targetWeekStart.minusWeeks(52), bd(200)); // same week last year
        weekly.put(currentWeekStart.minusWeeks(4), bd(10));
        weekly.put(currentWeekStart.minusWeeks(3), bd(20));
        weekly.put(currentWeekStart.minusWeeks(2), bd(30));
        weekly.put(currentWeekStart.minusWeeks(1), bd(40));

        SeasonalGrowthForecaster.Point point = SeasonalGrowthForecaster.forecastWeek(
            weekly, targetWeekStart, currentWeekStart, BigDecimal.ONE);

        // EWMA over [10,20,30,40] = 31.25 ; 0.7*200 + 0.3*31.25 = 149.375 → 149.38
        assertThat(point.basis()).isEqualTo(SeasonalGrowthForecaster.Basis.SEASONAL_GROWTH);
        assertThat(point.amount()).isEqualByComparingTo("149.38");
    }

    private static BigDecimal bd(int value) {
        return BigDecimal.valueOf(value);
    }
}
