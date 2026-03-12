package ge.camora.erp.module.salesanalysis;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.dto.SalesAggregation;
import ge.camora.erp.model.dto.SalesAnalysisAggregationBlock;
import ge.camora.erp.model.dto.SalesAnalysisMetric;
import ge.camora.erp.model.dto.SalesAnalysisPeriodRow;
import ge.camora.erp.model.dto.SalesAnalysisResult;
import ge.camora.erp.model.dto.SalesAnalysisStatus;
import ge.camora.erp.model.dto.SalesAnalysisSummary;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class SalesAnalysisService {

    private final SpreadsheetAmountParser spreadsheetAmountParser;
    private final CamoraProperties properties;

    public SalesAnalysisService(SpreadsheetAmountParser spreadsheetAmountParser, CamoraProperties properties) {
        this.spreadsheetAmountParser = spreadsheetAmountParser;
        this.properties = properties;
    }

    public SalesAnalysisResult analyze(
        InputStream salesStream,
        InputStream tbcStream,
        InputStream bogStream,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        Map<LocalDate, BigDecimal> salesAll = spreadsheetAmountParser.parse(salesStream, properties.getParsers().getSales(), "Sales");
        Map<LocalDate, BigDecimal> tbcAll = spreadsheetAmountParser.parse(tbcStream, properties.getParsers().getTbc(), "TBC");
        Map<LocalDate, BigDecimal> bogAll = spreadsheetAmountParser.parse(bogStream, properties.getParsers().getBog(), "BOG");

        return new SalesAnalysisResult(
            dateFrom.toString(),
            dateTo.toString(),
            LocalDateTime.now().toString(),
            buildDayBlock(salesAll, tbcAll, bogAll, dateFrom, dateTo),
            buildWeekBlock(salesAll, tbcAll, bogAll, dateFrom, dateTo),
            buildMonthBlock(salesAll, tbcAll, bogAll, dateFrom, dateTo)
        );
    }

    private SalesAnalysisAggregationBlock buildDayBlock(
        Map<LocalDate, BigDecimal> salesAll,
        Map<LocalDate, BigDecimal> tbcAll,
        Map<LocalDate, BigDecimal> bogAll,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        List<Bucket> current = new ArrayList<>();
        for (LocalDate cursor = dateFrom; !cursor.isAfter(dateTo); cursor = cursor.plusDays(1)) {
            current.add(buildBucket(cursor, cursor, salesAll, tbcAll, bogAll));
        }

        List<Bucket> previous = new ArrayList<>();
        LocalDate previousEnd = dateFrom.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(current.size() - 1L);
        for (LocalDate cursor = previousStart; !cursor.isAfter(previousEnd); cursor = cursor.plusDays(1)) {
            previous.add(buildBucket(cursor, cursor, salesAll, tbcAll, bogAll));
        }

        return toBlock(SalesAggregation.DAY, current, previous);
    }

    private SalesAnalysisAggregationBlock buildWeekBlock(
        Map<LocalDate, BigDecimal> salesAll,
        Map<LocalDate, BigDecimal> tbcAll,
        Map<LocalDate, BigDecimal> bogAll,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        DayOfWeek weekStart = DayOfWeek.of(properties.getSalesAnalysis().getWeekStartsOnIso());
        LocalDate firstStart = alignToWeekStart(dateFrom, weekStart);
        LocalDate lastStart = alignToWeekStart(dateTo, weekStart);

        List<Bucket> current = new ArrayList<>();
        for (LocalDate cursor = firstStart; !cursor.isAfter(lastStart); cursor = cursor.plusWeeks(1)) {
            current.add(buildBucket(cursor, cursor.plusDays(6), salesAll, tbcAll, bogAll, dateFrom, dateTo));
        }

        List<Bucket> previous = new ArrayList<>();
        LocalDate previousCursor = firstStart.minusWeeks(current.size());
        while (previous.size() < current.size()) {
            previous.add(buildBucket(previousCursor, previousCursor.plusDays(6), salesAll, tbcAll, bogAll));
            previousCursor = previousCursor.plusWeeks(1);
        }

        return toBlock(SalesAggregation.WEEK, current, previous);
    }

    private SalesAnalysisAggregationBlock buildMonthBlock(
        Map<LocalDate, BigDecimal> salesAll,
        Map<LocalDate, BigDecimal> tbcAll,
        Map<LocalDate, BigDecimal> bogAll,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        LocalDate firstMonth = dateFrom.withDayOfMonth(1);
        LocalDate lastMonth = dateTo.withDayOfMonth(1);

        List<Bucket> current = new ArrayList<>();
        for (LocalDate cursor = firstMonth; !cursor.isAfter(lastMonth); cursor = cursor.plusMonths(1)) {
            current.add(buildBucket(cursor, cursor.with(TemporalAdjusters.lastDayOfMonth()), salesAll, tbcAll, bogAll, dateFrom, dateTo));
        }

        List<Bucket> previous = new ArrayList<>();
        LocalDate previousCursor = firstMonth.minusMonths(current.size());
        while (previous.size() < current.size()) {
            previous.add(buildBucket(previousCursor, previousCursor.with(TemporalAdjusters.lastDayOfMonth()), salesAll, tbcAll, bogAll));
            previousCursor = previousCursor.plusMonths(1);
        }

        return toBlock(SalesAggregation.MONTH, current, previous);
    }

    private SalesAnalysisAggregationBlock toBlock(SalesAggregation aggregation, List<Bucket> current, List<Bucket> previous) {
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

        return new SalesAnalysisAggregationBlock(aggregation, summary, periods);
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
            resolveStatus(bucket.sales, bankIncome, variance)
        );
    }

    private Bucket buildBucket(
        LocalDate bucketStart,
        LocalDate bucketEnd,
        Map<LocalDate, BigDecimal> salesAll,
        Map<LocalDate, BigDecimal> tbcAll,
        Map<LocalDate, BigDecimal> bogAll
    ) {
        return buildBucket(bucketStart, bucketEnd, salesAll, tbcAll, bogAll, bucketStart, bucketEnd);
    }

    private Bucket buildBucket(
        LocalDate bucketStart,
        LocalDate bucketEnd,
        Map<LocalDate, BigDecimal> salesAll,
        Map<LocalDate, BigDecimal> tbcAll,
        Map<LocalDate, BigDecimal> bogAll,
        LocalDate visibleStart,
        LocalDate visibleEnd
    ) {
        LocalDate effectiveStart = bucketStart.isBefore(visibleStart) ? visibleStart : bucketStart;
        LocalDate effectiveEnd = bucketEnd.isAfter(visibleEnd) ? visibleEnd : bucketEnd;
        if (effectiveEnd.isBefore(effectiveStart)) {
            return new Bucket(bucketStart, bucketEnd, MoneyUtil.ZERO, MoneyUtil.ZERO, MoneyUtil.ZERO);
        }
        return new Bucket(
            bucketStart,
            bucketEnd,
            sumRange(salesAll, effectiveStart, effectiveEnd),
            sumRange(tbcAll, effectiveStart, effectiveEnd),
            sumRange(bogAll, effectiveStart, effectiveEnd)
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

    private record Bucket(LocalDate start, LocalDate end, BigDecimal sales, BigDecimal tbcIncome, BigDecimal bogIncome) {
        private BigDecimal bankIncome() {
            return MoneyUtil.round(tbcIncome.add(bogIncome));
        }
    }

    @FunctionalInterface
    private interface BucketValueExtractor {
        BigDecimal extract(Bucket bucket);
    }
}
