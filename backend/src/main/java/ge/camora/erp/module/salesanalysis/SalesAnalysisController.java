package ge.camora.erp.module.salesanalysis;

import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.model.dto.SalesAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("${camora.api-prefix}/sales-analysis")
public class SalesAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(SalesAnalysisController.class);

    private final SalesAnalysisService salesAnalysisService;

    public SalesAnalysisController(SalesAnalysisService salesAnalysisService) {
        this.salesAnalysisService = salesAnalysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<SalesAnalysisResult>> analyze(
        @RequestParam("salesFile") MultipartFile salesFile,
        @RequestParam("tbcFile") MultipartFile tbcFile,
        @RequestParam("bogFile") MultipartFile bogFile,
        @RequestParam("dateFrom") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam("dateTo") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        validateDateRange(dateFrom, dateTo);
        validateFile(salesFile, "salesFile");
        validateFile(tbcFile, "tbcFile");
        validateFile(bogFile, "bogFile");

        try {
            SalesAnalysisResult result = salesAnalysisService.analyze(
                salesFile.getInputStream(),
                tbcFile.getInputStream(),
                bogFile.getInputStream(),
                dateFrom,
                dateTo
            );
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.error("Sales analysis failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to analyze sales and bank files: " + e.getMessage(), e);
        }
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo must be on or after dateFrom");
        }
    }

    private void validateFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
