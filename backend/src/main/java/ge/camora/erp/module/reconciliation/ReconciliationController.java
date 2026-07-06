package ge.camora.erp.module.reconciliation;

import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.model.dto.ReconciliationResult;
import ge.camora.erp.model.dto.ReconciliationResultSummary;
import ge.camora.erp.model.record.PosterRecord;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.module.ingestion.ParsedRows;
import ge.camora.erp.module.ingestion.PosterXlsxParser;
import ge.camora.erp.module.ingestion.RsgeCsvParser;
import ge.camora.erp.module.ingestion.FileParsingException;
import ge.camora.erp.module.rsge.RsgePurchaseWaybillService;
import ge.camora.erp.store.ResultCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("${camora.api-prefix}/reconciliation")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final RsgeCsvParser csvParser;
    private final PosterXlsxParser xlsxParser;
    private final RsgePurchaseWaybillService rsgePurchaseWaybillService;
    private final ReconciliationEngine engine;
    private final ResultCache resultCache;

    public ReconciliationController(RsgeCsvParser csvParser, PosterXlsxParser xlsxParser,
                                    RsgePurchaseWaybillService rsgePurchaseWaybillService,
                                    ReconciliationEngine engine, ResultCache resultCache) {
        this.csvParser = csvParser;
        this.xlsxParser = xlsxParser;
        this.rsgePurchaseWaybillService = rsgePurchaseWaybillService;
        this.engine = engine;
        this.resultCache = resultCache;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<ReconciliationResult>> analyze(
        @RequestParam("rsgeFile")   MultipartFile rsgeFile,
        @RequestParam("posterFile") MultipartFile posterFile,
        @RequestParam("dateFrom")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam("dateTo")     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        validateDateRange(dateFrom, dateTo);
        log.info("Reconciliation analyze: dateFrom={}, dateTo={}, rsge={}, poster={}",
                 dateFrom, dateTo, rsgeFile.getOriginalFilename(), posterFile.getOriginalFilename());

        ParsedRows<RsgeRecord> rsgeParsed;
        ParsedRows<PosterRecord> posterParsed;
        try {
            rsgeParsed   = csvParser.parse(rsgeFile.getInputStream());
            posterParsed = xlsxParser.parse(posterFile.getInputStream());
        } catch (Exception e) {
            log.error("File parsing failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("File parsing failed: " + e.getMessage()));
        }

        ReconciliationResult result = engine.run(rsgeParsed.records(), posterParsed.records(), dateFrom, dateTo,
            rsgeParsed.skippedRows(), posterParsed.skippedRows());
        resultCache.store(result.runId(), result);

        log.info("Reconciliation complete: runId={}, totalLines={}", result.runId(),
                 result.summary().totalLines());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/purchase-analyze")
    public ResponseEntity<ApiResponse<ReconciliationResult>> analyzePurchases(
        @RequestParam("posterFile") MultipartFile posterFile,
        @RequestParam("dateFrom") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam("dateTo") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        validateDateRange(dateFrom, dateTo);
        log.info("Purchase reconciliation analyze: dateFrom={}, dateTo={}, poster={}",
            dateFrom, dateTo, posterFile.getOriginalFilename());

        List<RsgeRecord> rsgeRecords = rsgePurchaseWaybillService.fetchPurchaseRecords(dateFrom, dateTo);
        try {
            ParsedRows<PosterRecord> posterParsed = xlsxParser.parse(posterFile.getInputStream());
            ReconciliationResult result = engine.run(rsgeRecords, posterParsed.records(), dateFrom, dateTo,
                0, posterParsed.skippedRows());
            resultCache.store(result.runId(), result);
            log.info("Purchase reconciliation complete: totalLines={}", result.summary().totalLines());
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.error("Purchase reconciliation failed: {}", e.getMessage(), e);
            throw new FileParsingException("Failed to parse Poster XLSX: " + e.getMessage(), e);
        }
    }

    @GetMapping("/results")
    public ResponseEntity<ApiResponse<List<ReconciliationResultSummary>>> listResults() {
        return ResponseEntity.ok(ApiResponse.ok(resultCache.listRecent()));
    }

    @GetMapping("/results/{runId}")
    public ResponseEntity<ApiResponse<ReconciliationResult>> getResult(@PathVariable String runId) {
        return resultCache.get(runId)
            .map(r -> ResponseEntity.ok(ApiResponse.ok(r)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo must be on or after dateFrom");
        }
    }
}
