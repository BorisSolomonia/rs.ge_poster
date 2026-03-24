package ge.camora.erp.module.cashflow;

import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.model.dto.CashFlowGroupDto;
import ge.camora.erp.model.dto.CashFlowOverviewDto;
import ge.camora.erp.model.dto.CashFlowSyncStatusDto;
import ge.camora.erp.model.dto.CashFlowTransactionsResponseDto;
import ge.camora.erp.model.dto.CashFlowWarningsResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${camora.api-prefix}/cash-flow")
public class CashFlowController {

    private final CashFlowService cashFlowService;

    public CashFlowController(CashFlowService cashFlowService) {
        this.cashFlowService = cashFlowService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<CashFlowSyncStatusDto>> status() {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.getStatus()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<CashFlowSyncStatusDto>> refresh() {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.refreshSnapshot()));
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<CashFlowOverviewDto>> overview(
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.getOverview(from, to)));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CashFlowGroupDto>>> categories(@RequestParam String month) {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.getCategories(month)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<CashFlowTransactionsResponseDto>> transactions(
        @RequestParam String month,
        @RequestParam(required = false) String group,
        @RequestParam(required = false) String category
    ) {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.getTransactions(month, group, category)));
    }

    @GetMapping("/warnings")
    public ResponseEntity<ApiResponse<CashFlowWarningsResponseDto>> warnings(@RequestParam(required = false) String month) {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.getWarnings(month)));
    }
}
