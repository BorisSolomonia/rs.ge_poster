package ge.camora.erp.module.salesanalysis;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.SalesEvent;
import ge.camora.erp.model.dto.SalesAggregation;
import ge.camora.erp.model.dto.SalesAnalysisAggregationBlock;
import ge.camora.erp.model.dto.SalesAnalysisMetric;
import ge.camora.erp.model.dto.SalesAnalysisPeriodRow;
import ge.camora.erp.model.dto.SalesAnalysisProductPoint;
import ge.camora.erp.model.dto.SalesAnalysisProductOption;
import ge.camora.erp.model.dto.SalesAnalysisProductSeries;
import ge.camora.erp.model.dto.SalesAnalysisResult;
import ge.camora.erp.model.dto.SalesAnalysisStatus;
import ge.camora.erp.model.dto.SalesAnalysisSummary;
import ge.camora.erp.store.ConfigStore;
import ge.camora.erp.util.MoneyUtil;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class SalesAnalysisService {

    private final SpreadsheetAmountParser spreadsheetAmountParser;
    private final SalesSpreadsheetParser salesSpreadsheetParser;
    private final ConfigStore configStore;
    private final CamoraProperties properties;

    public SalesAnalysisService(
        SpreadsheetAmountParser spreadsheetAmountParser,
        SalesSpreadsheetParser salesSpreadsheetParser,
        ConfigStore configStore,
        CamoraProperties properties
    ) {
        this.spreadsheetAmountParser = spreadsheetAmountParser;
        this.salesSpreadsheetParser = salesSpreadsheetParser;
        this.configStore = configStore;
        this.properties = properties;
    }

    public SalesAnalysisResult analyze(
        InputStream salesStream,
        InputStream tbcStream,
        InputStream bogStream,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        List<SalesRow> salesRows = salesSpreadsheetParser.parse(salesStream);
        salesRows.forEach(row -> configStore.registerSalesProduct(row.productName()));
        List<SalesRow> includedSalesRows = salesRows.stream()
            .filter(row -> !configStore.isSalesProductExcluded(row.productName()))
            .toList();

        Map<LocalDate, BigDecimal> salesAll = aggregateSales(includedSalesRows);
        Map<LocalDate, BigDecimal> tbcAll = spreadsheetAmountParser.parse(tbcStream, properties.getParsers().getTbc(), "TBC");
        Map<LocalDate, BigDecimal> bogAll = spreadsheetAmountParser.parse(bogStream, properties.getParsers().getBog(), "BOG");
        Map<LocalDate, String> eventsByDate = configStore.getSalesEvents().stream()
            .collect(Collectors.toMap(SalesEvent::getDate, SalesEvent::getName, (left, right) -> right, TreeMap::new));
        List<String> availableEvents = configStore.getSalesEvents().stream()
            .map(SalesEvent::getName)
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        List<SalesAnalysisProductOption> availableProducts = buildAvailableProducts(includedSalesRows);

        return new SalesAnalysisResult(
            dateFrom.toString(),
            dateTo.toString(),
            LocalDateTime.now().toString(),
            availableEvents,
            buildDayBlock(salesAll, tbcAll, bogAll, includedSalesRows, availableProducts, eventsByDate, dateFrom, dateTo),
            buildWeekBlock(salesAll, tbcAll, bogAll, includedSalesRows, availableProducts, eventsByDate, dateFrom, dateTo),
            buildMonthBlock(salesAll, tbcAll, bogAll, includedSalesRows, availableProducts, eventsByDate, dateFrom, dateTo)
        );
    }

    private Map<LocalDate, BigDecimal> aggregateSales(List<SalesRow> salesRows) {
        Map<LocalDate, BigDecimal> totals = new TreeMap<>();
        for (SalesRow row : salesRows) {
            totals.merge(row.date(), row.grossRevenue(), BigDecimal::add);
        }
        totals.replaceAll((date, amount) -> MoneyUtil.round(amount));
        return totals;
    }

    private SalesAnalysisAggregationBlock buildDayBlock(
        Map<LocalDate, BigDecimal> salesAll,
        Map<LocalDate, BigDecimal> tbcAll,
        Map<LocalDate, BigDecimal> bogAll,
        List<SalesRow> salesRows,
        List<SalesAnalysisProductOption> availableProducts,
        Map<LocalDate, String> eventsByDate,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        List<Bucket> current = new ArrayList<>();
        for (LocalDate cursor = dateFrom; !cursor.isAfter(dateTo); cursor = cursor.plusDays(1)) {
            current.add(buildBucket(cursor, cursor, salesAll, tbcAll, bogAll, eventsByDate));
        }

        List<Bucket> previous = new ArrayList<>();
        LocalDate previousEnd = dateFrom.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(current.size() - 1L);
        for (LocalDate cursor = previousStart; !cursor.isAfter(previousEnd); cursor = cursor.plusDays(1)) {
            previous.add(buildBucket(cursor, cursor, salesAll, tbcAll, bogAll, eventsByDate));
        }

        return toBlock(SalesAggregation.DAY, current, previous, salesRows, availableProducts);
    }

    private SalesAnalysisAggregationBlock buildWeekBlock(
        Map<LocalDate, BigDecimal> salesAll,
        Map<LocalDate, BigDecimal> tbcAll,
        Map<LocalDate, BigDecimal> bogAll,
        List<SalesRow> salesRows,
        List<SalesAnalysisProductOption> availableProducts,
        Map<LocalDate, String> eventsByDate,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        DayOfWeek weekStart = DayOfWeek.of(properties.getSalesAnalysis().getWeekStartsOnIso());
        LocalDate firstStart = alignToWeekStart(dateFrom, weekStart);
        LocalDate lastStart = alignToWeekStart(dateTo, weekStart);

        List<Bucket> current = new ArrayList<>();
        for (LocalDate cursor = firstStart; !cursor.isAfter(lastStart); cursor = cursor.plusWeeks(1)) {
            current.add(buildBucket(cursor, cursor.plusDays(6), salesAll, tbcAll, bogAll, eventsByDate, dateFrom, dateTo));
        }

        List<Bucket> previous = new ArrayList<>();
        LocalDate previousCursor = firstStart.minusWeeks(current.size());
        while (previous.size() < current.size()) {
            previous.add(buildBucket(previousCursor, previousCursor.plusDays(6), salesAll, tbcAll, bogAll, eventsByDate));
            previousCursor = previousCursor.plusWeeks(1);
        }

        return toBlock(SalesAggregation.WEEK, current, previous, salesRows, availableProducts);
    }

    private SalesAnalysisAggregationBlock buildMonthBlock(
        Map<LocalDate, BigDecimal> salesAll,
        Map<LocalDate, BigDecimal> tbcAll,
        Map<LocalDate, BigDecimal> bogAll,
        List<SalesRow> salesRows,
        List<SalesAnalysisProductOption> availableProducts,
        Map<LocalDate, String> eventsByDate,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        LocalDate firstMonth = dateFrom.withDayOfMonth(1);
        LocalDate lastMonth = dateTo.withDayOfMonth(1);

        List<Bucket> current = new ArrayList<>();
        for (LocalDate cursor = firstMonth; !cursor.isAfter(lastMonth); cursor = cursor.plusMonths(1)) {
            current.add(buildBucket(cursor, cursor.with(TemporalAdjusters.lastDayOfMonth()), salesAll, tbcAll, bogAll, eventsByDate, dateFrom, dateTo));
        }

        List<Bucket> previous = new ArrayList<>();
        LocalDate previousCursor = firstMonth.minusMonths(current.size());
        while (previous.size() < current.size()) {
            previous.add(buildBucket(previousCursor, previousCursor.with(TemporalAdjusters.lastDayOfMonth()), salesAll, tbcAll, bogAll, eventsByDate));
            previousCursor = previousCursor.plusMonths(1);
        }

        return toBlock(SalesAggregation.MONTH, current, previous, salesRows, availableProducts);
    }

    private SalesAnalysisAggregationBlock toBlock(
        SalesAggregation aggregation,
        List<Bucket> current,
        List<Bucket> previous,
        List<SalesRow> salesRows,
        List<SalesAnalysisProductOption> availableProducts
    ) {
        List<SalesAnalysisPeriodRow> periods = current.stream().map(this::toPeriodRow).toList();
        BigDecimal currentSales = sum(current, bucket -> bucket.sales);
        BigDecimal currentBank = sum(current, Bucket::bankIncome);
        BigDecimal currentTbc = sum(current, bucket -> bucket.tbcIncome);
        BigDecimal currentBog = sum(current, bucket -> bucket.bogIncome);
        BigDecimal currentVariance = MoneyUtil.round(currentBank.subtract(currentSales));

        BigDecimal previousSales = sum(previous, bucket -> bucket.sales);
        BigDecimal previousBank = sum(previous, Bucket::bankIncome);
        BigDecimal previousTbc = sum(previous, bucket -> bucket.tbcIncome);
        BigDecimal previousBog = sum(previous, bucket -> bucket.bogIncome);
        BigDecimal previousVariance = MoneyUtil.round(previousBank.subtract(previousSales));

        int currentCount = Math.max(periods.size(), 1);
        int previousCount = Math.max(previous.size(), 1);

        SalesAnalysisSummary summary = new SalesAnalysisSummary(
            periods.size(),
            metric(currentSales, previousSales),
            metric(currentBank, previousBank),
            metric(currentTbc, previousTbc),
            metric(currentBog, previousBog),
            metric(currentVariance, previousVariance),
            metric(ratio(currentBank, currentSales), ratio(previousBank, previousSales)),
            metric(divide(currentSales, BigDecimal.valueOf(currentCount)), divide(previousSales, BigDecimal.valueOf(previousCount))),
            metric(divide(currentBank, BigDecimal.valueOf(currentCount)), divide(previousBank, BigDecimal.valueOf(previousCount)))
        );

        return new SalesAnalysisAggregationBlock(
            aggregation,
            summary,
            periods,
            availableProducts,
            buildProductSeries(current, salesRows, availableProducts)
        );
    }

    private SalesAnalysisPeriodRow toPeriodRow(Bucket bucket) {
        BigDecimal bankIncome = bucket.bankIncome();
        BigDecimal variance = MoneyUtil.round(bankIncome.subtract(bucket.sales));
        return new SalesAnalysisPeriodRow(
            bucket.start.toString(),
            bucket.start.toString(),
            bucket.end.toString(),
            bucket.sales,
            bucket.tbcIncome,
            bucket.bogIncome,
            bankIncome,
            variance,
            ratio(variance, bucket.sales),
            ratio(bankIncome, bucket.sales),
            ratio(bucket.tbcIncome, bankIncome),
            ratio(bucket.bogIncome, bankIncome),
            bucket.events,
            resolveStatus(bucket.sales, bankIncome, variance)
        );
    }

    private List<SalesAnalysisProductSeries> buildProductSeries(
        List<Bucket> buckets,
        List<SalesRow> salesRows,
        List<SalesAnalysisProductOption> availableProducts
    ) {
        if (availableProducts.isEmpty()) {
            return List.of();
        }

        Map<String, ProductAggregate> aggregatesByKey = new TreeMap<>();
        for (SalesRow row : salesRows) {
            String productKey = ConfigStore.normalizeSalesKey(row.productName());
            ProductAggregate aggregate = aggregatesByKey.computeIfAbsent(productKey, ignored -> new ProductAggregate(row.productName()));
            aggregate.addRow(row);
        }

        List<SalesAnalysisProductSeries> series = new ArrayList<>();
        for (SalesAnalysisProductOption option : availableProducts) {
            ProductAggregate aggregate = aggregatesByKey.get(option.productKey());
            List<SalesRow> productRows = aggregate == null ? List.of() : aggregate.rows();
            List<SalesAnalysisProductPoint> periods = new ArrayList<>(buckets.size());
            for (Bucket bucket : buckets) {
                BigDecimal grossRevenue = MoneyUtil.ZERO;
                BigDecimal quantity = MoneyUtil.ZERO;
                BigDecimal profit = MoneyUtil.ZERO;
                for (SalesRow row : productRows) {
                    if (row.date().isBefore(bucket.effectiveStart) || row.date().isAfter(bucket.effectiveEnd)) {
                        continue;
                    }
                    grossRevenue = grossRevenue.add(row.grossRevenue());
                    quantity = quantity.add(row.quantity());
                    profit = profit.add(row.profit());
                }
                grossRevenue = MoneyUtil.round(grossRevenue);
                quantity = MoneyUtil.round(quantity);
                profit = MoneyUtil.round(profit);
                periods.add(new SalesAnalysisProductPoint(
                    bucket.start.toString(),
                    bucket.start.toString(),
                    bucket.end.toString(),
                    grossRevenue,
                    quantity,
                    profit,
                    ratio(profit, grossRevenue),
                    bucket.events
                ));
            }
            series.add(new SalesAnalysisProductSeries(option.productKey(), option.productName(), List.copyOf(periods)));
        }
        return List.copyOf(series);
    }

    private List<SalesAnalysisProductOption> buildAvailableProducts(List<SalesRow> salesRows) {
        Map<String, ProductAggregate> aggregates = new TreeMap<>();
        for (SalesRow row : salesRows) {
            String productKey = ConfigStore.normalizeSalesKey(row.productName());
            if (productKey.isBlank()) {
                continue;
            }
            ProductAggregate aggregate = aggregates.computeIfAbsent(productKey, ignored -> new ProductAggregate(row.productName()));
            aggregate.addRow(row);
        }

        return aggregates.entrySet().stream()
            .map(entry -> new SalesAnalysisProductOption(
                entry.getKey(),
                entry.getValue().displayName(),
                MoneyUtil.round(entry.getValue().grossRevenueTotal())
            ))
            .sorted(Comparator
                .comparing(SalesAnalysisProductOption::grossRevenueTotal, Comparator.reverseOrder())
                .thenComparing(SalesAnalysisProductOption::productName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private Bucket buildBucket(
        LocalDate bucketStart,
        LocalDate bucketEnd,
        Map<LocalDate, BigDecimal> salesAll,
        Map<LocalDate, BigDecimal> tbcAll,
        Map<LocalDate, BigDecimal> bogAll,
        Map<LocalDate, String> eventsByDate
    ) {
        return buildBucket(bucketStart, bucketEnd, salesAll, tbcAll, bogAll, eventsByDate, bucketStart, bucketEnd);
    }

    private Bucket buildBucket(
        LocalDate bucketStart,
        LocalDate bucketEnd,
        Map<LocalDate, BigDecimal> salesAll,
        Map<LocalDate, BigDecimal> tbcAll,
        Map<LocalDate, BigDecimal> bogAll,
        Map<LocalDate, String> eventsByDate,
        LocalDate visibleStart,
        LocalDate visibleEnd
    ) {
        LocalDate effectiveStart = bucketStart.isBefore(visibleStart) ? visibleStart : bucketStart;
        LocalDate effectiveEnd = bucketEnd.isAfter(visibleEnd) ? visibleEnd : bucketEnd;
        if (effectiveEnd.isBefore(effectiveStart)) {
            return new Bucket(bucketStart, bucketEnd, effectiveStart, effectiveEnd, MoneyUtil.ZERO, MoneyUtil.ZERO, MoneyUtil.ZERO, List.of());
        }
        return new Bucket(
            bucketStart,
            bucketEnd,
            effectiveStart,
            effectiveEnd,
            sumRange(salesAll, effectiveStart, effectiveEnd),
            sumRange(tbcAll, effectiveStart, effectiveEnd),
            sumRange(bogAll, effectiveStart, effectiveEnd),
            collectEvents(eventsByDate, effectiveStart, effectiveEnd)
        );
    }

    private BigDecimal sumRange(Map<LocalDate, BigDecimal> source, LocalDate from, LocalDate to) {
        BigDecimal total = MoneyUtil.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> entry : new TreeMap<>(source).entrySet()) {
            if (entry.getKey().isBefore(from) || entry.getKey().isAfter(to)) {
                continue;
            }
            total = total.add(entry.getValue());
        }
        return MoneyUtil.round(total);
    }

    private List<String> collectEvents(Map<LocalDate, String> eventsByDate, LocalDate from, LocalDate to) {
        List<String> events = new ArrayList<>();
        for (Map.Entry<LocalDate, String> entry : eventsByDate.entrySet()) {
            if (entry.getKey().isBefore(from) || entry.getKey().isAfter(to)) {
                continue;
            }
            if (!events.contains(entry.getValue())) {
                events.add(entry.getValue());
            }
        }
        return Collections.unmodifiableList(events);
    }

    private BigDecimal sum(List<Bucket> buckets, BucketValueExtractor extractor) {
        BigDecimal total = MoneyUtil.ZERO;
        for (Bucket bucket : buckets) {
            total = total.add(extractor.extract(bucket));
        }
        return MoneyUtil.round(total);
    }

    private SalesAnalysisMetric metric(BigDecimal current, BigDecimal previous) {
        BigDecimal delta = MoneyUtil.round(current.subtract(previous));
        return new SalesAnalysisMetric(current, previous, delta, ratio(delta, previous));
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return MoneyUtil.ZERO;
        }
        return MoneyUtil.round(numerator.divide(denominator, 4, RoundingMode.HALF_UP));
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return MoneyUtil.ZERO;
        }
        return MoneyUtil.round(numerator.divide(denominator, 4, RoundingMode.HALF_UP));
    }

    private SalesAnalysisStatus resolveStatus(BigDecimal sales, BigDecimal bankIncome, BigDecimal variance) {
        BigDecimal threshold = properties.getSalesAnalysis().getMatchThreshold();
        if (sales.compareTo(BigDecimal.ZERO) == 0 && bankIncome.compareTo(BigDecimal.ZERO) > 0) {
            return SalesAnalysisStatus.BANK_ONLY;
        }
        if (sales.compareTo(BigDecimal.ZERO) > 0 && bankIncome.compareTo(BigDecimal.ZERO) == 0) {
            return SalesAnalysisStatus.NO_BANK_DATA;
        }
        if (variance.abs().compareTo(threshold) <= 0) {
            return SalesAnalysisStatus.MATCH;
        }
        return variance.compareTo(BigDecimal.ZERO) < 0 ? SalesAnalysisStatus.SHORT : SalesAnalysisStatus.OVER;
    }

    private LocalDate alignToWeekStart(LocalDate date, DayOfWeek weekStart) {
        LocalDate aligned = date;
        while (aligned.getDayOfWeek() != weekStart) {
            aligned = aligned.minusDays(1);
        }
        return aligned;
    }

    private record Bucket(
        LocalDate start,
        LocalDate end,
        LocalDate effectiveStart,
        LocalDate effectiveEnd,
        BigDecimal sales,
        BigDecimal tbcIncome,
        BigDecimal bogIncome,
        List<String> events
    ) {
        private BigDecimal bankIncome() {
            return MoneyUtil.round(tbcIncome.add(bogIncome));
        }
    }

    private static final class ProductAggregate {
        private final String displayName;
        private final List<SalesRow> rows = new ArrayList<>();
        private BigDecimal grossRevenueTotal = MoneyUtil.ZERO;

        private ProductAggregate(String displayName) {
            this.displayName = displayName;
        }

        private void addRow(SalesRow row) {
            rows.add(row);
            grossRevenueTotal = grossRevenueTotal.add(row.grossRevenue());
        }

        private String displayName() {
            return displayName;
        }

        private List<SalesRow> rows() {
            return rows;
        }

        private BigDecimal grossRevenueTotal() {
            return grossRevenueTotal;
        }
    }

    @FunctionalInterface
    private interface BucketValueExtractor {
        BigDecimal extract(Bucket bucket);
    }
}
