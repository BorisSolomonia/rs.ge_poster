package ge.camora.erp.module.cashflow;

import ge.camora.erp.util.MoneyUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The "Golden Path" forecaster (see {@code docs/budgeting/forecasting-research.md}):
 * a per-category <b>seasonal-naive × growth</b> baseline blended with a recent-trend
 * <b>EWMA</b>, with a documented graceful-degradation ladder. Pure functions over a
 * category's historical period series — no state, no Spring, no external libraries —
 * so it is trivially unit-testable and cheap to run.
 *
 * <p>Chosen because the app has only ~1.5 seasonal cycles of history: methods that
 * <i>estimate</i> a yearly shape (SARIMA / Holt-Winters seasonal / Prophet) would be
 * fitting one year of noise. Using the single real same-period-last-year observation
 * directly, scaled by a bounded growth factor, is both the most honest and the most
 * explainable baseline — which matters because every number here is user-overridable.
 */
final class SeasonalGrowthForecaster {

    /** How the baseline for a single period was derived (drives the UI's "basis" hint). */
    enum Basis {
        SEASONAL_GROWTH,   // same period last year × growth, blended with recent trend
        TREND,             // recent-period EWMA only (no prior-year point)
        AVERAGE,           // flat average of what little history exists
        NONE               // no history at all → 0, user must enter manually
    }

    record Point(BigDecimal amount, Basis basis) {
        static Point of(BigDecimal amount, Basis basis) {
            return new Point(MoneyUtil.round(amount == null ? BigDecimal.ZERO : amount), basis);
        }
    }

    /** Weight on the seasonal term when a prior-year observation exists; the rest goes to recent trend. */
    static final BigDecimal SEASONAL_WEIGHT = new BigDecimal("0.70");
    /** Growth is clamped so a near-zero prior-year period cannot explode the forecast. */
    static final BigDecimal GROWTH_MIN = new BigDecimal("0.50");
    static final BigDecimal GROWTH_MAX = new BigDecimal("2.00");
    /** EWMA smoothing factor for the recent-trend term (higher = more weight on the latest period). */
    static final BigDecimal EWMA_ALPHA = new BigDecimal("0.50");

    static final int RECENT_MONTHS = 3;
    static final int RECENT_WEEKS = 4;
    private static final int WEEKS_PER_YEAR = 52;

    private SeasonalGrowthForecaster() {
    }

    /**
     * Category-level annual growth factor, estimated from the last {@link #RECENT_MONTHS}
     * completed months vs. the same months a year earlier. Returns {@code null} when there
     * is no prior-year basis (caller then falls back to trend). Always clamped when present.
     */
    static BigDecimal growthFactor(Map<YearMonth, BigDecimal> monthly, YearMonth currentMonth) {
        BigDecimal recent = BigDecimal.ZERO;
        BigDecimal prior = BigDecimal.ZERO;
        for (int i = 1; i <= RECENT_MONTHS; i++) {
            YearMonth month = currentMonth.minusMonths(i);
            recent = recent.add(nz(monthly.get(month)));
            prior = prior.add(nz(monthly.get(month.minusYears(1))));
        }
        if (prior.signum() <= 0) {
            return null;
        }
        BigDecimal ratio = recent.divide(prior, 6, RoundingMode.HALF_UP);
        return clamp(ratio, GROWTH_MIN, GROWTH_MAX);
    }

    /** Forecast one future month for a category. {@code currentMonth} is the (partial) month of "today". */
    static Point forecastMonth(Map<YearMonth, BigDecimal> monthly, YearMonth target,
                               YearMonth currentMonth, BigDecimal growth) {
        BigDecimal priorYear = monthly.get(target.minusYears(1));
        List<BigDecimal> recent = new ArrayList<>();
        for (int i = RECENT_MONTHS; i >= 1; i--) { // oldest → newest of the completed recent months
            BigDecimal value = monthly.get(currentMonth.minusMonths(i));
            if (value != null) {
                recent.add(value);
            }
        }
        return blend(priorYear, ewma(recent), growth, monthly.values());
    }

    /** Forecast one future ISO week for a category. {@code currentWeekStart} is the Monday of "today"'s week. */
    static Point forecastWeek(Map<LocalDate, BigDecimal> weekly, LocalDate targetWeekStart,
                              LocalDate currentWeekStart, BigDecimal growth) {
        BigDecimal priorYear = weekly.get(targetWeekStart.minusWeeks(WEEKS_PER_YEAR));
        List<BigDecimal> recent = new ArrayList<>();
        for (int i = RECENT_WEEKS; i >= 1; i--) {
            BigDecimal value = weekly.get(currentWeekStart.minusWeeks(i));
            if (value != null) {
                recent.add(value);
            }
        }
        return blend(priorYear, ewma(recent), growth, weekly.values());
    }

    /**
     * The degradation ladder: prior-year × growth blended with recent EWMA → trend-only
     * → flat average → zero. Kept in one place so weeks and months behave identically.
     */
    private static Point blend(BigDecimal priorYear, BigDecimal recentTrend,
                               BigDecimal growth, Collection<BigDecimal> allHistory) {
        boolean hasSeasonal = priorYear != null && priorYear.signum() != 0;
        if (hasSeasonal) {
            BigDecimal g = growth == null ? BigDecimal.ONE : growth;
            BigDecimal seasonal = priorYear.multiply(g);
            if (recentTrend != null) {
                BigDecimal blended = seasonal.multiply(SEASONAL_WEIGHT)
                    .add(recentTrend.multiply(BigDecimal.ONE.subtract(SEASONAL_WEIGHT)));
                return Point.of(blended, Basis.SEASONAL_GROWTH);
            }
            return Point.of(seasonal, Basis.SEASONAL_GROWTH);
        }
        if (recentTrend != null) {
            return Point.of(recentTrend, Basis.TREND);
        }
        BigDecimal average = averageOf(allHistory);
        if (average != null) {
            return Point.of(average, Basis.AVERAGE);
        }
        return Point.of(BigDecimal.ZERO, Basis.NONE);
    }

    /** Canonical EWMA over the given values ordered oldest → newest; {@code null} if empty. */
    private static BigDecimal ewma(List<BigDecimal> orderedOldToNew) {
        if (orderedOldToNew.isEmpty()) {
            return null;
        }
        BigDecimal level = orderedOldToNew.get(0);
        for (int i = 1; i < orderedOldToNew.size(); i++) {
            level = EWMA_ALPHA.multiply(orderedOldToNew.get(i))
                .add(BigDecimal.ONE.subtract(EWMA_ALPHA).multiply(level));
        }
        return level;
    }

    private static BigDecimal averageOf(Collection<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal value : values) {
            if (value != null) {
                sum = sum.add(value);
                count++;
            }
        }
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP);
    }

    static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
