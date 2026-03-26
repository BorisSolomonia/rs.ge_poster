package ge.camora.erp.module.cashflow;

import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.model.dto.CashFlowCategoryMappingRequest;
import ge.camora.erp.model.dto.CashFlowCategoryMappingView;
import ge.camora.erp.model.dto.CashFlowMappingsViewDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${camora.api-prefix}/cash-flow-mappings")
public class CashFlowMappingController {

    private final CashFlowService cashFlowService;

    public CashFlowMappingController(CashFlowService cashFlowService) {
        this.cashFlowService = cashFlowService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CashFlowMappingsViewDto>> list(
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.getMappingsView(from, to)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CashFlowCategoryMappingView>> upsert(@RequestBody CashFlowCategoryMappingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
            cashFlowService.upsertMapping(request.getSourceCategory(), request.getTargetCategory())
        ));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(@RequestParam String sourceCategory) {
        cashFlowService.deleteMapping(sourceCategory);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
