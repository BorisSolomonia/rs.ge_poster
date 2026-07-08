package ge.camora.erp.module.cashflow;

import ge.camora.erp.model.dto.BudgetForecastCellDto;
import ge.camora.erp.model.dto.BudgetForecastDto;
import ge.camora.erp.model.dto.BudgetForecastPeriodDto;
import ge.camora.erp.model.dto.BudgetForecastRowDto;
import ge.camora.erp.model.dto.BudgetForecastTotalDto;
import ge.camora.erp.util.MoneyUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Predictive budgeting engine. Builds a rolling week + month forecast per cash-flow
 * category from the already-categorized bank history (reusing
 * {@link CashFlowService#categorizedDailySeries}), applies the Golden-Path
 * {@link SeasonalGrowthForecaster}, then merges any management overrides on top.
 *
 * <p>The forecast is a pure read-model: it holds only the aggregated per-category
 * period series (not raw transactions), so a multi-year history window stays cheap
 * on the heap (see {@code docs/budgeting/architecture-risk-assessment.md}).
 */
@Service
public class ForecastService {

    static final String TYPE_WEEK = "WEEK";
    static final String TYPE_MONTH = "MONTH";

    static final int WEEKS_AHEAD = 4;
    static final int MONTHS_AHEAD = 3;
    /** Look back far enough to have a full prior-year point for every forecast period plus recent trend. */
    static final int HISTORY_MONTHS = 24;

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM");

    private final CashFlowService cashFlowService;
    private final CashFlowCategoryStore categoryStore;
    private final ForecastOverrideStore overrideStore;

    public ForecastService(CashFlowService cashFlowService,
                           CashFlowCategoryStore categoryStore,
                           ForecastOverrideStore overrideStore) {
        this.cashFlowService = cashFlowService;
        this.categoryStore = categoryStore;
        this.overrideStore = overrideStore;
    }

    public BudgetForecastDto forecast(LocalDate asOf) {
        LocalDate today = asOf == null ? LocalDate.now() : asOf;
        LocalDate historyFrom = today.minusMonths(HISTORY_MONTHS).withDayOfMonth(1);

        Map<String, Map<LocalDate, BigDecimal>> daily = cashFlowService.categorizedDailySeries(historyFrom, today);

        List<BudgetForecastPeriodDto> periods = buildPeriods(today);
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate currentWeekStart = weekStart(today);
        Map<String, BigDecimal> overrides = overrideStore.snapshot();

        List<CashFlowCategory> categories = categoryStore.list().stream()
            .filter(category -> !isSentinel(category.getId()))
            .filter(category -> shouldInclude(category, daily, overrides))
            .sorted(Comparator
                .comparingInt((CashFlowCategory c) -> c.getSection().order())
                .thenComparing(c -> c.getDirection() == CashFlowDirection.INFLOW ? 0 : 1)
                .thenComparingInt(CashFlowCategory::getOrder)
                .thenComparing(CashFlowCategory::getNameKa, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        List<BudgetForecastRowDto> rows = new ArrayList<>();
        // period key -> accumulating totals
        Map<String, BigDecimal> inflowBaseline = zeroed(periods);
        Map<String, BigDecimal> inflowAmount = zeroed(periods);
        Map<String, BigDecimal> outflowBaseline = zeroed(periods);
        Map<String, BigDecimal> outflowAmount = zeroed(periods);

        for (CashFlowCategory category : categories) {
            Map<LocalDate, BigDecimal> categoryDaily = daily.getOrDefault(category.getId(), Map.of());
            Map<YearMonth, BigDecimal> monthly = bucketMonthly(categoryDaily);
            Map<LocalDate, BigDecimal> weekly = bucketWeekly(categoryDaily);
            BigDecimal growth = SeasonalGrowthForecaster.growthFactor(monthly, currentMonth);
            boolean inflow = category.getDirection() == CashFlowDirection.INFLOW;

            List<BudgetForecastCellDto> cells = new ArrayList<>();
            for (BudgetForecastPeriodDto period : periods) {
                SeasonalGrowthForecaster.Point point = TYPE_WEEK.equals(period.type())
                    ? SeasonalGrowthForecaster.forecastWeek(weekly, LocalDate.parse(period.key()), currentWeekStart, growth)
                    : SeasonalGrowthForecaster.forecastMonth(monthly, YearMonth.parse(period.key()), currentMonth, growth);

                BigDecimal baseline = point.amount();
                BigDecimal override = overrides.get(ForecastOverrideStore.key(period.type(), category.getId(), period.key()));
                boolean overridden = override != null;
                BigDecimal amount = overridden ? MoneyUtil.round(override) : baseline;

                cells.add(new BudgetForecastCellDto(period.key(), baseline, amount, overridden, point.basis().name()));

                if (inflow) {
                    inflowBaseline.merge(period.key(), baseline, BigDecimal::add);
                    inflowAmount.merge(period.key(), amount, BigDecimal::add);
                } else {
                    outflowBaseline.merge(period.key(), baseline, BigDecimal::add);
                    outflowAmount.merge(period.key(), amount, BigDecimal::add);
                }
            }
            rows.add(new BudgetForecastRowDto(
                category.getId(), category.getNameKa(), category.getSection().name(),
                category.getDirection().name(), category.getParentId(), cells));
        }

        List<BudgetForecastTotalDto> totals = new ArrayList<>();
        for (BudgetForecastPeriodDto period : periods) {
            BigDecimal inB = MoneyUtil.round(inflowBaseline.get(period.key()));
            BigDecimal inA = MoneyUtil.round(inflowAmount.get(period.key()));
            BigDecimal outB = MoneyUtil.round(outflowBaseline.get(period.key()));
            BigDecimal outA = MoneyUtil.round(outflowAmount.get(period.key()));
            totals.add(new BudgetForecastTotalDto(
                period.key(), inB, inA, outB, outA,
                MoneyUtil.round(inB.subtract(outB)), MoneyUtil.round(inA.subtract(outA))));
        }

        boolean historyUncategorized = hasActivity(daily.get(CashFlowCategoryDefaults.UNCATEGORIZED_INFLOW))
            || hasActivity(daily.get(CashFlowCategoryDefaults.UNCATEGORIZED_OUTFLOW));

        return new BudgetForecastDto(today, periods, rows, totals, historyUncategorized, LocalDateTime.now());
    }

    public void setOverride(String periodType, String periodKey, String categoryId, BigDecimal amount) {
        String type = normalizeType(periodType);
        if (categoryStore.findById(categoryId).isEmpty()) {
            throw new IllegalArgumentException("Unknown cash-flow category: " + categoryId);
        }
        overrideStore.put(type, categoryId, periodKey, amount);
    }

    public void clearOverride(String periodType, String periodKey, String categoryId) {
        overrideStore.remove(normalizeType(periodType), categoryId, periodKey);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private List<BudgetForecastPeriodDto> buildPeriods(LocalDate today) {
        List<BudgetForecastPeriodDto> periods = new ArrayList<>();
        LocalDate firstFutureWeek = weekStart(today).plusWeeks(1);
        for (int i = 0; i < WEEKS_AHEAD; i++) {
            LocalDate start = firstFutureWeek.plusWeeks(i);
            LocalDate end = start.plusDays(6);
            periods.add(new BudgetForecastPeriodDto(
                TYPE_WEEK, start.toString(),
                start.format(DAY_MONTH) + "–" + end.format(DAY_MONTH), start, end));
        }
        YearMonth currentMonth = YearMonth.from(today);
        for (int i = 1; i <= MONTHS_AHEAD; i++) {
            YearMonth ym = currentMonth.plusMonths(i);
            periods.add(new BudgetForecastPeriodDto(
                TYPE_MONTH, ym.toString(), ym.toString(), ym.atDay(1), ym.atEndOfMonth()));
        }
        return periods;
    }

    private boolean shouldInclude(CashFlowCategory category,
                                  Map<String, Map<LocalDate, BigDecimal>> daily,
                                  Map<String, BigDecimal> overrides) {
        if (category.getSection() == CashFlowSection.OPERATING) {
            return true; // operating income/expense lines are the core of the budget — always visible
        }
        if (hasActivity(daily.get(category.getId()))) {
            return true;
        }
        String marker = "|" + category.getId() + "|";
        return overrides.keySet().stream().anyMatch(key -> key.contains(marker));
    }

    private static Map<YearMonth, BigDecimal> bucketMonthly(Map<LocalDate, BigDecimal> daily) {
        Map<YearMonth, BigDecimal> monthly = new LinkedHashMap<>();
        daily.forEach((date, amount) -> monthly.merge(YearMonth.from(date), amount, BigDecimal::add));
        return monthly;
    }

    private static Map<LocalDate, BigDecimal> bucketWeekly(Map<LocalDate, BigDecimal> daily) {
        Map<LocalDate, BigDecimal> weekly = new LinkedHashMap<>();
        daily.forEach((date, amount) -> weekly.merge(weekStart(date), amount, BigDecimal::add));
        return weekly;
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static Map<String, BigDecimal> zeroed(List<BudgetForecastPeriodDto> periods) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        periods.forEach(period -> map.put(period.key(), BigDecimal.ZERO));
        return map;
    }

    private static boolean hasActivity(Map<LocalDate, BigDecimal> series) {
        return series != null && !series.isEmpty();
    }

    private static boolean isSentinel(String categoryId) {
        return CashFlowCategoryDefaults.UNCATEGORIZED_INFLOW.equals(categoryId)
            || CashFlowCategoryDefaults.UNCATEGORIZED_OUTFLOW.equals(categoryId);
    }

    private static String normalizeType(String periodType) {
        String type = periodType == null ? "" : periodType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!TYPE_WEEK.equals(type) && !TYPE_MONTH.equals(type)) {
            throw new IllegalArgumentException("periodType must be WEEK or MONTH");
        }
        return type;
    }
}
