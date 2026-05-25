package ge.camora.erp.module.supplierdebt;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.SupplierCashPayment;
import ge.camora.erp.model.config.SupplierPaymentMapping;
import ge.camora.erp.model.dto.SupplierDebtOverviewDto;
import ge.camora.erp.model.dto.SupplierDebtPaymentDto;
import ge.camora.erp.model.dto.SupplierDebtPurchaseDto;
import ge.camora.erp.model.dto.SupplierDebtRowDto;
import ge.camora.erp.model.dto.SupplierDebtSourceStatusDto;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.module.bankanalysis.BankTransaction;
import ge.camora.erp.module.bankanalysis.BogBusinessOnlineClient;
import ge.camora.erp.module.bankanalysis.TbcDbiClient;
import ge.camora.erp.module.rsge.RsgePurchaseWaybillService;
import ge.camora.erp.store.ConfigStore;
import ge.camora.erp.util.MoneyUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SupplierDebtService {

    private static final Pattern RAW_SUPPLIER_PATTERN = Pattern.compile("^\\((\\d+)\\)\\s*(.+)$");
    private static final String BOG = "BOG";
    private static final String TBC = "TBC";
    private static final String CASH = "CASH";

    private final RsgePurchaseWaybillService rsgePurchaseWaybillService;
    private final BogBusinessOnlineClient bogBusinessOnlineClient;
    private final TbcDbiClient tbcDbiClient;
    private final ConfigStore configStore;
    private final CamoraProperties properties;

    public SupplierDebtService(
        RsgePurchaseWaybillService rsgePurchaseWaybillService,
        BogBusinessOnlineClient bogBusinessOnlineClient,
        TbcDbiClient tbcDbiClient,
        ConfigStore configStore,
        CamoraProperties properties
    ) {
        this.rsgePurchaseWaybillService = rsgePurchaseWaybillService;
        this.bogBusinessOnlineClient = bogBusinessOnlineClient;
        this.tbcDbiClient = tbcDbiClient;
        this.configStore = configStore;
        this.properties = properties;
    }

    public LocalDate defaultDateFrom() {
        String configured = properties.getOrganizationOpeningDate();
        if (configured == null || configured.isBlank()) {
            return LocalDate.now().withDayOfYear(1);
        }
        return LocalDate.parse(configured);
    }

    public SupplierDebtOverviewDto analyze(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null) {
            dateFrom = defaultDateFrom();
        }
        if (dateTo == null) {
            dateTo = LocalDate.now();
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo must be on or after dateFrom");
        }

        Map<String, SupplierBucket> suppliers = new LinkedHashMap<>();
        List<SupplierDebtSourceStatusDto> statuses = new ArrayList<>();
        List<RsgeRecord> purchaseRecords = rsgePurchaseWaybillService.fetchPurchaseRecords(dateFrom, dateTo);
        BigDecimal purchaseSourceTotal = BigDecimal.ZERO;
        for (RsgeRecord record : purchaseRecords) {
            SupplierIdentity identity = supplierIdentity(record.supplierRaw());
            SupplierBucket bucket = suppliers.computeIfAbsent(identity.key(), ignored -> new SupplierBucket(identity));
            BigDecimal amount = MoneyUtil.round(record.totalPrice());
            purchaseSourceTotal = purchaseSourceTotal.add(amount);
            bucket.addPurchase(new SupplierDebtPurchaseDto(
                record.waybillNumber(),
                record.recordDate() == null ? null : record.recordDate().toLocalDate(),
                amount,
                identity.tin(),
                identity.name()
            ));
        }
        statuses.add(success("RSGE", purchaseRecords.size(), purchaseSourceTotal));

        List<SupplierPaymentMapping> mappings = configStore.getSupplierPaymentMappings();
        List<SupplierDebtPaymentDto> unmatchedPayments = new ArrayList<>();
        addBankPayments(BOG, dateFrom, dateTo, suppliers, mappings, unmatchedPayments, statuses);
        addBankPayments(TBC, dateFrom, dateTo, suppliers, mappings, unmatchedPayments, statuses);
        addCashPayments(dateFrom, dateTo, suppliers, statuses);

        List<SupplierDebtRowDto> rows = suppliers.values().stream()
            .map(SupplierBucket::toDto)
            .sorted(Comparator.comparing(SupplierDebtRowDto::debtLeft).reversed()
                .thenComparing(SupplierDebtRowDto::supplierName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        BigDecimal purchaseTotal = sum(rows.stream().map(SupplierDebtRowDto::purchaseTotal).toList());
        BigDecimal bogPaidTotal = sum(rows.stream().map(SupplierDebtRowDto::bogPaidTotal).toList());
        BigDecimal tbcPaidTotal = sum(rows.stream().map(SupplierDebtRowDto::tbcPaidTotal).toList());
        BigDecimal cashPaidTotal = sum(rows.stream().map(SupplierDebtRowDto::cashPaidTotal).toList());
        BigDecimal paidTotal = sum(rows.stream().map(SupplierDebtRowDto::paidTotal).toList());
        BigDecimal unmatchedTotal = sum(unmatchedPayments.stream().map(SupplierDebtPaymentDto::amount).toList());

        return new SupplierDebtOverviewDto(
            dateFrom,
            dateTo,
            purchaseTotal,
            bogPaidTotal,
            tbcPaidTotal,
            cashPaidTotal,
            MoneyUtil.round(bogPaidTotal.add(tbcPaidTotal)),
            paidTotal,
            MoneyUtil.round(purchaseTotal.subtract(paidTotal)),
            rows.size(),
            unmatchedTotal,
            unmatchedPayments.size(),
            rows,
            unmatchedPayments.stream()
                .sorted(Comparator.comparing(SupplierDebtPaymentDto::amount).reversed())
                .toList(),
            mappings,
            statuses
        );
    }

    private void addBankPayments(
        String provider,
        LocalDate dateFrom,
        LocalDate dateTo,
        Map<String, SupplierBucket> suppliers,
        List<SupplierPaymentMapping> mappings,
        List<SupplierDebtPaymentDto> unmatchedPayments,
        List<SupplierDebtSourceStatusDto> statuses
    ) {
        try {
            List<BankTransaction> transactions = provider.equals(BOG)
                ? bogBusinessOnlineClient.getStatement(dateFrom, dateTo)
                : tbcDbiClient.getAccountMovements(dateFrom, dateTo);
            int debitCount = 0;
            BigDecimal debitTotal = BigDecimal.ZERO;
            for (BankTransaction transaction : transactions) {
                if (!"DEBIT".equals(transaction.direction()) || transaction.amount().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                debitCount++;
                debitTotal = debitTotal.add(MoneyUtil.round(transaction.amount()));
                PaymentMatch match = matchPayment(provider, transaction, suppliers, mappings);
                SupplierDebtPaymentDto payment = toPaymentDto(provider, transaction, match.reason());
                if (match.bucket() == null) {
                    unmatchedPayments.add(payment);
                } else {
                    match.bucket().addPayment(payment);
                }
            }
            statuses.add(success(provider, debitCount, debitTotal));
        } catch (RuntimeException exception) {
            statuses.add(failed(provider, exception.getMessage()));
        }
    }

    private void addCashPayments(
        LocalDate dateFrom,
        LocalDate dateTo,
        Map<String, SupplierBucket> suppliers,
        List<SupplierDebtSourceStatusDto> statuses
    ) {
        List<SupplierCashPayment> cashPayments = configStore.getSupplierCashPayments(dateFrom, dateTo);
        BigDecimal cashTotal = BigDecimal.ZERO;
        for (SupplierCashPayment cashPayment : cashPayments) {
            SupplierIdentity identity = new SupplierIdentity(
                cashPayment.getSupplierKey(),
                normalizeTin(cashPayment.getSupplierTin()),
                blankTo(cashPayment.getSupplierName(), cashPayment.getSupplierKey())
            );
            SupplierBucket bucket = suppliers.computeIfAbsent(identity.key(), ignored -> new SupplierBucket(identity));
            BigDecimal amount = MoneyUtil.round(cashPayment.getAmount());
            cashTotal = cashTotal.add(amount);
            bucket.addPayment(new SupplierDebtPaymentDto(
                cashPayment.getId(),
                cashPayment.getDate(),
                amount,
                CASH,
                cashPayment.getSupplierName(),
                normalizeTin(cashPayment.getSupplierTin()),
                "",
                cashPayment.getNote(),
                cashPayment.getId(),
                "manual cash payment"
            ));
        }
        statuses.add(success(CASH, cashPayments.size(), cashTotal));
    }

    private PaymentMatch matchPayment(
        String provider,
        BankTransaction transaction,
        Map<String, SupplierBucket> suppliers,
        List<SupplierPaymentMapping> mappings
    ) {
        String normalizedInn = normalizeTin(transaction.counterpartyInn());
        if (!normalizedInn.isBlank()) {
            SupplierBucket byTin = suppliers.get("tin:" + normalizedInn);
            if (byTin != null) {
                return new PaymentMatch(byTin, "matched supplier TIN");
            }
        }

        String counterparty = ConfigStore.normalizeSalesKey(transaction.counterparty());
        if (!counterparty.isBlank()) {
            for (SupplierBucket bucket : suppliers.values()) {
                String supplierName = ConfigStore.normalizeSalesKey(bucket.identity.name());
                if (!supplierName.isBlank()
                    && (counterparty.equals(supplierName)
                    || counterparty.contains(supplierName)
                    || supplierName.contains(counterparty))) {
                    return new PaymentMatch(bucket, "matched supplier name");
                }
            }
        }

        String searchText = ConfigStore.normalizeSalesKey(String.join(" ",
            safe(transaction.counterparty()),
            safe(transaction.counterpartyInn()),
            safe(transaction.counterpartyAccount()),
            safe(transaction.description()),
            safe(transaction.reference())
        ));
        for (SupplierPaymentMapping mapping : mappings) {
            if (!provider.equalsIgnoreCase(mapping.getProvider())) {
                continue;
            }
            if (!mapping.getNormalizedMatchText().isBlank()
                && searchText.contains(mapping.getNormalizedMatchText())) {
                SupplierBucket bucket = suppliers.computeIfAbsent(mapping.getSupplierKey(), ignored ->
                    new SupplierBucket(new SupplierIdentity(
                        mapping.getSupplierKey(),
                        normalizeTin(mapping.getSupplierTin()),
                        blankTo(mapping.getSupplierName(), mapping.getSupplierKey())
                    ))
                );
                return new PaymentMatch(bucket, "matched saved mapping");
            }
        }
        return new PaymentMatch(null, "unmatched");
    }

    private SupplierDebtPaymentDto toPaymentDto(String provider, BankTransaction transaction, String matchReason) {
        return new SupplierDebtPaymentDto(
            transaction.reference(),
            transaction.date(),
            MoneyUtil.round(transaction.amount()),
            provider,
            transaction.counterparty(),
            normalizeTin(transaction.counterpartyInn()),
            transaction.counterpartyAccount(),
            transaction.description(),
            transaction.reference(),
            matchReason
        );
    }

    private SupplierIdentity supplierIdentity(String rawSupplier) {
        String raw = safe(rawSupplier).trim();
        Matcher matcher = RAW_SUPPLIER_PATTERN.matcher(raw);
        if (matcher.matches()) {
            String tin = normalizeTin(matcher.group(1));
            return new SupplierIdentity("tin:" + tin, tin, matcher.group(2).trim());
        }
        String name = raw.isBlank() ? "Unknown Supplier" : raw;
        return new SupplierIdentity("name:" + ConfigStore.normalizeSalesKey(name), "", name);
    }

    private SupplierDebtSourceStatusDto success(String source, int recordCount, BigDecimal total) {
        return new SupplierDebtSourceStatusDto(source, "OK", "", recordCount, MoneyUtil.round(total));
    }

    private SupplierDebtSourceStatusDto failed(String source, String message) {
        return new SupplierDebtSourceStatusDto(source, "FAILED", message == null ? "" : message, 0, BigDecimal.ZERO);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return MoneyUtil.round(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private String normalizeTin(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("[^\\d]", "");
        return normalized.isBlank() ? "" : normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record SupplierIdentity(String key, String tin, String name) {
    }

    private record PaymentMatch(SupplierBucket bucket, String reason) {
    }

    private static class SupplierBucket {
        private final SupplierIdentity identity;
        private final List<SupplierDebtPurchaseDto> purchases = new ArrayList<>();
        private final List<SupplierDebtPaymentDto> payments = new ArrayList<>();
        private BigDecimal purchaseTotal = BigDecimal.ZERO;
        private BigDecimal bogPaidTotal = BigDecimal.ZERO;
        private BigDecimal tbcPaidTotal = BigDecimal.ZERO;
        private BigDecimal cashPaidTotal = BigDecimal.ZERO;
        private int bogPaymentCount;
        private int tbcPaymentCount;
        private int cashPaymentCount;

        private SupplierBucket(SupplierIdentity identity) {
            this.identity = identity;
        }

        private void addPurchase(SupplierDebtPurchaseDto purchase) {
            purchases.add(purchase);
            purchaseTotal = purchaseTotal.add(purchase.amount());
        }

        private void addPayment(SupplierDebtPaymentDto payment) {
            payments.add(payment);
            if (BOG.equals(payment.provider())) {
                bogPaidTotal = bogPaidTotal.add(payment.amount());
                bogPaymentCount++;
            } else if (TBC.equals(payment.provider())) {
                tbcPaidTotal = tbcPaidTotal.add(payment.amount());
                tbcPaymentCount++;
            } else if (CASH.equals(payment.provider())) {
                cashPaidTotal = cashPaidTotal.add(payment.amount());
                cashPaymentCount++;
            }
        }

        private SupplierDebtRowDto toDto() {
            LocalDate lastPurchaseDate = purchases.stream()
                .map(SupplierDebtPurchaseDto::date)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(null);
            LocalDate lastPaymentDate = payments.stream()
                .map(SupplierDebtPaymentDto::date)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(null);
            BigDecimal paidTotal = MoneyUtil.round(bogPaidTotal.add(tbcPaidTotal).add(cashPaidTotal));
            return new SupplierDebtRowDto(
                identity.key(),
                identity.tin(),
                identity.name(),
                MoneyUtil.round(purchaseTotal),
                purchases.size(),
                MoneyUtil.round(bogPaidTotal),
                bogPaymentCount,
                MoneyUtil.round(tbcPaidTotal),
                tbcPaymentCount,
                MoneyUtil.round(cashPaidTotal),
                cashPaymentCount,
                paidTotal,
                payments.size(),
                MoneyUtil.round(purchaseTotal.subtract(paidTotal)),
                lastPurchaseDate,
                lastPaymentDate,
                purchases.stream()
                    .sorted(Comparator.comparing(SupplierDebtPurchaseDto::date, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList(),
                payments.stream()
                    .sorted(Comparator.comparing(SupplierDebtPaymentDto::date, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList()
            );
        }
    }
}
