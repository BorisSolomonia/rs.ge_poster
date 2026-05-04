package ge.camora.erp.module.supplierdebt;

import ge.camora.erp.model.config.SupplierPaymentMapping;
import ge.camora.erp.model.dto.SupplierDebtOverviewDto;
import ge.camora.erp.model.dto.SupplierDebtPaymentDto;
import ge.camora.erp.model.dto.SupplierDebtPurchaseDto;
import ge.camora.erp.model.dto.SupplierDebtRowDto;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.module.bankanalysis.BankTransaction;
import ge.camora.erp.module.bankanalysis.BogBusinessOnlineClient;
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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SupplierDebtService {

    private static final Pattern RAW_SUPPLIER_PATTERN = Pattern.compile("^\\((\\d+)\\)\\s*(.+)$");

    private final RsgePurchaseWaybillService rsgePurchaseWaybillService;
    private final BogBusinessOnlineClient bogBusinessOnlineClient;
    private final ConfigStore configStore;

    public SupplierDebtService(
        RsgePurchaseWaybillService rsgePurchaseWaybillService,
        BogBusinessOnlineClient bogBusinessOnlineClient,
        ConfigStore configStore
    ) {
        this.rsgePurchaseWaybillService = rsgePurchaseWaybillService;
        this.bogBusinessOnlineClient = bogBusinessOnlineClient;
        this.configStore = configStore;
    }

    public SupplierDebtOverviewDto analyze(LocalDate dateFrom, LocalDate dateTo) {
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo must be on or after dateFrom");
        }

        Map<String, SupplierBucket> suppliers = new LinkedHashMap<>();
        for (RsgeRecord record : rsgePurchaseWaybillService.fetchPurchaseRecords(dateFrom, dateTo)) {
            SupplierIdentity identity = supplierIdentity(record.supplierRaw());
            SupplierBucket bucket = suppliers.computeIfAbsent(identity.key(), ignored -> new SupplierBucket(identity));
            bucket.addPurchase(new SupplierDebtPurchaseDto(
                record.waybillNumber(),
                record.recordDate() == null ? null : record.recordDate().toLocalDate(),
                MoneyUtil.round(record.totalPrice()),
                identity.tin(),
                identity.name()
            ));
        }

        List<SupplierPaymentMapping> mappings = configStore.getSupplierPaymentMappings();
        List<SupplierDebtPaymentDto> unmatchedPayments = new ArrayList<>();
        for (BankTransaction transaction : bogBusinessOnlineClient.getStatement(dateFrom, dateTo)) {
            if (!"DEBIT".equals(transaction.direction()) || transaction.amount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            PaymentMatch match = matchPayment(transaction, suppliers, mappings);
            SupplierDebtPaymentDto payment = toPaymentDto(transaction, match.reason());
            if (match.bucket() == null) {
                unmatchedPayments.add(payment);
            } else {
                match.bucket().addPayment(payment);
            }
        }

        List<SupplierDebtRowDto> rows = suppliers.values().stream()
            .map(SupplierBucket::toDto)
            .sorted(Comparator.comparing(SupplierDebtRowDto::debtLeft).reversed()
                .thenComparing(SupplierDebtRowDto::supplierName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        BigDecimal purchaseTotal = MoneyUtil.round(rows.stream()
            .map(SupplierDebtRowDto::purchaseTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal paidTotal = MoneyUtil.round(rows.stream()
            .map(SupplierDebtRowDto::paidTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal unmatchedTotal = MoneyUtil.round(unmatchedPayments.stream()
            .map(SupplierDebtPaymentDto::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));

        return new SupplierDebtOverviewDto(
            dateFrom,
            dateTo,
            purchaseTotal,
            paidTotal,
            MoneyUtil.round(purchaseTotal.subtract(paidTotal)),
            rows.size(),
            unmatchedTotal,
            unmatchedPayments.size(),
            rows,
            unmatchedPayments.stream()
                .sorted(Comparator.comparing(SupplierDebtPaymentDto::amount).reversed())
                .toList(),
            mappings
        );
    }

    private PaymentMatch matchPayment(
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
            if (!"BOG".equalsIgnoreCase(mapping.getProvider())) {
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

    private SupplierDebtPaymentDto toPaymentDto(BankTransaction transaction, String matchReason) {
        return new SupplierDebtPaymentDto(
            transaction.date(),
            MoneyUtil.round(transaction.amount()),
            "BOG",
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
        private BigDecimal paidTotal = BigDecimal.ZERO;

        private SupplierBucket(SupplierIdentity identity) {
            this.identity = identity;
        }

        private void addPurchase(SupplierDebtPurchaseDto purchase) {
            purchases.add(purchase);
            purchaseTotal = purchaseTotal.add(purchase.amount());
        }

        private void addPayment(SupplierDebtPaymentDto payment) {
            payments.add(payment);
            paidTotal = paidTotal.add(payment.amount());
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
            return new SupplierDebtRowDto(
                identity.key(),
                identity.tin(),
                identity.name(),
                MoneyUtil.round(purchaseTotal),
                purchases.size(),
                MoneyUtil.round(paidTotal),
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
