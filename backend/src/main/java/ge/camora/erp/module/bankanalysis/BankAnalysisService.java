package ge.camora.erp.module.bankanalysis;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.BankTransactionMapping;
import ge.camora.erp.model.dto.BankAnalysisOverviewDto;
import ge.camora.erp.model.dto.BankCategoryTotalDto;
import ge.camora.erp.model.dto.BankTransactionDto;
import ge.camora.erp.model.dto.BankUnmappedGroupDto;
import ge.camora.erp.store.ConfigStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BankAnalysisService {

    private static final String UNMAPPED = "Unmapped";

    private final TbcDbiClient tbcDbiClient;
    private final BogBusinessOnlineClient bogBusinessOnlineClient;
    private final ConfigStore configStore;
    private final CamoraProperties properties;

    public BankAnalysisService(
        TbcDbiClient tbcDbiClient,
        BogBusinessOnlineClient bogBusinessOnlineClient,
        ConfigStore configStore,
        CamoraProperties properties
    ) {
        this.tbcDbiClient = tbcDbiClient;
        this.bogBusinessOnlineClient = bogBusinessOnlineClient;
        this.configStore = configStore;
        this.properties = properties;
    }

    public BankAnalysisOverviewDto analyzeTbc(LocalDate dateFrom, LocalDate dateTo) {
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo must be on or after dateFrom");
        }
        List<BankTransactionMapping> mappings = configStore.getBankTransactionMappings();
        List<AnalyzedTransaction> analyzed = tbcDbiClient.getAccountMovements(dateFrom, dateTo).stream()
            .map(transaction -> analyzeTransaction(transaction, mappings))
            .toList();

        BigDecimal totalCredits = sum(analyzed.stream()
            .filter(transaction -> transaction.transaction().direction().equals("CREDIT"))
            .map(transaction -> transaction.transaction().amount())
            .toList());
        BigDecimal totalDebits = sum(analyzed.stream()
            .filter(transaction -> transaction.transaction().direction().equals("DEBIT"))
            .map(transaction -> transaction.transaction().amount())
            .toList());

        return new BankAnalysisOverviewDto(
            dateFrom,
            dateTo,
            "TBC",
            properties.getTbcDbi().getAccountNumber(),
            properties.getTbcDbi().getCurrency(),
            totalCredits,
            totalDebits,
            totalCredits.subtract(totalDebits),
            analyzed.size(),
            categoryTotals(analyzed),
            unmappedGroups(analyzed, "CREDIT", properties.getTbcDbi().getLargeCreditThreshold()),
            unmappedGroups(analyzed, "DEBIT", BigDecimal.ZERO),
            mappings,
            analyzed.stream()
                .sorted(Comparator.comparing((AnalyzedTransaction row) -> row.transaction().date()).reversed())
                .map(this::toDto)
                .toList()
        );
    }

    public BankAnalysisOverviewDto analyzeBog(LocalDate dateFrom, LocalDate dateTo) {
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo must be on or after dateFrom");
        }
        List<BankTransactionMapping> mappings = configStore.getBankTransactionMappings();
        List<AnalyzedTransaction> analyzed = bogBusinessOnlineClient.getStatement(dateFrom, dateTo).stream()
            .map(transaction -> analyzeTransaction(transaction, mappings))
            .toList();

        BigDecimal totalCredits = sum(analyzed.stream()
            .filter(transaction -> transaction.transaction().direction().equals("CREDIT"))
            .map(transaction -> transaction.transaction().amount())
            .toList());
        BigDecimal totalDebits = sum(analyzed.stream()
            .filter(transaction -> transaction.transaction().direction().equals("DEBIT"))
            .map(transaction -> transaction.transaction().amount())
            .toList());

        return new BankAnalysisOverviewDto(
            dateFrom,
            dateTo,
            "BOG",
            properties.getBogApi().getAccountNumber(),
            properties.getBogApi().getCurrency(),
            totalCredits,
            totalDebits,
            totalCredits.subtract(totalDebits),
            analyzed.size(),
            categoryTotals(analyzed),
            unmappedGroups(analyzed, "CREDIT", properties.getBogApi().getLargeCreditThreshold()),
            unmappedGroups(analyzed, "DEBIT", BigDecimal.ZERO),
            mappings,
            analyzed.stream()
                .sorted(Comparator.comparing((AnalyzedTransaction row) -> row.transaction().date()).reversed())
                .map(this::toDto)
                .toList()
        );
    }

    private AnalyzedTransaction analyzeTransaction(BankTransaction transaction, List<BankTransactionMapping> mappings) {
        String searchText = ConfigStore.normalizeSalesKey(String.join(" ",
            safe(transaction.counterparty()),
            safe(transaction.description()),
            safe(transaction.reference()),
            safe(transaction.accountNumber())
        ));
        BankTransactionMapping mapping = mappings.stream()
            .filter(candidate -> directionMatches(candidate.getDirection(), transaction.direction()))
            .filter(candidate -> !candidate.getNormalizedMatchText().isBlank())
            .filter(candidate -> searchText.contains(candidate.getNormalizedMatchText()))
            .findFirst()
            .orElse(null);
        return new AnalyzedTransaction(
            transaction,
            mapping == null ? UNMAPPED : mapping.getCategory(),
            mapping != null,
            mapping == null ? "" : mapping.getMatchText()
        );
    }

    private List<BankCategoryTotalDto> categoryTotals(List<AnalyzedTransaction> transactions) {
        Map<String, TotalBucket> totals = new LinkedHashMap<>();
        for (AnalyzedTransaction transaction : transactions) {
            String key = transaction.transaction().direction() + "|" + transaction.category();
            totals.computeIfAbsent(key, ignored -> new TotalBucket(
                transaction.transaction().direction(),
                transaction.category()
            )).add(transaction.transaction().amount());
        }
        return totals.values().stream()
            .map(bucket -> new BankCategoryTotalDto(bucket.direction, bucket.category, bucket.amount, bucket.count))
            .sorted(Comparator.comparing(BankCategoryTotalDto::direction)
                .thenComparing(BankCategoryTotalDto::amount, Comparator.reverseOrder()))
            .toList();
    }

    private List<BankUnmappedGroupDto> unmappedGroups(
        List<AnalyzedTransaction> transactions,
        String direction,
        BigDecimal minimumAmount
    ) {
        Map<String, UnmappedBucket> buckets = new LinkedHashMap<>();
        for (AnalyzedTransaction analyzed : transactions) {
            BankTransaction transaction = analyzed.transaction();
            if (!transaction.direction().equals(direction) || analyzed.mapped()) {
                continue;
            }
            if (transaction.amount().compareTo(minimumAmount) < 0) {
                continue;
            }
            String matchText = bestMatchText(transaction);
            buckets.computeIfAbsent(
                ConfigStore.normalizeSalesKey(matchText),
                ignored -> new UnmappedBucket(direction, matchText, transaction.counterparty(), transaction.description())
            ).add(transaction.amount());
        }
        return buckets.values().stream()
            .map(bucket -> new BankUnmappedGroupDto(
                bucket.direction,
                bucket.matchText,
                bucket.counterparty,
                bucket.description,
                bucket.amount,
                bucket.count,
                bucket.largestTransaction
            ))
            .sorted(Comparator.comparing(BankUnmappedGroupDto::amount).reversed())
            .toList();
    }

    private BankTransactionDto toDto(AnalyzedTransaction analyzed) {
        BankTransaction transaction = analyzed.transaction();
        return new BankTransactionDto(
            transaction.date(),
            transaction.direction(),
            transaction.amount(),
            transaction.currency(),
            transaction.accountNumber(),
            transaction.counterparty(),
            transaction.description(),
            transaction.reference(),
            analyzed.category(),
            analyzed.mapped(),
            analyzed.mappingMatchText()
        );
    }

    private boolean directionMatches(String mappingDirection, String transactionDirection) {
        return mappingDirection.equals("BOTH") || mappingDirection.equals(transactionDirection);
    }

    private String bestMatchText(BankTransaction transaction) {
        String counterparty = safe(transaction.counterparty());
        if (!counterparty.isBlank()) {
            return counterparty;
        }
        String description = safe(transaction.description());
        if (!description.isBlank()) {
            return description;
        }
        return safe(transaction.reference());
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record AnalyzedTransaction(
        BankTransaction transaction,
        String category,
        boolean mapped,
        String mappingMatchText
    ) {
    }

    private static class TotalBucket {
        private final String direction;
        private final String category;
        private BigDecimal amount = BigDecimal.ZERO;
        private int count;

        private TotalBucket(String direction, String category) {
            this.direction = direction;
            this.category = category;
        }

        private void add(BigDecimal value) {
            amount = amount.add(value);
            count++;
        }
    }

    private static class UnmappedBucket {
        private final String direction;
        private final String matchText;
        private final String counterparty;
        private final String description;
        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal largestTransaction = BigDecimal.ZERO;
        private int count;

        private UnmappedBucket(String direction, String matchText, String counterparty, String description) {
            this.direction = direction;
            this.matchText = matchText;
            this.counterparty = counterparty;
            this.description = description;
        }

        private void add(BigDecimal value) {
            amount = amount.add(value);
            if (value.compareTo(largestTransaction) > 0) {
                largestTransaction = value;
            }
            count++;
        }
    }
}
