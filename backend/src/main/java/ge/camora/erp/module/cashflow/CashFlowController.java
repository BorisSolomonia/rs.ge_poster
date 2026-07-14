package ge.camora.erp.module.cashflow;

import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.model.dto.CashFlowCategorizeRequest;
import ge.camora.erp.model.dto.CashFlowCategoryDto;
import ge.camora.erp.model.dto.CashFlowCategoryRequest;
import ge.camora.erp.model.dto.CashFlowDrilldownDto;
import ge.camora.erp.model.dto.CashFlowMatrixDto;
import ge.camora.erp.model.dto.CashFlowRuleDto;
import ge.camora.erp.model.dto.CashFlowRuleRequest;
import ge.camora.erp.model.dto.CashFlowStatusDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("${camora.api-prefix}/cash-flow")
public class CashFlowController {

    private final CashFlowService cashFlowService;

    public CashFlowController(CashFlowService cashFlowService) {
        this.cashFlowService = cashFlowService;
    }

    @GetMapping("/matrix")
    public ResponseEntity<ApiResponse<CashFlowMatrixDto>> matrix(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.matrix(from, to)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<CashFlowDrilldownDto>> transactions(
        @RequestParam String categoryId,
        @RequestParam(required = false) String month,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.drilldown(categoryId, month, from, to)));
    }

    @PostMapping("/transactions/categorize")
    public ResponseEntity<ApiResponse<Void>> categorize(@RequestBody CashFlowCategorizeRequest request) {
        String scope = request.scope() == null ? "" : request.scope().trim().toUpperCase(Locale.ROOT);
        if ("CASCADE".equals(scope)) {
            cashFlowService.categorizeCascade(request.fingerprint(), request.categoryId(),
                request.counterpartyInn(), request.counterpartyAccount(), request.counterparty());
        } else if ("SINGLE".equals(scope)) {
            cashFlowService.categorizeSingle(request.fingerprint(), request.categoryId());
        } else {
            throw new IllegalArgumentException("scope must be SINGLE or CASCADE");
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<CashFlowStatusDto>> refresh(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.refresh(from, to)));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<CashFlowStatusDto>> status() {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.status()));
    }

    // ── Category tree ─────────────────────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CashFlowCategoryDto>>> categories() {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.categories()));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<CashFlowCategoryDto>> createCategory(@RequestBody CashFlowCategoryRequest request) {
        boolean isSub = request.parentId() != null && !request.parentId().isBlank();
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.createCategory(
            isSub || request.section() == null ? null : parseSection(request.section()),
            isSub || request.direction() == null ? null : parseDirection(request.direction()),
            request.nameKa(),
            request.parentId(),
            request.order())));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<CashFlowCategoryDto>> updateCategory(
        @PathVariable String id, @RequestBody CashFlowCategoryRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.updateCategory(
            id,
            request.section() == null ? null : parseSection(request.section()),
            request.direction() == null ? null : parseDirection(request.direction()),
            request.nameKa(),
            request.order())));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable String id) {
        cashFlowService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Rules (mapping sheet) ─────────────────────────────────────────────────

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<CashFlowRuleDto>>> rules() {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.rules()));
    }

    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<CashFlowRuleDto>> upsertRule(@RequestBody CashFlowRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(cashFlowService.upsertRule(
            parseMatchType(request.matchType()),
            request.matchValue(),
            request.direction() == null || request.direction().isBlank() ? null : parseDirection(request.direction()),
            request.categoryId())));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable String id) {
        cashFlowService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private static CashFlowSection parseSection(String value) {
        try {
            return CashFlowSection.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid section: " + value);
        }
    }

    private static CashFlowDirection parseDirection(String value) {
        try {
            return CashFlowDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid direction: " + value);
        }
    }

    private static CashFlowMatchType parseMatchType(String value) {
        try {
            return CashFlowMatchType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid match type: " + value);
        }
    }
}
