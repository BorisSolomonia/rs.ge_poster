package ge.camora.erp.module.bankanalysis;

import ge.camora.erp.model.config.BankTransactionMapping;
import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.model.dto.BankAnalysisOverviewDto;
import ge.camora.erp.model.dto.BankTransactionMappingRequest;
import ge.camora.erp.store.ConfigStore;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("${camora.api-prefix}/bank-analysis")
public class BankAnalysisController {

    private final BankAnalysisService bankAnalysisService;
    private final ConfigStore configStore;

    public BankAnalysisController(BankAnalysisService bankAnalysisService, ConfigStore configStore) {
        this.bankAnalysisService = bankAnalysisService;
        this.configStore = configStore;
    }

    @GetMapping("/tbc")
    public ResponseEntity<ApiResponse<BankAnalysisOverviewDto>> tbcOverview(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(bankAnalysisService.analyzeTbc(dateFrom, dateTo)));
    }

    @GetMapping("/bog")
    public ResponseEntity<ApiResponse<BankAnalysisOverviewDto>> bogOverview(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(bankAnalysisService.analyzeBog(dateFrom, dateTo)));
    }

    @GetMapping("/mappings")
    public ResponseEntity<ApiResponse<List<BankTransactionMapping>>> mappings() {
        return ResponseEntity.ok(ApiResponse.ok(configStore.getBankTransactionMappings()));
    }

    @PostMapping("/mappings")
    public ResponseEntity<ApiResponse<BankTransactionMapping>> upsertMapping(
        @RequestBody BankTransactionMappingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(configStore.upsertBankTransactionMapping(
            request.direction(),
            request.matchText(),
            request.category(),
            "user"
        )));
    }

    @DeleteMapping("/mappings/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMapping(@PathVariable String id) {
        configStore.deleteBankTransactionMapping(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
