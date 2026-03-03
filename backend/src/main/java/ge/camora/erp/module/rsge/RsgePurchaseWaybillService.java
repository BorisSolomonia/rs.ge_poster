package ge.camora.erp.module.rsge;

import ge.camora.erp.model.config.SupplierMapping;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.store.ConfigStore;
import ge.camora.erp.util.MoneyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class RsgePurchaseWaybillService {

    private static final Logger log = LoggerFactory.getLogger(RsgePurchaseWaybillService.class);

    private static final List<String> AMOUNT_FIELDS = Arrays.asList(
        "FULL_AMOUNT", "full_amount", "FullAmount", "fullAmount",
        "TOTAL_AMOUNT", "total_amount", "totalAmount", "TotalAmount",
        "AMOUNT_LARI", "amount_lari", "AmountLari", "amountLari",
        "NET_AMOUNT", "net_amount", "NetAmount", "netAmount",
        "GROSS_AMOUNT", "gross_amount", "GrossAmount", "grossAmount",
        "AMOUNT", "amount", "Amount",
        "SUM", "sum", "Sum",
        "SUMA", "suma", "Suma",
        "VALUE", "value", "Value",
        "VALUE_LARI", "value_lari",
        "PRICE", "price", "Price",
        "TOTAL_PRICE", "total_price",
        "COST", "cost", "Cost",
        "TOTAL_COST", "total_cost"
    );

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = Arrays.asList(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
    );

    private final RsgeSoapClient rsgeSoapClient;
    private final ConfigStore configStore;

    public RsgePurchaseWaybillService(RsgeSoapClient rsgeSoapClient, ConfigStore configStore) {
        this.rsgeSoapClient = rsgeSoapClient;
        this.configStore = configStore;
    }

    public List<RsgeRecord> fetchPurchaseRecords(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> rawWaybills = rsgeSoapClient.getBuyerWaybills(startDate, endDate);
        List<RsgeRecord> records = new ArrayList<>();
        int skipped = 0;

        for (Map<String, Object> rawWaybill : rawWaybills) {
            Integer status = parseStatus(rawWaybill);
            if (status != null && (status == -1 || status == -2)) {
                skipped++;
                continue;
            }

            String supplierName = normalizeSpace(firstNonBlank(rawWaybill, "SELLER_NAME", "seller_name", "SellerName"));
            if (supplierName == null || supplierName.isBlank()) {
                skipped++;
                continue;
            }

            String sellerTin = normalizeTaxId(firstNonBlank(rawWaybill, "SELLER_TIN", "seller_tin", "SellerTin"));

            LocalDateTime recordDate = parseDateTime(rawWaybill);
            if (recordDate == null) {
                skipped++;
                continue;
            }

            BigDecimal total = extractAmount(rawWaybill);
            if (MoneyUtil.ZERO.compareTo(total) == 0) {
                skipped++;
                continue;
            }

            String supplierRawValue = resolveSupplierRawValue(sellerTin, supplierName);
            records.add(new RsgeRecord(
                firstNonBlank(rawWaybill, "ID", "id", "waybill_id", "waybillId"),
                supplierRawValue,
                "",
                "",
                MoneyUtil.ZERO,
                MoneyUtil.ZERO,
                total,
                recordDate
            ));
        }

        log.info("rs.ge purchase waybills mapped: {} records, {} skipped", records.size(), skipped);
        return records;
    }

    private BigDecimal extractAmount(Map<String, Object> rawWaybill) {
        for (String field : AMOUNT_FIELDS) {
            Object value = rawWaybill.get(field);
            if (value == null) {
                continue;
            }
            BigDecimal amount = parseAmount(value.toString());
            if (MoneyUtil.ZERO.compareTo(amount) != 0) {
                return amount;
            }
        }
        return MoneyUtil.ZERO;
    }

    private LocalDateTime parseDateTime(Map<String, Object> rawWaybill) {
        String rawDate = firstNonBlank(
            rawWaybill,
            "CREATE_DATE", "create_date", "CreateDate",
            "CREATEDATE", "DATE", "date",
            "WAYBILL_DATE", "waybill_date", "WaybillDate"
        );
        if (rawDate == null) {
            return null;
        }

        try {
            return OffsetDateTime.parse(rawDate).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(rawDate, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        try {
            return LocalDate.parse(rawDate, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }

        return null;
    }

    private Integer parseStatus(Map<String, Object> rawWaybill) {
        String rawStatus = firstNonBlank(rawWaybill, "STATUS", "status");
        if (rawStatus == null) {
            return null;
        }
        try {
            return Integer.parseInt(rawStatus);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private String normalizeSpace(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeTaxId(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[^\\d]", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String resolveSupplierRawValue(String sellerTin, String supplierName) {
        List<SupplierMapping> mappings = configStore.getAllSupplierMappings();

        if (sellerTin != null) {
            Optional<SupplierMapping> byTaxId = mappings.stream()
                .filter(mapping -> Objects.equals(normalizeTaxId(mapping.getRsgeTaxId()), sellerTin))
                .findFirst();
            if (byTaxId.isPresent()) {
                return byTaxId.get().getRsgeRawValue();
            }
        }

        Optional<SupplierMapping> byOfficialName = mappings.stream()
            .filter(mapping -> normalizedEquals(mapping.getRsgeOfficialName(), supplierName))
            .findFirst();
        if (byOfficialName.isPresent()) {
            return byOfficialName.get().getRsgeRawValue();
        }

        if (sellerTin != null) {
            return "(" + sellerTin + ") " + supplierName;
        }

        return supplierName;
    }

    private boolean normalizedEquals(String left, String right) {
        String normalizedLeft = normalizeSpace(left);
        String normalizedRight = normalizeSpace(right);
        if (normalizedLeft == null || normalizedRight == null) {
            return false;
        }
        return normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    private BigDecimal parseAmount(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return MoneyUtil.ZERO;
        }

        String normalized = rawAmount.replace(",", ".").replaceAll("[^\\d.-]", "");
        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized) || "-.".equals(normalized)) {
            return MoneyUtil.ZERO;
        }

        return MoneyUtil.round(new BigDecimal(normalized));
    }
}
