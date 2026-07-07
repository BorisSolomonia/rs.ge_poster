package ge.camora.erp.module.supplierdebt;

import ge.camora.erp.model.config.SupplierPaymentMapping;
import ge.camora.erp.model.config.SupplierCashPayment;
import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.model.dto.SupplierCreditorActiveRequest;
import ge.camora.erp.model.dto.SupplierCreditorOverviewDto;
import ge.camora.erp.model.dto.SupplierCreditorRowDto;
import ge.camora.erp.model.dto.SupplierCashPaymentRequest;
import ge.camora.erp.model.dto.SupplierDebtAuditDto;
import ge.camora.erp.model.dto.SupplierDebtOverviewDto;
import ge.camora.erp.model.dto.SupplierDebtRawPayloadDto;
import ge.camora.erp.model.dto.SupplierDebtRowDto;
import ge.camora.erp.model.dto.SupplierPaymentMappingRequest;
import ge.camora.erp.store.ConfigStore;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("${camora.api-prefix}/supplier-debts")
public class SupplierDebtController {

    private final SupplierDebtService supplierDebtService;
    private final ConfigStore configStore;

    public SupplierDebtController(SupplierDebtService supplierDebtService, ConfigStore configStore) {
        this.supplierDebtService = supplierDebtService;
        this.configStore = configStore;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SupplierDebtOverviewDto>> overview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(defaultValue = "false") boolean refreshSources
    ) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.overview(dateFrom, dateTo, refreshSources)));
    }

    @PostMapping("/sync-now")
    public ResponseEntity<ApiResponse<SupplierDebtOverviewDto>> syncNow(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.syncNow(dateFrom, dateTo)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<SupplierDebtOverviewDto>> startRefresh(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.startAsyncRefresh(dateFrom, dateTo)));
    }

    @GetMapping("/creditors")
    public ResponseEntity<ApiResponse<SupplierCreditorOverviewDto>> creditors(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.creditorOverview(dateFrom, dateTo)));
    }

    @PostMapping("/creditors/sync-all")
    public ResponseEntity<ApiResponse<SupplierCreditorOverviewDto>> syncAllCreditors(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.syncAllCreditors(dateFrom, dateTo)));
    }

    @PostMapping("/creditors/{supplierKey}/sync")
    public ResponseEntity<ApiResponse<SupplierCreditorRowDto>> syncCreditor(
        @PathVariable String supplierKey,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.syncCreditorSupplier(supplierKey, dateFrom, dateTo)));
    }

    @PatchMapping("/creditors/{supplierKey}/active")
    public ResponseEntity<ApiResponse<SupplierCreditorOverviewDto>> setCreditorActive(
        @PathVariable String supplierKey,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestBody SupplierCreditorActiveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.setCreditorActive(
            supplierKey,
            request.active(),
            dateFrom,
            dateTo
        )));
    }

    @GetMapping("/suppliers/{supplierKey}/transactions")
    public ResponseEntity<ApiResponse<SupplierDebtRowDto>> supplierTransactions(
        @PathVariable String supplierKey,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(defaultValue = "false") boolean refreshSources
    ) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.supplierTransactions(
            supplierKey,
            dateFrom,
            dateTo,
            refreshSources
        )));
    }

    @PostMapping("/audit-random")
    public ResponseEntity<ApiResponse<SupplierDebtAuditDto>> auditRandom(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.auditRandom(dateFrom, dateTo)));
    }

    @GetMapping("/debug/raw-payloads")
    public ResponseEntity<ApiResponse<SupplierDebtRawPayloadDto>> rawPayloads(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(defaultValue = "false") boolean refreshSources
    ) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.rawPayloads(dateFrom, dateTo, refreshSources)));
    }

    @GetMapping("/cash-payments")
    public ResponseEntity<ApiResponse<List<SupplierCashPayment>>> cashPayments(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        LocalDate effectiveDateFrom = dateFrom == null ? supplierDebtService.defaultDateFrom() : dateFrom;
        LocalDate effectiveDateTo = dateTo == null ? LocalDate.now() : dateTo;
        return ResponseEntity.ok(ApiResponse.ok(configStore.getSupplierCashPayments(effectiveDateFrom, effectiveDateTo)));
    }

    @PostMapping("/cash-payments")
    public ResponseEntity<ApiResponse<SupplierCashPayment>> addCashPayment(
        @RequestBody SupplierCashPaymentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(configStore.addSupplierCashPayment(
            request.supplierKey(),
            request.supplierTin(),
            request.supplierName(),
            request.date(),
            request.amount(),
            request.note(),
            "user"
        )));
    }

    @DeleteMapping("/cash-payments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCashPayment(@PathVariable String id) {
        configStore.deleteSupplierCashPayment(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/mappings")
    public ResponseEntity<ApiResponse<List<SupplierPaymentMapping>>> mappings() {
        return ResponseEntity.ok(ApiResponse.ok(configStore.getSupplierPaymentMappings()));
    }

    @PostMapping("/mappings")
    public ResponseEntity<ApiResponse<SupplierPaymentMapping>> upsertMapping(
        @RequestBody SupplierPaymentMappingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(configStore.upsertSupplierPaymentMapping(
            request.provider(),
            request.matchText(),
            request.supplierKey(),
            request.supplierTin(),
            request.supplierName(),
            "user"
        )));
    }

    @DeleteMapping("/mappings/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMapping(@PathVariable String id) {
        configStore.deleteSupplierPaymentMapping(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
