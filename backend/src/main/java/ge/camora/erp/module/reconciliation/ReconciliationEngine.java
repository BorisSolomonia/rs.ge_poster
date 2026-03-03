package ge.camora.erp.module.reconciliation;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.SupplierMapping;
import ge.camora.erp.model.dto.*;
import ge.camora.erp.model.record.PosterRecord;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.store.ConfigStore;
import ge.camora.erp.util.MoneyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReconciliationEngine {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationEngine.class);

    private final ConfigStore configStore;
    private final CamoraProperties properties;

    public ReconciliationEngine(ConfigStore configStore, CamoraProperties properties) {
        this.configStore = configStore;
        this.properties = properties;
    }

    public ReconciliationResult run(
        List<RsgeRecord> rsgeRecords,
        List<PosterRecord> posterRecords,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        List<SupplierMapping> activeMappings = configStore.getAllSupplierMappings();
        Set<String> knownRsgeRaw = activeMappings.stream()
            .map(SupplierMapping::getRsgeRawValue)
            .collect(Collectors.toSet());
        Set<String> knownPosterAliases = activeMappings.stream()
            .map(SupplierMapping::getPosterAlias)
            .collect(Collectors.toSet());

        // ── STEP 1: Filter by date range ──────────────────────────────────────
        List<RsgeRecord> rsgeFiltered = rsgeRecords.stream()
            .filter(r -> !r.recordDate().toLocalDate().isBefore(dateFrom)
                      && !r.recordDate().toLocalDate().isAfter(dateTo))
            .collect(Collectors.toList());

        List<PosterRecord> posterFiltered = posterRecords.stream()
            .filter(r -> !r.documentDate().toLocalDate().isBefore(dateFrom)
                      && !r.documentDate().toLocalDate().isAfter(dateTo))
            .collect(Collectors.toList());

        log.info("Filtered: rsge={}/{}, poster={}/{}", rsgeFiltered.size(), rsgeRecords.size(),
                 posterFiltered.size(), posterRecords.size());

        // ── STEP 2: Group rs.ge records by supplierRaw ─────────────────────────
        Map<String, BigDecimal> rsgeTotals   = new LinkedHashMap<>();
        Map<String, List<String>> rsgeProducts = new HashMap<>();
        Map<String, List<String>> rsgeWaybills = new HashMap<>();

        for (RsgeRecord r : rsgeFiltered) {
            String key = r.supplierRaw();
            rsgeTotals.merge(key, r.totalPrice(), BigDecimal::add);
            rsgeProducts.computeIfAbsent(key, k -> new ArrayList<>()).add(r.productName());
            rsgeWaybills.computeIfAbsent(key, k -> new ArrayList<>()).add(r.waybillNumber());
        }
        rsgeProducts.replaceAll((k, v) -> v.stream().distinct().collect(Collectors.toList()));
        rsgeWaybills.replaceAll((k, v) -> v.stream().distinct().collect(Collectors.toList()));

        // ── STEP 3: Group Poster records by supplierAlias ─────────────────────
        Map<String, BigDecimal> posterTotals  = new LinkedHashMap<>();
        Map<String, List<Integer>> posterDocs = new HashMap<>();
        Map<String, List<String>> posterProds = new HashMap<>();

        for (PosterRecord r : posterFiltered) {
            String key = r.supplierAlias();
            posterTotals.merge(key, r.totalPrice(), BigDecimal::add);
            posterDocs.computeIfAbsent(key, k -> new ArrayList<>()).add(r.documentNumber());
            if (r.productsRaw() != null && !r.productsRaw().isBlank()) {
                posterProds.computeIfAbsent(key, k -> new ArrayList<>()).add(r.productsRaw());
            }
        }

        List<ReconciliationLineResult> results = new ArrayList<>();

        // ── STEP 4: Process mapped suppliers ──────────────────────────────────
        for (SupplierMapping mapping : activeMappings) {
            if (mapping.isPosterExcluded() && mapping.isRsgeExcluded()) continue;

            BigDecimal rsgeTotal   = rsgeTotals.getOrDefault(mapping.getRsgeRawValue(), BigDecimal.ZERO);
            BigDecimal posterTotal = posterTotals.getOrDefault(mapping.getPosterAlias(), BigDecimal.ZERO);
            BigDecimal diff = MoneyUtil.round(rsgeTotal.subtract(posterTotal));

            ReconciliationStatus status = MoneyUtil.isMatch(
                rsgeTotal,
                posterTotal,
                properties.getMatchThreshold()
            )
                ? ReconciliationStatus.MATCH
                : ReconciliationStatus.DISCREPANCY;

            String correctionAction = buildCorrectionText(mapping, rsgeTotal, posterTotal, diff, status,
                rsgeProducts.get(mapping.getRsgeRawValue()),
                rsgeWaybills.get(mapping.getRsgeRawValue()),
                posterDocs.get(mapping.getPosterAlias()));

            results.add(new ReconciliationLineResult(
                mapping.getPosterAlias(),
                mapping.getRsgeOfficialName(),
                mapping.getRsgeRawValue(),
                MoneyUtil.round(rsgeTotal),
                MoneyUtil.round(posterTotal),
                diff,
                status,
                rsgeProducts.getOrDefault(mapping.getRsgeRawValue(), List.of()),
                posterProds.getOrDefault(mapping.getPosterAlias(), List.of()),
                rsgeWaybills.getOrDefault(mapping.getRsgeRawValue(), List.of()),
                posterDocs.getOrDefault(mapping.getPosterAlias(), List.of()),
                correctionAction
            ));

            rsgeTotals.remove(mapping.getRsgeRawValue());
            posterTotals.remove(mapping.getPosterAlias());
        }

        // ── STEP 5: Remaining rs.ge suppliers ─────────────────────────────────
        for (Map.Entry<String, BigDecimal> entry : rsgeTotals.entrySet()) {
            String supplierRaw = entry.getKey();
            BigDecimal total = MoneyUtil.round(entry.getValue());
            results.add(new ReconciliationLineResult(
                null, supplierRaw, supplierRaw,
                total, BigDecimal.ZERO, total,
                ReconciliationStatus.MISSING_IN_POSTER,
                rsgeProducts.getOrDefault(supplierRaw, List.of()),
                List.of(),
                rsgeWaybills.getOrDefault(supplierRaw, List.of()),
                List.of(),
                String.format(properties.getMessages().getMissingInPosterTemplate(), supplierRaw, total)
            ));
        }

        // ── STEP 6: Remaining Poster suppliers ────────────────────────────────
        for (Map.Entry<String, BigDecimal> entry : posterTotals.entrySet()) {
            String alias = entry.getKey();
            BigDecimal total = MoneyUtil.round(entry.getValue());
            results.add(new ReconciliationLineResult(
                alias, null, null,
                BigDecimal.ZERO, total, total.negate(),
                ReconciliationStatus.MISSING_IN_RSGE,
                List.of(),
                posterProds.getOrDefault(alias, List.of()),
                List.of(),
                posterDocs.getOrDefault(alias, List.of()),
                String.format(properties.getMessages().getMissingInRsgeTemplate(), alias, total)
            ));
        }

        // ── STEP 7: New supplier discovery ────────────────────────────────────
        List<String> newRsge = rsgeFiltered.stream()
            .map(RsgeRecord::supplierRaw)
            .distinct()
            .filter(s -> !knownRsgeRaw.contains(s))
            .collect(Collectors.toList());

        List<String> newPoster = posterFiltered.stream()
            .map(PosterRecord::supplierAlias)
            .distinct()
            .filter(s -> !knownPosterAliases.contains(s))
            .collect(Collectors.toList());

        newRsge.forEach(s -> configStore.registerStandaloneSupplier(properties.getPlatforms().getRsge(), s));
        newPoster.forEach(s -> configStore.registerStandaloneSupplier(properties.getPlatforms().getPoster(), s));

        // ── Summary ───────────────────────────────────────────────────────────
        long matched       = results.stream().filter(r -> r.status() == ReconciliationStatus.MATCH).count();
        long discrepancy   = results.stream().filter(r -> r.status() == ReconciliationStatus.DISCREPANCY).count();
        long missingPoster = results.stream().filter(r -> r.status() == ReconciliationStatus.MISSING_IN_POSTER).count();
        long missingRsge   = results.stream().filter(r -> r.status() == ReconciliationStatus.MISSING_IN_RSGE).count();

        LocalDateTime now = LocalDateTime.now();
        String runId = UUID.randomUUID().toString();

        return new ReconciliationResult(
            runId,
            dateFrom,
            dateTo,
            now,
            now.plusHours(properties.getReconciliation().getResultExpireAfterHours()),
            new ReconciliationSummary(results.size(), (int) matched, (int) discrepancy,
                                      (int) missingPoster, (int) missingRsge),
            results,
            new NewSuppliersDiscovered(newRsge, newPoster)
        );
    }

    private String buildCorrectionText(
        SupplierMapping mapping,
        BigDecimal rsgeTotal, BigDecimal posterTotal, BigDecimal diff,
        ReconciliationStatus status,
        List<String> rsgeProductList,
        List<String> waybills,
        List<Integer> posterDocsList
    ) {
        if (status == ReconciliationStatus.MATCH) return null;

        String rsgeProductStr = rsgeProductList != null ? String.join(", ", rsgeProductList) : "";
        String waybillStr     = waybills != null ? String.join(", ", waybills) : "";
        String posterDocStr   = posterDocsList != null
            ? posterDocsList.stream().map(d -> "#" + d).collect(Collectors.joining(", "))
            : "";

        return String.format(
            properties.getMessages().getDiscrepancyCorrectionTemplate(),
            mapping.getPosterAlias(), mapping.getRsgeOfficialName(),
            MoneyUtil.round(rsgeTotal), MoneyUtil.round(posterTotal), diff,
            rsgeProductStr, waybillStr, posterDocStr
        );
    }
}
